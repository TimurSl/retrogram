package app.retrogram.xmpp;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.LruCache;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

/** Local files for XMPP: picked attachments, downloaded images and avatars. */
public final class XmppFiles {
    private static final long MAX_IMAGE_BYTES = 12L * 1024 * 1024;
    private static final int MAX_IMAGE_SIDE = 1280;
    private static final LruCache<String, Bitmap> memory = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    public interface ImageCallback {
        void onImage(Bitmap bitmap);

        void onFailure();
    }

    private XmppFiles() {
    }

    private static File dir(String name) {
        File d = new File(ApplicationLoader.applicationContext.getCacheDir(), name);
        if (!d.exists() && !d.mkdirs()) {
            FileLog.e("cannot create " + d);
        }
        return d;
    }

    private static File outbox() {
        return dir("xmpp_out");
    }

    public static String sha1(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    public static boolean looksLikeImage(String url) {
        if (url == null) {
            return false;
        }
        String p = url;
        int q = p.indexOf('?');
        if (q >= 0) {
            p = p.substring(0, q);
        }
        p = p.toLowerCase(Locale.ROOT);
        return p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".png") || p.endsWith(".gif") || p.endsWith(".webp");
    }

    public static String fileNameOf(String url) {
        String p = url == null ? "" : url;
        int q = p.indexOf('?');
        if (q >= 0) {
            p = p.substring(0, q);
        }
        int slash = p.lastIndexOf('/');
        String name = slash >= 0 ? p.substring(slash + 1) : p;
        try {
            name = java.net.URLDecoder.decode(name, "UTF-8");
        } catch (Exception ignored) {
        }
        return name.isEmpty() ? "file" : name;
    }

    public static String displayName(Context context, Uri uri) {
        if ("content".equals(uri.getScheme())) {
            try (Cursor c = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst() && !c.isNull(0)) {
                    return c.getString(0);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        String last = uri.getLastPathSegment();
        return last == null || last.isEmpty() ? "file" : last;
    }

    /** Copies a picked document into the app's cache so it can be uploaded. Returns null if it cannot be read. */
    public static File copyToOutbox(Context context, Uri uri) {
        String name = displayName(context, uri).replaceAll("[\\\\/:*?\"<>|]", "_");
        File dir = new File(outbox(), Long.toString(System.nanoTime(), 36));
        if (!dir.mkdirs()) {
            return null;
        }
        File out = new File(dir, name);
        ContentResolver cr = context.getContentResolver();
        try (InputStream in = cr.openInputStream(uri); OutputStream os = new FileOutputStream(out)) {
            if (in == null) {
                return null;
            }
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
            return out;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public static String mimeOf(Context context, Uri uri, String name) {
        String type = context.getContentResolver().getType(uri);
        if (type != null && !type.isEmpty()) {
            return type;
        }
        String ext = android.webkit.MimeTypeMap.getFileExtensionFromUrl(name);
        String t = ext == null ? null : android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase(Locale.ROOT));
        return t == null ? "application/octet-stream" : t;
    }

    public static Bitmap cachedImage(String url) {
        return memory.get(url);
    }

    /** Downloads (or reads from the disk cache) an image and decodes it at a sensible size. Callback runs on the UI thread. */
    public static void loadImage(String url, ImageCallback cb) {
        Bitmap hit = memory.get(url);
        if (hit != null) {
            cb.onImage(hit);
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            Bitmap bmp = null;
            try {
                File cached = new File(dir("xmpp_images"), sha1(url));
                if (!cached.exists()) {
                    download(url, cached);
                }
                bmp = decode(cached, MAX_IMAGE_SIDE);
                if (bmp == null) {
                    cached.delete();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            final Bitmap result = bmp;
            if (result != null) {
                memory.put(url, result);
            }
            org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                if (result != null) {
                    cb.onImage(result);
                } else {
                    cb.onFailure();
                }
            });
        });
    }

    private static void download(String url, File target) throws IOException {
        URL u = new URL(url);
        String protocol = u.getProtocol();
        if (!"https".equalsIgnoreCase(protocol) && !"http".equalsIgnoreCase(protocol)) {
            throw new IOException("unsupported scheme");
        }
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        File tmp = new File(target.getPath() + ".part");
        try {
            if (c.getResponseCode() / 100 != 2) {
                throw new IOException("HTTP " + c.getResponseCode());
            }
            long len = c.getContentLengthLong();
            if (len > MAX_IMAGE_BYTES) {
                throw new IOException("too large");
            }
            long total = 0;
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[32 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_IMAGE_BYTES) {
                        throw new IOException("too large");
                    }
                    out.write(buf, 0, n);
                }
            }
            if (!tmp.renameTo(target)) {
                throw new IOException("rename failed");
            }
        } finally {
            c.disconnect();
            if (tmp.exists()) {
                tmp.delete();
            }
        }
    }

    public static Bitmap decode(File f, int maxSide) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) {
            return null;
        }
        int sample = 1;
        while (o.outWidth / sample > maxSide * 2 || o.outHeight / sample > maxSide * 2) {
            sample *= 2;
        }
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getPath(), d);
    }

    public static Bitmap decode(byte[] data, int maxSide) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) {
            return null;
        }
        int sample = 1;
        while (o.outWidth / sample > maxSide * 2 || o.outHeight / sample > maxSide * 2) {
            sample *= 2;
        }
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(data, 0, data.length, d);
    }

    public static File avatarFile(long accountId, String jid) {
        return new File(dir("xmpp_avatars"), sha1(accountId + "/" + jid.toLowerCase(Locale.ROOT)));
    }

    public static void clearAccount(long accountId) {
        // Avatars are keyed by a hash, so they cannot be matched to an account: drop them all, they are cheap to fetch again.
        File[] files = dir("xmpp_avatars").listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
    }
}
