package org.telegram.ui.Components.chat.layouts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.chat.buttons.ChatActivityBlurredRoundButton;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

@SuppressLint("ViewConstructor")
public class ChatActivityActionsButtonsLayout extends LinearLayout {
    private final Theme.ResourcesProvider resourcesProvider;

    private final ButtonHolder replyButton = new ButtonHolder();
    private final ButtonHolder forwardButton = new ButtonHolder();

    public ChatActivityActionsButtonsLayout(@NonNull Context context,
                                            Theme.ResourcesProvider resourcesProvider,
                                            BlurredBackgroundColorProvider colorProvider,
                                            BlurredBackgroundDrawableViewFactory blurredBackgroundDrawableViewFactory) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        replyButton.button = ChatActivityBlurredRoundButton.create(
            context, blurredBackgroundDrawableViewFactory, colorProvider, resourcesProvider
        );
        replyButton.button.setOnClickListener(v -> {});
        ScaleStateListAnimator.apply(replyButton.button, .065f, 2f);

        forwardButton.button = ChatActivityBlurredRoundButton.create(
            context, blurredBackgroundDrawableViewFactory, colorProvider, resourcesProvider
        );
        forwardButton.button.setOnClickListener(v -> {});
        ScaleStateListAnimator.apply(forwardButton.button, .065f, 2f);

        addTextView(replyButton, LocaleController.getString(R.string.Reply), R.drawable.input_reply, false);
        addTextView(forwardButton, LocaleController.getString(R.string.Forward), R.drawable.input_forward, true);

        setOrientation(HORIZONTAL);
        setClipChildren(false);

