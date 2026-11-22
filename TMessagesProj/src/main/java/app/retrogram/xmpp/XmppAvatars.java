package app.retrogram.xmpp;

import android.graphics.Bitmap;
import android.util.LruCache;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import app.retrogram.xmpp.core.Callback;
import app.retrogram.xmpp.core.XmppController;
import app.retrogram.xmpp.core.XmppError;
import app.retrogram.xmpp.core.XmppSession;

/** vCard avatars (XEP-0054) of contacts, cached in memory and on disk. */
public final class XmppAvatars {
    private static final long RETRY_AFTER_MS = 60 * 60 * 1000L;
    private static final long REFRESH_AFTER_MS = 24 * 60 * 60 * 1000L;
    private static final int SIZE = 192;

    public interface Listener {
        void onAvatarChanged(long accountId, String jid);
    }

    private static final LruCache<String, Bitmap> memory = new LruCache<>(150);
    private static final Set<String> loading = new HashSet<>();
    private static final Map<String, Long> missedAt = new HashMap<>();
    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private XmppAvatars() {
    }

    public static void addListener(Listener l) {
        listeners.add(l);
    }

    public static void removeListener(Listener l) {
        listeners.remove(l);
    }

    private static String key(long accountId, String jid) {
        return accountId + "/" + jid.toLowerCase(Locale.ROOT);
    }

    /** The cached avatar, or null. Starts a background fetch when there is none (or it is stale). */
    public static Bitmap get(long accountId, String jid) {
        if (jid == null || jid.isEmpty()) {
            return null;
        }
        String k = key(accountId, jid);
        Bitmap b = memory.get(k);
        if (b != null) {
            return b;
        }
        File f = XmppFiles.avatarFile(accountId, jid);
        if (f.exists()) {
            Bitmap decoded = XmppFiles.decode(f, SIZE);
            if (decoded != null) {
                memory.put(k, decoded);
                if (System.currentTimeMillis() - f.lastModified() > REFRESH_AFTER_MS) {
                    fetch(accountId, jid);
                }
                return decoded;
            }
        }
        fetch(accountId, jid);
        return null;
    }

    private static void fetch(long accountId, String jid) {
        String k = key(accountId, jid);
        synchronized (loading) {
            Long miss = missedAt.get(k);
            if (loading.contains(k) || (miss != null && System.currentTimeMillis() - miss < RETRY_AFTER_MS)) {
                return;
            }
            loading.add(k);
        }
        XmppController c = XmppManager.get().controller(accountId);
        if (c == null || !c.session().isOnline()) {
            synchronized (loading) {
                loading.remove(k);
            }
            return;
        }
        c.session().loadProfile(jid, new Callback<XmppSession.Profile>() {
            @Override
            public void onResult(XmppSession.Profile p) {
                Utilities.globalQueue.postRunnable(() -> {
                    Bitmap bmp = p.avatar == null || p.avatar.length == 0 ? null : XmppFiles.decode(p.avatar, SIZE);
                    if (bmp != null) {
                        try (FileOutputStream out = new FileOutputStream(XmppFiles.avatarFile(accountId, jid))) {
                            out.write(p.avatar);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        memory.put(k, bmp);
                    }
                    synchronized (loading) {
                        loading.remove(k);
                        if (bmp == null) {
                            missedAt.put(k, System.currentTimeMillis());
                        }
                    }
                    if (bmp != null) {
                        AndroidUtilities.runOnUIThread(() -> {
                            for (Listener l : listeners) {
                                l.onAvatarChanged(accountId, jid);
                            }
                        });
                    }
                });
            }

            @Override
            public void onError(XmppError error) {
                synchronized (loading) {
                    loading.remove(k);
                    missedAt.put(k, System.currentTimeMillis());
                }
            }
        });
    }

    /** Forget a cached avatar, e.g. after the user changed their own. */
    public static void invalidate(long accountId, String jid) {
        memory.remove(key(accountId, jid));
        XmppFiles.avatarFile(accountId, jid).delete();
        synchronized (loading) {
            missedAt.remove(key(accountId, jid));
        }
    }
}
