package app.retrogram;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import tw.nekomimi.nekogram.NekoConfig;

/**
 * User-chosen app font (Settings → Retrogram → Font).
 *
 * The user picks a .ttf/.otf file; it is validated, copied into the app's private storage and used for the
 * regular, medium (bold) and italic sans styles that go through {@link AndroidUtilities#getTypeface(String)}.
 * Monospace, condensed and the symbol/number fonts are left alone. The typeface cache is process-wide, so a change
 * applies after a restart.
 *
 * Reads the stored file name straight from the preferences: typefaces can be requested before
 * {@link RetroConfig#init()} runs (same reason as {@link RetroThemeColors#enabled()}).
 */
public final class RetroFont {

    private static final String DIR = "retro_fonts";
    /** A font file bigger than this is refused; CJK fonts reach tens of MB but nobody needs more than this for a UI font. */
    static final long MAX_FILE_SIZE = 32L * 1024 * 1024;

    private static final Object lock = new Object();
    private static boolean loaded;
    private static Typeface base;

    private RetroFont() {
    }

    /** Thrown by {@link #install} with a reason the settings screen can map to a message. */
    public static final class InvalidFontException extends IOException {
        public final boolean tooLarge;

        InvalidFontException(String message, boolean tooLarge) {
            super(message);
            this.tooLarge = tooLarge;
        }
    }

    /**
     * Weight (index 0) and italic flag (index 1, 1 = italic) a custom font should be used with for the given bundled
     * font asset, or {@code null} when that asset keeps its own font.
     * "Medium" is asked for at 700 on purpose: a single font file has no medium cut, and 700 makes the system embolden it,
     * which keeps titles distinguishable from body text (the asset loader in TypefaceHelper does the same).
     */
    static int[] styleFor(String assetPath) {
        if (assetPath == null) {
            return null;
        }
        switch (assetPath) {
            case AndroidUtilities.TYPEFACE_ROBOTO_REGULAR:
                return new int[]{400, 0};
            case AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM:
                return new int[]{700, 0};
            case AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM_ITALIC:
                return new int[]{700, 1};
            case AndroidUtilities.TYPEFACE_ROBOTO_EXTRA_BOLD:
                return new int[]{800, 0};
            case AndroidUtilities.TYPEFACE_RITALIC:
                return new int[]{400, 1};
            default:
                return null;
        }
    }

    /** The custom variant for a bundled font asset, or {@code null} to use the normal one. */
    public static Typeface resolve(String assetPath) {
        int[] style = styleFor(assetPath);
        if (style == null) {
            return null;
        }
        Typeface font = base();
        if (font == null) {
            return null;
        }
        return create(font, style[0], style[1] == 1);
    }

    /** Whether a custom font is loaded in this process. */
    public static boolean isActive() {
        return base() != null;
    }

    private static Typeface create(Typeface font, int weight, boolean italic) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Typeface.create(font, weight, italic);
        }
        int style = weight >= 600 ? (italic ? Typeface.BOLD_ITALIC : Typeface.BOLD) : (italic ? Typeface.ITALIC : Typeface.NORMAL);
        return Typeface.create(font, style);
    }

    private static Typeface base() {
        synchronized (lock) {
            if (!loaded) {
                loaded = true;
                try {
                    String name = NekoConfig.getPreferences().getString(RetroConfig.fontFile.getKey(), "");
                    if (name != null && !name.isEmpty()) {
                        File file = new File(directory(), name);
                        if (file.isFile()) {
                            // Null (not an exception) when the file cannot be parsed, e.g. it was corrupted since.
                            base = new Typeface.Builder(file).build();
                        }
                    }
                } catch (Throwable e) {
                    // Never let a bad font keep the app from starting: fall back to the bundled one.
                    FileLog.e(e);
                    base = null;
                }
            }
            return base;
        }
    }

    private static File directory() {
        return new File(ApplicationLoader.getFilesDirFixed(), DIR);
    }

    /** Name shown in settings: the picked file's name, or {@code null} when the bundled font is used. */
    public static String selectedName() {
        String file = RetroConfig.fontFile.String();
        if (file == null || file.isEmpty()) {
            return null;
        }
        String name = RetroConfig.fontName.String();
        return name == null || name.isEmpty() ? file : name;
    }

    /**
     * Validates the font behind {@code uri}, stores a private copy and selects it. Blocking; call off the UI thread.
     * Takes effect after a restart. Returns the name to show for it.
     */
    public static String install(Context context, Uri uri) throws IOException {
        File dir = directory();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Cannot create " + dir);
        }
        String displayName = displayName(context, uri);
        File target = new File(dir, "font_" + System.currentTimeMillis() + ".ttf");
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException("Cannot open " + uri);
            }
            copyLimited(in, target);
            if (new Typeface.Builder(target).build() == null) {
                throw new InvalidFontException("Not a usable font file", false);
            }
        } catch (IOException | RuntimeException e) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new InvalidFontException(String.valueOf(e.getMessage()), false);
        }
        RetroConfig.fontFile.setConfigString(target.getName());
        RetroConfig.fontName.setConfigString(displayName);
        deleteStoredExcept(target.getName());
        return displayName;
    }

    /** Goes back to the bundled font (after a restart). */
    public static void reset() {
        RetroConfig.fontFile.setConfigString("");
        RetroConfig.fontName.setConfigString("");
        deleteStoredExcept(null);
    }

    private static void deleteStoredExcept(String keep) {
        File[] files = directory().listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!file.getName().equals(keep)) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    private static void copyLimited(InputStream in, File target) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        long total = 0;
        try (FileOutputStream out = new FileOutputStream(target)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_FILE_SIZE) {
                    throw new InvalidFontException("Font file is too large", true);
                }
                out.write(buffer, 0, read);
            }
        }
    }

    private static String displayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.trim().isEmpty()) {
                    return name.trim();
                }
            }
        } catch (Exception ignore) {
        }
        String last = uri.getLastPathSegment();
        return last == null || last.isEmpty() ? "font" : last;
    }
}
