package app.retrogram;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.TypefaceSpan;

/**
 * Small look-and-feel rules of the classic shell that several upstream classes share.
 * Every method is a no-op (returns the upstream value) outside retro mode, so callers can use it unconditionally.
 */
public final class RetroLook {

    private RetroLook() {
    }

    /** How far the chat list moves to the right while the drawer opens, as a fraction of the drawer's own travel. */
    public static final float DRAWER_PARALLAX = 0.2f;

    /** Maximum dim over the content while the drawer is fully open (12.1.1 used 0x99000000). */
    public static final int DRAWER_SCRIM_ALPHA = 0x99;

    /** White lift used for "raised" surfaces on a dark background when the theme gives no usable overlay. */
    private static final int DARK_LIFT = 0x08ffffff;

    public static boolean enabled() {
        return RetroConfig.isRetroUi();
    }

    /**
     * Theme key to read in place of {@code key}. 12.2+ draws the chat chrome icons (input bar: emoji, attach, bot, notify, gift, ...;
     * round buttons; emoji panel tabs; sticker set names) in {@code glass_defaultIcon}, a 60% translucent near-black in the default
     * theme; 12.1.1 drew the same icons in {@code chat_messagePanelIcons} (opaque grey). Themes that predate 12.2 never define the glass
     * key and already fall back to the panel key, so in retro mode every theme reads the panel key. Called from {@code Theme.getColor}
     * and {@code ChatActivity.ThemeDelegate.getColor} only when {@code key} is the glass key, so the hot path stays one int compare.
     */
    public static int themeKey(int key) {
        if (key == Theme.key_glass_defaultIcon && enabled()) {
            return Theme.key_chat_messagePanelIcons;
        }
        return key;
    }

    /**
     * Overlay drawn over the list background behind pinned chats. 12.1.1 drew {@code chats_pinnedOverlay}; 13.1.1
     * commented that out. On a dark background a black-based overlay (the light-theme default) would make the rows darker
     * instead of lighter, so it is replaced by a faint white one there.
     */
    public static int pinnedOverlay(int background, int themeOverlay) {
        if (isDark(background) && Color.alpha(themeOverlay) > 0 && ColorUtils.calculateLuminance(themeOverlay | 0xff000000) < 0.5) {
            return DARK_LIFT;
        }
        return themeOverlay;
    }

    public static int pinnedOverlay(Theme.ResourcesProvider resourcesProvider) {
        return pinnedOverlay(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), Theme.getColor(Theme.key_chats_pinnedOverlay, resourcesProvider));
    }

    /**
     * The background colour with the pinned overlay applied: the colour of pinned rows, and in retro mode also of the
     * chat list header (action bar + folder tabs), which sit one step above the list as in the classic look.
     */
    public static int raised(int background, Theme.ResourcesProvider resourcesProvider) {
        int overlay = pinnedOverlay(background, Theme.getColor(Theme.key_chats_pinnedOverlay, resourcesProvider));
        return ColorUtils.compositeColors(overlay, background | 0xff000000);
    }

    /** Status dot of a user who is online: the theme accent (the colour of the compose button). */
    public static int onlineDotColor(Theme.ResourcesProvider resourcesProvider) {
        return Theme.getColor(Theme.key_chats_actionBackground, resourcesProvider);
    }

    /** Status dot of a user who is offline. */
    public static int offlineDotColor(Theme.ResourcesProvider resourcesProvider) {
        return 0xff888888;
    }

    /** Whether the chat list shows a status dot for every user (grey when offline), not only for online ones. */
    public static boolean alwaysShowStatusDot() {
        return enabled();
    }

    /**
     * Removes bold from a chat list preview: bold message entities ({@link TextStyleSpan} with the bold flag) and bold
     * {@link TypefaceSpan}s. Upstream already strips {@code StyleSpan(BOLD)} and link spans, but not these two.
     */
    public static void stripBold(Spannable text) {
        if (!enabled() || text == null) {
            return;
        }
        TextStyleSpan[] styleSpans = text.getSpans(0, text.length(), TextStyleSpan.class);
        for (TextStyleSpan span : styleSpans) {
            if ((span.getStyleFlags() & TextStyleSpan.FLAG_STYLE_BOLD) == 0) {
                continue;
            }
            // The run can be shared with the message bubble, so replace the span with a copy instead of editing it.
            int start = text.getSpanStart(span);
            int end = text.getSpanEnd(span);
            int spanFlags = text.getSpanFlags(span);
            TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun(span.getTextStyleRun());
            run.flags &= ~TextStyleSpan.FLAG_STYLE_BOLD;
            text.removeSpan(span);
            if (start >= 0 && end > start) {
                text.setSpan(new TextStyleSpan(run), start, end, spanFlags);
            }
        }
        TypefaceSpan[] typefaceSpans = text.getSpans(0, text.length(), TypefaceSpan.class);
        for (TypefaceSpan span : typefaceSpans) {
            if (span.isBold()) {
                text.removeSpan(span);
            }
        }
    }

    /**
     * Wraps {@code text} so it draws in the regular weight even when the paint is bold. Used for file names and link
     * previews in message bubbles, which upstream draws with the bold "name" paints.
     */
    public static CharSequence regular(CharSequence text) {
        if (!enabled() || TextUtils.isEmpty(text)) {
            return text;
        }
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        builder.setSpan(new TypefaceSpan(regularTypeface()), 0, builder.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        return builder;
    }

    /**
     * The regular weight of the font body text uses: the picked font when there is one, else the system default (what
     * message text paints use, since they set no typeface).
     */
    public static Typeface regularTypeface() {
        if (RetroFont.isActive()) {
            return AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_REGULAR);
        }
        return Typeface.DEFAULT;
    }

    static boolean isDark(int color) {
        return ColorUtils.calculateLuminance(color | 0xff000000) < 0.5;
    }
}
