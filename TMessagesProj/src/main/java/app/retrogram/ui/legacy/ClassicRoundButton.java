package app.retrogram.ui.legacy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CombinedDrawable;

/**
 * The look of the chat's round floating buttons (scroll down, mentions, reactions, search up/down) in Telegram 12.1.1:
 * a solid {@code key_chat_goDownButton} circle of 42dp over the {@code pagedown_shadow} image (46dp).
 *
 * Revived from {@code 9cbf0333:TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java} (the
 * {@code pagedownButtonImage} / {@code mentiondownButtonImage} / {@code reactionsMentiondownButtonImage} backgrounds) and
 * {@code res/drawable-*dpi/pagedown_shadow.png} from the same commit. Used by
 * {@code ChatActivityBlurredRoundButton} in place of its blur3 drawable when
 * Settings → Retrogram → "Classic chat buttons" is on (WP 2.3a).
 */
public final class ClassicRoundButton {
    private ClassicRoundButton() {}

    /** Diameter of the solid circle, as in 12.1.1. */
    public static final int CIRCLE_SIZE = 42;
    /** Size of the shadow image the circle sits in. */
    public static final int SHADOW_SIZE = 46;

    /** Shadow plus solid circle. Draw it with {@link #draw}, which centers it in the view. */
    public static Drawable createBackground(Context context, Theme.ResourcesProvider resourcesProvider) {
        final Drawable circle = Theme.createCircleDrawable(dp(CIRCLE_SIZE), Theme.getColor(Theme.key_chat_goDownButton, resourcesProvider));
        final Drawable shadow = ContextCompat.getDrawable(context, R.drawable.pagedown_shadow);
        if (shadow == null) {
            return circle;
        }
        final Drawable tintedShadow = shadow.mutate();
        tintedShadow.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
        final CombinedDrawable combined = new CombinedDrawable(tintedShadow, circle, 0, 0);
        combined.setIconSize(dp(CIRCLE_SIZE), dp(CIRCLE_SIZE));
        return combined;
    }

    /** Pressed-state overlay matching the 42dp circle in a {@code size}-dp square view. */
    public static Drawable createPressedSelector(int pressedColor, int viewSizeDp) {
        final int inset = dp((viewSizeDp - CIRCLE_SIZE) / 2f);
        return Theme.createInsetRoundRectDrawable(pressedColor, dp(CIRCLE_SIZE / 2f), inset);
    }

    /** Draws {@code background} centered in a {@code width} x {@code height} view. */
    public static void draw(Canvas canvas, Drawable background, int width, int height) {
        final int half = dp(SHADOW_SIZE) / 2;
        final int cx = width / 2;
        final int cy = height / 2;
        background.setBounds(cx - half, cy - half, cx + half, cy + half);
        background.draw(canvas);
    }
}
