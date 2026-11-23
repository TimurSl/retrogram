package app.retrogram.xmpp.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.MessageDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MsgClockDrawable;

import app.retrogram.xmpp.XmppFiles;
import app.retrogram.xmpp.core.XmppMessage;

/**
 * A message bubble drawn like Telegram's: the theme's {@link MessageDrawable} (tail on the last message of a run,
 * bubble colours and gradients of the chat theme), time and check marks at the bottom right. Incoming on the left,
 * outgoing on the right, at most 80% of the width.
 */
class XmppMessageRow extends FrameLayout {

    interface ImageClick {
        void onImageClick(XmppMessage m, boolean loaded);
    }

    // Space the MessageDrawable keeps around its body: 2dp all round, plus 6dp for the tail on the tail side.
    private static final int TAIL_SIDE = 17;
    private static final int OTHER_SIDE = 11;

    private final LinearLayout bubble;
    private final TextView sender;
    private final LinearLayout quote;
    private final TextView quoteAuthor;
    private final TextView quoteText;
    private final ImageView image;
    private final TextView imagePlaceholder;
    private final TextView text;
    private final TextView footer;
    private final StatusDrawable statusDrawable;
    private boolean outgoing;
    private boolean topNear;
    private boolean bottomNear;
    private String boundUrl;

    XmppMessageRow(Context context) {
        super(context);
        setWillNotDraw(false);
        statusDrawable = new StatusDrawable(context);

        bubble = new LinearLayout(context);
        bubble.setOrientation(LinearLayout.VERTICAL);
        addView(bubble, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT, 3, 1, 0, 1));