        addView(replyButton.button, LayoutHelper.createLinear(0, 56, 1f, 1, 0, -1, 0));
        addView(forwardButton.button, LayoutHelper.createLinear(0, 56, 1f, -1, 0, 1, 0));
    }

    public void setReplyButtonOnClickListener(View.OnClickListener listener) {
        replyButton.button.setOnClickListener(listener);
    }

    public void setForwardButtonOnClickListener(View.OnClickListener listener) {
        forwardButton.button.setOnClickListener(listener);
    }

    public View getForwardButton() {
        return forwardButton.button;
    }

    public View getReplyButton() {
        return replyButton.button;
    }

    /*private void addTextView(ButtonHolder button, String text, @DrawableRes int iconRes, boolean iconLeft) {
        TextView forwardButton = new TextView(getContext());
        forwardButton.setText(text);
        forwardButton.setGravity(Gravity.CENTER_VERTICAL);
        forwardButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        forwardButton.setPadding(AndroidUtilities.dp(21), 0, AndroidUtilities.dp(21), 0);
        forwardButton.setCompoundDrawablePadding(AndroidUtilities.dp(6));
        forwardButton.setTextColor(Theme.getColor(Theme.key_glass_defaultText, resourcesProvider));
        forwardButton.setTypeface(AndroidUtilities.bold());
        Drawable image = getContext().getResources().getDrawable(iconRes).mutate();
        image.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_glass_defaultIcon, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        forwardButton.setCompoundDrawablesWithIntrinsicBounds(iconLeft ? image : null, null, iconLeft ? null : image, null);

        button.textView = forwardButton;
        button.button.addView(forwardButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        if (getDialogId() == UserObject.VERIFY) {
            forwardButton.setVisibility(View.GONE);
        }
    }*/

    private void addTextView(ButtonHolder button, String text, @DrawableRes int iconRes, boolean iconLeft) {
        TextView textView = new TextView(getContext());
        textView.setGravity(Gravity.CENTER_VERTICAL);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setPadding(AndroidUtilities.dp(21), 0, AndroidUtilities.dp(21), 0);
        textView.setCompoundDrawablePadding(AndroidUtilities.dp(6));
        textView.setTypeface(AndroidUtilities.bold());
        button.textView = textView;
        button.button.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        setButtonTextAndIcon(button, text, iconRes, iconLeft);
    }

    private void setButtonTextAndIcon(ButtonHolder holder, String text, @DrawableRes int iconRes, boolean iconLeft) {
        if (holder == null || holder.textView == null) {
            return;
        }
        holder.textView.setText(text);
        holder.textView.setTextColor(Theme.getColor(classicStyle ? Theme.key_actionBarActionModeDefaultIcon : Theme.key_glass_defaultText, resourcesProvider));
        Drawable image = null;
        if (iconRes != 0) {
            image = getContext().getResources().getDrawable(iconRes).mutate();
            image.setColorFilter(new PorterDuffColorFilter(Theme.getColor(classicStyle ? Theme.key_actionBarActionModeDefaultIcon : Theme.key_glass_defaultIcon, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        }
        holder.textView.setCompoundDrawablesWithIntrinsicBounds(iconLeft ? image : null, null, iconLeft ? null : image, null);
    }


    public void showReplyButton(boolean visible, boolean animated) {
        replyButton.visibilityAnimator.setValue(visible, animated);
    }

    public void setReplyButtonEnabled(boolean enabled, boolean animated) {
        replyButton.enabledAnimator.setValue(enabled, animated);
        replyButton.button.setEnabled(enabled);
    }

    public void showForwardButton(boolean visible, boolean animated) {
        forwardButton.visibilityAnimator.setValue(visible, animated);
    }

    public void setForwardButtonEnabled(boolean enabled, boolean animated) {
        forwardButton.enabledAnimator.setValue(enabled, animated);
        forwardButton.button.setEnabled(enabled);
    }






    public void updateColors() {
        replyButton.button.updateColors();
        forwardButton.button.updateColors();
        if (classicStyle) {
            applyClassicColors(replyButton);
            applyClassicColors(forwardButton);
            invalidate();
        }
    }

    // Retrogram (WP 2.3b): the 12.1.1 selection-mode bar ("bottomMessagesActionContainer", 9cbf0333 ChatActivity):
    // one full-width solid bar with the compose shadow on top, Reply on the left and Forward on the right, both as plain
    // text buttons with a rectangular pressed state. The two buttons slide up with the bar instead of in from the sides.
    private static final int CLASSIC_BAR_HEIGHT = 50;
    private boolean classicStyle;
    private Paint classicBarPaint;

    public void setClassicStyle() {
        if (classicStyle) {
            return;
        }
        classicStyle = true;
        classicBarPaint = new Paint();
        setWillNotDraw(false);
        replyButton.button.setFlatStyle();
        forwardButton.button.setFlatStyle();
        replyButton.button.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, CLASSIC_BAR_HEIGHT, Gravity.BOTTOM));
        forwardButton.button.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, CLASSIC_BAR_HEIGHT, Gravity.BOTTOM));
        // The gap between the buttons is part of the bar: it takes taps so they do not reach the messages under it.
        final View gap = new View(getContext());
        gap.setClickable(true);
        addView(gap, indexOfChild(forwardButton.button), LayoutHelper.createLinear(0, CLASSIC_BAR_HEIGHT, 1f));
        replyButton.textView.setPadding(dp(14), 0, dp(21), 0);
        replyButton.textView.setCompoundDrawablePadding(dp(7));
        applyClassicColors(replyButton);
        applyClassicColors(forwardButton);
        checkButtonsPositionsAndVisibility();
        invalidate();
    }

    private void applyClassicColors(ButtonHolder holder) {
        final int color = Theme.getColor(Theme.key_actionBarActionModeDefaultIcon, resourcesProvider);
        holder.button.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector, resourcesProvider), 3));
        holder.textView.setTextColor(color);
        for (Drawable d : holder.textView.getCompoundDrawables()) {
            if (d != null) {
                d.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
            }
        }
    }

    private int classicBarTop() {
        return getMeasuredHeight() - dp(CLASSIC_BAR_HEIGHT) + Math.round(dp(54) * (1f - totalVisibilityFactor));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!classicStyle || totalVisibilityFactor <= 0) {
            return;
        }
        final int top = classicBarTop();
        final Drawable shadow = Theme.chat_composeShadowDrawable;
        if (shadow != null) {
            shadow.setBounds(0, top - shadow.getIntrinsicHeight(), getMeasuredWidth(), top);
            shadow.draw(canvas);
        }
        classicBarPaint.setColor(Theme.getColor(Theme.key_chat_messagePanelBackground, resourcesProvider));
        canvas.drawRect(0, top, getMeasuredWidth(), getMeasuredHeight(), classicBarPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        checkButtonsPositionsAndVisibility();
    }

    private float totalVisibilityFactor;
    public void setTotalVisibilityFactor(float factor) {
        if (totalVisibilityFactor != factor) {
            totalVisibilityFactor = factor;
            checkButtonsPositionsAndVisibility();
            if (classicStyle) {
                invalidate();
            }
        }
    }

    private void checkButtonsPositionsAndVisibility() {
        checkHolderPositionsAndVisibility(forwardButton);
        checkHolderPositionsAndVisibility(replyButton);
    }

    private void checkHolderPositionsAndVisibility(ButtonHolder holder) {
        final float visibility = totalVisibilityFactor * holder.visibilityAnimator.getFloatValue();
        if (classicStyle) {
            holder.button.setTranslationX(0);
            holder.button.setTranslationY(dp(54) * (1f - totalVisibilityFactor));
            holder.button.setAlpha(visibility);
            holder.button.setVisibility(visibility > 0 ? VISIBLE : INVISIBLE);
            return;
        }
        final float offsetY = -dp(54) * (1f - visibility);
        float offsetX = getMeasuredWidth() / 2f * (1f - AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(visibility));
        if (holder == replyButton) {
            offsetX *= -1;
        }

        holder.button.setTranslationX(offsetX);
        holder.button.setTranslationY(offsetY);
        holder.button.setAlpha(visibility);
        holder.button.setVisibility(visibility > 0 ? VISIBLE : INVISIBLE);
    }

    private class ButtonHolder implements FactorAnimator.Target {
        public ChatActivityBlurredRoundButton button;
        public TextView textView;

        public BoolAnimator visibilityAnimator = new BoolAnimator(0, this, CubicBezierInterpolator.EASE_OUT_QUINT, 320, true);
        public BoolAnimator enabledAnimator = new BoolAnimator(1, this, CubicBezierInterpolator.EASE_OUT_QUINT, 320, true);

        @Override
        public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
            textView.setAlpha(lerp(0.5f, 1, enabledAnimator.getFloatValue()));
            checkHolderPositionsAndVisibility(this);
        }
    }

    public void setReplyButtonOnLongClickListener(View.OnLongClickListener listener) {
        replyButton.button.setOnLongClickListener(listener);
    }

    public void setForwardButtonOnLongClickListener(View.OnLongClickListener listener) {
        forwardButton.button.setOnLongClickListener(listener);
    }

    public void setReplyButtonTextAndIcon(String text, @DrawableRes int iconRes) {
        boolean iconLeft = iconRes == R.drawable.msg_saved || iconRes == R.drawable.msg_share;
        setButtonTextAndIcon(replyButton, text, iconRes, iconLeft);
    }

    public void setReplyButtonTextAndIcon(String text, @DrawableRes int iconRes, boolean iconLeft) {
        setButtonTextAndIcon(replyButton, text, iconRes, iconLeft);
    }

    public void setForwardButtonTextAndIcon(String text, @DrawableRes int iconRes, boolean iconLeft) {
        setButtonTextAndIcon(forwardButton, text, iconRes, iconLeft);
    }

}
