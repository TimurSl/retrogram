package app.retrogram.xmpp.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
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
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import app.retrogram.xmpp.XmppFiles;
import app.retrogram.xmpp.core.XmppMessage;

/** A message bubble. Incoming on the left, outgoing on the right, at most 80% of the width. */
class XmppMessageRow extends FrameLayout {

    interface ImageClick {
        void onImageClick(XmppMessage m, boolean loaded);
    }

    private final LinearLayout bubble;
    private final TextView sender;
    private final LinearLayout quote;
    private final TextView quoteAuthor;
    private final TextView quoteText;
    private final ImageView image;
    private final TextView imagePlaceholder;
    private final TextView text;
    private final TextView footer;
    private boolean outgoing;
    private String boundUrl;

    XmppMessageRow(Context context) {
        super(context);
        bubble = new LinearLayout(context);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(10), dp(6), dp(10), dp(5));
        addView(bubble, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT, 8, 2, 8, 2));

        sender = new TextView(context);
        sender.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        sender.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
        sender.setSingleLine(true);
        bubble.addView(sender, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));

        quote = new LinearLayout(context);
        quote.setOrientation(LinearLayout.VERTICAL);
        quote.setPadding(dp(8), dp(2), dp(4), dp(2));
        quoteAuthor = new TextView(context);
        quoteAuthor.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        quoteAuthor.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
        quoteAuthor.setSingleLine(true);
        quote.addView(quoteAuthor, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        quoteText = new TextView(context);
        quoteText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        quoteText.setMaxLines(2);
        quoteText.setEllipsize(TextUtils.TruncateAt.END);
        quote.addView(quoteText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        bubble.addView(quote, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

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
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        text.setTextIsSelectable(false);
        bubble.addView(text, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        footer = new TextView(context);
        footer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        footer.setSingleLine(true);
        bubble.addView(footer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT, 0, 2, 0, 0));
    }

    /**
     * @param senderLabel   shown above incoming group messages; null to hide
     * @param quoteAuthorText author of the replied-to message, or null
     * @param quoteBody     text of the replied-to message, or null
     * @param autoLoad      fetch an image attachment without asking
     * @param progress      0..1 while uploading, or -1
     */
    void set(XmppMessage m, String senderLabel, int senderColor, String quoteAuthorText, String quoteBody,
             boolean autoLoad, float progress, ImageClick imageClick) {
        outgoing = m.outgoing;
        int textColor = Theme.getColor(m.outgoing ? Theme.key_chat_messageTextOut : Theme.key_chat_messageTextIn);
        int timeColor = Theme.getColor(m.outgoing ? Theme.key_chat_outTimeText : Theme.key_chat_inTimeText);
        int bg = Theme.getColor(m.outgoing ? Theme.key_chat_outBubble : Theme.key_chat_inBubble);
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(dp(14));
        bubble.setBackground(d);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) bubble.getLayoutParams();
        lp.gravity = m.outgoing ? Gravity.RIGHT : Gravity.LEFT;
        bubble.setLayoutParams(lp);

        if (senderLabel != null && !m.outgoing) {
            sender.setVisibility(VISIBLE);
            sender.setText(senderLabel);
            sender.setTextColor(senderColor);
        } else {
            sender.setVisibility(GONE);
        }

        if (quoteAuthorText != null || quoteBody != null) {
            quote.setVisibility(VISIBLE);
            int bar = Theme.getColor(m.outgoing ? Theme.key_chat_outReplyLine : Theme.key_chat_inReplyLine);
            GradientDrawable barBg = new GradientDrawable();
            barBg.setColor(XmppUi.dimColor(bar, 0.12f));
            barBg.setCornerRadius(dp(4));
            quote.setBackground(barBg);
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
        }

        StringBuilder f = new StringBuilder();
        if (progress >= 0 && progress < 1) {
            f.append(LocaleController.formatString(R.string.XmppUploading, (int) (progress * 100))).append("  ");
        }
        if (m.edited) {
            f.append(LocaleController.getString(R.string.XmppEdited)).append("  ");
        }
        f.append(XmppUi.messageTime(m.time));
        String mark = XmppUi.statusMark(m);
        if (!mark.isEmpty()) {
            f.append(' ').append(mark);
        }
        if (m.status == XmppMessage.STATUS_FAILED) {
            f.append("  ").append(LocaleController.getString(R.string.XmppFailedTapToRetry));
        }
        footer.setText(f);
        footer.setTextColor(m.status == XmppMessage.STATUS_FAILED ? Theme.getColor(Theme.key_text_RedRegular)
                : m.status == XmppMessage.STATUS_DISPLAYED ? Theme.getColor(Theme.key_chat_outSentCheckRead) : timeColor);
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
        int l = outgoing ? (right - left) - lp.rightMargin - bw : lp.leftMargin;
        bubble.layout(l, lp.topMargin, l + bw, lp.topMargin + bh);
    }
}