        sender = new TextView(context);
        sender.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        sender.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
        sender.setSingleLine(true);
        bubble.addView(sender, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));

        quote = new LinearLayout(context);
        quote.setOrientation(LinearLayout.VERTICAL);
        quote.setPadding(dp(9), dp(3), dp(6), dp(3));
        quoteAuthor = new TextView(context);
        quoteAuthor.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        quoteAuthor.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
        quoteAuthor.setSingleLine(true);
        quote.addView(quoteAuthor, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        quoteText = new TextView(context);
        quoteText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        quoteText.setMaxLines(2);
        quoteText.setEllipsize(TextUtils.TruncateAt.END);
        quote.addView(quoteText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        bubble.addView(quote, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 4));

        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setAdjustViewBounds(true);
        image.setMaxWidth(dp(240));
        image.setMaxHeight(dp(280));
        bubble.addView(image, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 4));

        imagePlaceholder = new TextView(context);
        imagePlaceholder.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        imagePlaceholder.setGravity(Gravity.CENTER);
        imagePlaceholder.setMinWidth(dp(180));
        imagePlaceholder.setPadding(dp(8), dp(24), dp(8), dp(24));
        bubble.addView(imagePlaceholder, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 4));

        text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, org.telegram.messenger.SharedConfig.fontSize);
        text.setTextIsSelectable(false);
        text.setLineSpacing(dp(1), 1f);
        bubble.addView(text, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        footer = new TextView(context);
        footer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        footer.setSingleLine(true);
        footer.setCompoundDrawablePadding(dp(3));
        bubble.addView(footer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT, 8, 0, -2, 0));
    }

    /**
     * @param senderLabel     shown above incoming group messages; null to hide
     * @param quoteAuthorText author of the replied-to message, or null
     * @param quoteBody       text of the replied-to message, or null
     * @param autoLoad        fetch an image attachment without asking
     * @param progress        0..1 while uploading, or -1
     * @param topNear         the message above is from the same sender: smaller gap, smaller top corner
     * @param bottomNear      the message below is from the same sender: no tail on this one
     */
    void set(XmppMessage m, String senderLabel, int senderColor, String quoteAuthorText, String quoteBody,
             boolean autoLoad, float progress, boolean topNear, boolean bottomNear, ImageClick imageClick) {
        outgoing = m.outgoing;
        this.topNear = topNear;
        this.bottomNear = bottomNear;
        int textColor = Theme.getColor(m.outgoing ? Theme.key_chat_messageTextOut : Theme.key_chat_messageTextIn);
        int timeColor = Theme.getColor(m.outgoing ? Theme.key_chat_outTimeText : Theme.key_chat_inTimeText);
        bubble.setBackground(null);
        bubble.setPadding(dp(m.outgoing ? OTHER_SIDE : TAIL_SIDE), dp(7), dp(m.outgoing ? TAIL_SIDE : OTHER_SIDE), dp(6));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) bubble.getLayoutParams();
        lp.gravity = m.outgoing ? Gravity.RIGHT : Gravity.LEFT;
        lp.topMargin = dp(topNear ? 1 : 4);
        lp.bottomMargin = dp(1);
        bubble.setLayoutParams(lp);

        if (senderLabel != null && !m.outgoing && !topNear) {
            sender.setVisibility(VISIBLE);
            sender.setText(senderLabel);
            sender.setTextColor(senderColor);
        } else {
            sender.setVisibility(GONE);
        }

        if (quoteAuthorText != null || quoteBody != null) {
            quote.setVisibility(VISIBLE);
            int bar = Theme.getColor(m.outgoing ? Theme.key_chat_outReplyLine : Theme.key_chat_inReplyLine);
            quote.setBackground(new QuoteBackground(bar));
            quoteAuthor.setText(quoteAuthorText == null ? "" : quoteAuthorText);
            quoteAuthor.setTextColor(Theme.getColor(m.outgoing ? Theme.key_chat_outReplyNameText : Theme.key_chat_inReplyNameText));
            quoteText.setText(quoteBody == null ? "" : quoteBody);
            quoteText.setTextColor(Theme.getColor(m.outgoing ? Theme.key_chat_outReplyMessageText : Theme.key_chat_inReplyMessageText));
        } else {
            quote.setVisibility(GONE);
        }

        boolean isImage = m.hasAttachment() && XmppFiles.looksLikeImage(m.attachmentUrl);
        image.setVisibility(GONE);
        imagePlaceholder.setVisibility(GONE);
        boundUrl = isImage ? m.attachmentUrl : null;
        if (isImage) {
            Bitmap cached = XmppFiles.cachedImage(m.attachmentUrl);
            if (cached != null) {
                showImage(cached, m, imageClick);
            } else if (autoLoad) {
                imagePlaceholder.setVisibility(VISIBLE);
                imagePlaceholder.setTextColor(textColor);
                imagePlaceholder.setText(LocaleController.getString(R.string.XmppLoadingImage));
                final String url = m.attachmentUrl;
                XmppFiles.loadImage(url, new XmppFiles.ImageCallback() {
                    @Override
                    public void onImage(Bitmap bitmap) {
                        if (url.equals(boundUrl)) {
                            showImage(bitmap, m, imageClick);
                        }
                    }

                    @Override
                    public void onFailure() {
                        if (url.equals(boundUrl)) {
                            imagePlaceholder.setText(LocaleController.getString(R.string.XmppImageFailed));
                        }
                    }
                });
            } else {
                imagePlaceholder.setVisibility(VISIBLE);
                imagePlaceholder.setTextColor(textColor);
                imagePlaceholder.setText(LocaleController.formatString(R.string.XmppTapToLoadImage, hostOf(m.attachmentUrl)));
                imagePlaceholder.setOnClickListener(v -> imageClick.onImageClick(m, false));
            }
        }

        String body = m.body == null ? "" : m.body;
        boolean attachmentOnly = m.hasAttachment() && body.equals(m.attachmentUrl);
        if (attachmentOnly) {
            body = isImage ? "" : "📎 " + XmppFiles.fileNameOf(m.attachmentUrl);
        } else if (m.hasAttachment() && m.outgoing && !m.attachmentPath.isEmpty() && m.status == XmppMessage.STATUS_PENDING) {
            body = "📎 " + body;
        }
        if (body.isEmpty()) {
            text.setVisibility(GONE);
        } else {
            text.setVisibility(VISIBLE);
            text.setText(body);
            text.setTextColor(textColor);
            text.setLinkTextColor(Theme.getColor(m.outgoing ? Theme.key_chat_messageLinkOut : Theme.key_chat_messageLinkIn));
        }

        StringBuilder f = new StringBuilder();
        if (progress >= 0 && progress < 1) {
            f.append(LocaleController.formatString(R.string.XmppUploading, (int) (progress * 100))).append("  ");
        }
        if (m.status == XmppMessage.STATUS_FAILED) {
            f.append(LocaleController.getString(R.string.XmppFailedTapToRetry)).append("  ");
        }
        if (m.edited) {
            f.append(LocaleController.getString(R.string.XmppEdited)).append(' ');
        }
        f.append(XmppUi.messageTime(m.time));
        footer.setText(f);
        footer.setTextColor(m.status == XmppMessage.STATUS_FAILED ? Theme.getColor(Theme.key_text_RedRegular) : timeColor);
        if (m.outgoing && statusDrawable.setStatus(m.status)) {
            footer.setCompoundDrawablesWithIntrinsicBounds(null, null, statusDrawable, null);
        } else {
            footer.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
        }
        invalidate();
    }

    private void showImage(Bitmap b, XmppMessage m, ImageClick click) {
        imagePlaceholder.setVisibility(GONE);
        image.setVisibility(VISIBLE);
        image.setImageBitmap(b);
        image.setOnClickListener(v -> click.onImageClick(m, true));
    }

    private static String hostOf(String url) {
        try {
            String h = new java.net.URL(url).getHost();
            return h == null ? "" : h;
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        // The bubble is allowed 80% of the row; its own margins are part of that.
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) bubble.getLayoutParams();
        int max = (int) (w * 0.8f);
        bubble.measure(MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        setMeasuredDimension(w, bubble.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) bubble.getLayoutParams();
        int bw = bubble.getMeasuredWidth();
        int bh = bubble.getMeasuredHeight();
        int l = outgoing ? (right - left) - bw : lp.leftMargin;
        bubble.layout(l, lp.topMargin, l + bw, lp.topMargin + bh);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        MessageDrawable d = outgoing ? Theme.chat_msgOutDrawable : Theme.chat_msgInDrawable;
        if (d == null) {
            return;
        }
        // The theme's bubble drawables are shared by every cell; like ChatMessageCell, position them right before drawing.
        View parent = (View) getParent();
        int parentHeight = parent != null ? parent.getMeasuredHeight() : getMeasuredHeight();
        int parentWidth = parent != null ? parent.getMeasuredWidth() : getMeasuredWidth();
        d.setTop((int) getY() + bubble.getTop(), parentWidth, parentHeight, topNear, bottomNear);
        d.setBounds(bubble.getLeft(), bubble.getTop(), bubble.getRight(), bubble.getBottom());
        d.draw(canvas);
        Drawable shadow = d.getShadowDrawable();
        if (shadow != null) {
            shadow.setBounds(bubble.getLeft(), bubble.getTop(), bubble.getRight(), bubble.getBottom());
            shadow.draw(canvas);
        }
    }

    /** The reply preview's background: a tinted box with the accent line on the left, like Telegram's reply block. */
    private static final class QuoteBackground extends GradientDrawable {
        private final android.graphics.Paint line = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF rect = new android.graphics.RectF();

        QuoteBackground(int color) {
            setColor(XmppUi.dimColor(color, 0.10f));
            setCornerRadius(dp(4));
            line.setColor(color);
        }

        @Override
        public void draw(Canvas canvas) {
            super.draw(canvas);
            android.graphics.Rect b = getBounds();
            rect.set(b.left, b.top, b.left + dp(3), b.bottom);
            canvas.save();
            canvas.clipRect(rect);
            rect.right = b.left + dp(8);
            canvas.drawRoundRect(rect, dp(4), dp(4), line);
            canvas.restore();
        }
    }

    /** Telegram's message status icons: clock while sending, one check when the server has it, two when delivered, two in the "read" colour when displayed. */
    private static final class StatusDrawable extends Drawable {
        private final Drawable check;
        private final Drawable halfCheck;
        private final MsgClockDrawable clock = new MsgClockDrawable();
        private int status;

        StatusDrawable(Context context) {
            check = context.getResources().getDrawable(R.drawable.msg_check_s).mutate();
            halfCheck = context.getResources().getDrawable(R.drawable.msg_halfcheck_s).mutate();
        }

        /** Returns false when there is nothing to show for this status. */
        boolean setStatus(int status) {
            this.status = status;
            int color;
            switch (status) {
                case XmppMessage.STATUS_PENDING:
                    clock.setColor(Theme.getColor(Theme.key_chat_outSentClock));
                    return true;
                case XmppMessage.STATUS_SENT:
                case XmppMessage.STATUS_DELIVERED:
                    color = Theme.getColor(Theme.key_chat_outSentCheck);
                    break;
                case XmppMessage.STATUS_DISPLAYED:
                    color = Theme.getColor(Theme.key_chat_outSentCheckRead);
                    break;
                default:
                    return false;
            }
            PorterDuffColorFilter filter = new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY);
            check.setColorFilter(filter);
            halfCheck.setColorFilter(filter);
            invalidateSelf();
            return true;
        }

        @Override
        public int getIntrinsicWidth() {
            return check.getIntrinsicWidth() + dp(4);
        }

        @Override
        public int getIntrinsicHeight() {
            return check.getIntrinsicHeight();
        }

        @Override
        public void draw(Canvas canvas) {
            android.graphics.Rect b = getBounds();
            int w = check.getIntrinsicWidth();
            int h = check.getIntrinsicHeight();
            int y = b.centerY() - h / 2;
            if (status == XmppMessage.STATUS_PENDING) {
                int cw = clock.getIntrinsicWidth();
                int ch = clock.getIntrinsicHeight();
                int cx = b.right - cw;
                int cy = b.centerY() - ch / 2;
                clock.setBounds(cx, cy, cx + cw, cy + ch);
                clock.draw(canvas);
                return;
            }
            if (status == XmppMessage.STATUS_SENT) {
                check.setBounds(b.right - w, y, b.right, y + h);
                check.draw(canvas);
                return;
            }
            // Two checks: the full one on the left, the half one 4dp to its right (as in ChatMessageCell).
            check.setBounds(b.right - w - dp(4), y, b.right - dp(4), y + h);
            check.draw(canvas);
            int hw = halfCheck.getIntrinsicWidth();
            int hh = halfCheck.getIntrinsicHeight();
            int hy = b.centerY() - hh / 2;
            halfCheck.setBounds(b.right - hw, hy, b.right, hy + hh);
            halfCheck.draw(canvas);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
