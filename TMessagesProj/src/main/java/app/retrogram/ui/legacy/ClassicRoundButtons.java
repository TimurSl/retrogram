/*
 * This is the source code of Retrogram for Android. It is licensed under GNU GPL v. 3 or later.
 *
 * Drawing revived from Telegram 12.1.1 (DrKLO/Telegram @ 9cbf0333),
 * TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java, createView():
 * pagedownButtonImage / mentiondownButtonImage / reactionsMentiondownButtonImage backgrounds.
 */
package app.retrogram.ui.legacy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CombinedDrawable;

/**
 * The classic (12.1.1) floating chat buttons: page down, mentions, reactions and the other side controls.
 * A solid 42dp circle in {@code chat_goDownButton} on top of the {@code pagedown_shadow} bitmap (46dp),
 * with a list-selector ripple, instead of the 12.4 glass circle. Used by
 * {@code ChatActivityBlurredRoundButton} when {@code RetroConfig.isClassicChatButtons()} is on (WP 2.3).
 */
public final class ClassicRoundButtons {

    /** Diameter of the solid circle, as in 12.1.1. */
    public static final int CIRCLE_SIZE = 42;
    /** Size of the shadow bitmap the circle sits on, as in 12.1.1 (the button's image view was 46x46). */
    public static final int SHADOW_SIZE = 46;

    private ClassicRoundButtons() {
    }

    /**
     * Background for a button view of {@code viewSizeDp} x {@code viewSizeDp}: shadow plus circle, centered.
     * Set it with {@code View.setBackground} so pressed state and ripple hotspot work as for any view background.
     */
    public static Drawable background(Context context, int viewSizeDp, Theme.ResourcesProvider resourcesProvider) {
        final Drawable circle = Theme.createSimpleSelectorCircleDrawable(dp(CIRCLE_SIZE),
            Theme.getColor(Theme.key_chat_goDownButton, resourcesProvider),
            Theme.getColor(Theme.key_listSelector, resourcesProvider));
        final Drawable shadow = ContextCompat.getDrawable(context, R.drawable.pagedown_shadow).mutate();
        shadow.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
        final CombinedDrawable combined = new CombinedDrawable(shadow, circle, 0, 0);
        combined.setIconSize(dp(CIRCLE_SIZE), dp(CIRCLE_SIZE));
        final int inset = dp(Math.max(0, viewSizeDp - SHADOW_SIZE) / 2f);
        return inset > 0 ? new InsetDrawable(combined, inset) : combined;
    }

    /**
     * Icon tint. 12.1.1 used {@code chat_goDownButtonIcon} (default 0xff8e959b), which 13.x removed.
     * {@code chat_messagePanelIcons} has the same default and is set by the bundled and most custom themes.
     */
    public static int iconColor(Theme.ResourcesProvider resourcesProvider) {
        return Theme.getColor(Theme.key_chat_messagePanelIcons, resourcesProvider);
    }
}
