package app.retrogram.xmpp.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import app.retrogram.xmpp.core.XmppConversation;
import app.retrogram.xmpp.core.XmppShow;

/**
 * One row of the XMPP chat list, laid out like Telegram's dialog cell: 72dp high, round avatar with the online dot,
 * group icon before and mute icon after the name, time on the right, "You:" in the accent colour, the unread counter
 * (grey when muted) or the pin on the right of the preview, a divider from the text edge.
 */
class XmppConversationCell extends FrameLayout {
    private final XmppAvatarView avatar;
    private final TextView name;
    private final TextView preview;
    private final TextView time;
    private final TextView badge;
    private final ImageView pin;
    private final Drawable groupIcon;
    private final Drawable muteIcon;
    private boolean drawDivider;

    XmppConversationCell(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackground(Theme.getSelectorDrawable(false));
        boolean rtl = LocaleController.isRTL;
        avatar = new XmppAvatarView(context);
        addView(avatar, LayoutHelper.createFrame(56, 56, (rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, rtl ? 0 : 10, 0, rtl ? 10 : 0, 0));

        groupIcon = context.getResources().getDrawable(R.drawable.list_group).mutate();
        muteIcon = context.getResources().getDrawable(R.drawable.list_mute).mutate();

        name = new TextView(context);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        name.setTypeface(AndroidUtilities.bold());
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        name.setCompoundDrawablePadding(dp(4));
        addView(name, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP,
                rtl ? 72 : 76, 12, rtl ? 76 : 72, 0));

        preview = new TextView(context);
        preview.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        preview.setSingleLine(true);
        preview.setEllipsize(TextUtils.TruncateAt.END);
        preview.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        addView(preview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP,
                rtl ? 56 : 76, 38, rtl ? 76 : 56, 0));

        time = new TextView(context);
        time.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        addView(time, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (rtl ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP,
                rtl ? 14 : 0, 14, rtl ? 0 : 14, 0));

        badge = new TextView(context);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        badge.setTypeface(AndroidUtilities.bold());
        badge.setGravity(Gravity.CENTER);
        badge.setMinWidth(dp(23));
        badge.setPadding(dp(7), 0, dp(7), 0);
        addView(badge, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 23, (rtl ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP,
                rtl ? 12 : 0, 38, rtl ? 0 : 12, 0));

        pin = new ImageView(context);
        pin.setScaleType(ImageView.ScaleType.CENTER);
        pin.setImageResource(R.drawable.list_pin);
        addView(pin, LayoutHelper.createFrame(23, 23, (rtl ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, rtl ? 12 : 0, 38, rtl ? 0 : 12, 0));
    }

    void set(XmppConversation c, String accountLabel, XmppShow presence, boolean divider) {
        avatar.set(c.accountId, c.jid, c.displayName(), !c.groupChat, c.groupChat ? null : presence);

        name.setText(c.displayName());
        name.setTextColor(Theme.getColor(Theme.key_chats_name));
        groupIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_name), PorterDuff.Mode.MULTIPLY));
        muteIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_muteIcon), PorterDuff.Mode.MULTIPLY));
        Drawable start = c.groupChat ? groupIcon : null;
        Drawable end = c.muted ? muteIcon : null;
        if (LocaleController.isRTL) {
            name.setCompoundDrawablesWithIntrinsicBounds(end, null, start, null);
        } else {
            name.setCompoundDrawablesWithIntrinsicBounds(start, null, end, null);
        }

        String body = c.lastBody == null ? "" : c.lastBody.replace('\n', ' ');
        if (body.isEmpty() && c.groupChat && !TextUtils.isEmpty(c.subject)) {
            body = c.subject;
        }
        SpannableStringBuilder text = new SpannableStringBuilder();
        if (body.isEmpty()) {
            if (c.groupChat && !c.joined) {
                text.append(LocaleController.getString(R.string.XmppNotInRoom));
            }
        } else {
            if (c.lastOutgoing) {
                String you = LocaleController.getString(R.string.XmppYouPrefix) + " ";
                text.append(you);
                text.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_chats_nameMessage)), 0, you.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            text.append(body);
        }
        if (accountLabel != null) {
            text.append(text.length() == 0 ? "" : "  ·  ").append(accountLabel);
        }
        preview.setText(text);
        preview.setTextColor(Theme.getColor(Theme.key_chats_message));

        time.setText(XmppUi.listTime(c.lastTime));
        time.setTextColor(Theme.getColor(Theme.key_chats_date));

        if (c.unread > 0) {
            badge.setVisibility(VISIBLE);
            pin.setVisibility(GONE);
            badge.setText(c.unread > 999 ? "999+" : String.valueOf(c.unread));
            badge.setTextColor(Theme.getColor(Theme.key_chats_unreadCounterText));
            badge.setBackground(Theme.createRoundRectDrawable(dp(11.5f),
                    Theme.getColor(c.muted ? Theme.key_chats_unreadCounterMuted : Theme.key_chats_unreadCounter)));
        } else {
            badge.setVisibility(GONE);
            pin.setVisibility(c.pinned ? VISIBLE : GONE);
            pin.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_pinnedIcon), PorterDuff.Mode.MULTIPLY));
        }
        // Pinned chats get Telegram's pinned tint behind the row.
        setBackground(c.pinned ? Theme.createSelectorWithBackgroundDrawable(Theme.getColor(Theme.key_chats_pinnedOverlay), Theme.getColor(Theme.key_listSelector))
                : Theme.getSelectorDrawable(false));
        drawDivider = divider;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(72) + (drawDivider ? 1 : 0), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (drawDivider) {
            if (LocaleController.isRTL) {
                canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth() - dp(76), getMeasuredHeight() - 1, Theme.dividerPaint);
            } else {
                canvas.drawLine(dp(76), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        }
    }
}
