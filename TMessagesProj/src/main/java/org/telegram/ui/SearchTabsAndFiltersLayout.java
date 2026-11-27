package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

public class SearchTabsAndFiltersLayout extends FrameLayout implements Theme.Colorable {
    private final Path clipPath = new Path();
    private BlurredBackgroundDrawable blurredBackgroundDrawable;
    /** Retrogram: a flat full-width strip on the white search header (12.1.1) instead of the rounded glass pill. */
    public final boolean classic = app.retrogram.RetroConfig.isRetroUi();

    public SearchTabsAndFiltersLayout(@NonNull Context context) {
        super(context);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        clipPath.rewind();
        clipPath.addRoundRect(dp(9), dp(9), w - dp(9), h - dp(9),
                dp(16), dp(16), Path.Direction.CW);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (classic) {
            super.dispatchDraw(canvas);
            return;
        }
        canvas.save();
        canvas.clipPath(clipPath);
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    public void setBlurredBackground(BlurredBackgroundDrawable drawable) {
        if (classic) {
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            return;
        }
        setBackground(blurredBackgroundDrawable = drawable);
    }

    @Override
    public void updateColors() {
        if (classic) {
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            return;
        }
        if (blurredBackgroundDrawable != null) {
            blurredBackgroundDrawable.updateColors();
        }
    }
}
