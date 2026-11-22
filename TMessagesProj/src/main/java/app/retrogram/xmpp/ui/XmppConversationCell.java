package app.retrogram.xmpp.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import app.retrogram.xmpp.core.XmppConversation;
import app.retrogram.xmpp.core.XmppShow;

/** One row of the XMPP chat list. */
class XmppConversationCell extends FrameLayout {
    private final XmppAvatarView avatar;
    private final TextView name;
    private final TextView preview;
    private final TextView time;
    private final TextView badge;
    private final Paint divider = new Paint();
    private boolean drawDivider;

    XmppConversationCell(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackground(Theme.getSelectorDrawable(false));
        avatar = new XmppAvatarView(context);
        addView(avatar, LayoutHelper.createFrame(54, 54, Gravity.LEFT | Gravity.CENTER_VERTICAL, 12, 0, 0, 0));

        name = new TextView(context);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        name.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setTextColor(Theme.getColor(Theme.key_chats_name));
        addView(name, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 78, 10, 72, 0));

        preview = new TextView(context);
        preview.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        preview.setSingleLine(true);
        preview.setEllipsize(TextUtils.TruncateAt.END);
        preview.setTextColor(Theme.getColor(Theme.key_chats_message));
        addView(preview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 78, 34, 56, 0));

        time = new TextView(context);
        time.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        time.setTextColor(Theme.getColor(Theme.key_chats_date));
        addView(time, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP, 0, 11, 12, 0));

        badge = new TextView(context);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        badge.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
        badge.setTextColor(Theme.getColor(Theme.key_chats_unreadCounterText));
        badge.setGravity(Gravity.CENTER);
        badge.setMinWidth(dp(22));
        badge.setPadding(dp(7), dp(1), dp(7), dp(2));
        badge.setBackground(Theme.createRoundRectDrawable(dp(11), Theme.getColor(Theme.key_chats_unreadCounter)));
        addView(badge, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP, 0, 34, 12, 0));
        divider.setColor(Theme.getColor(Theme.key_divider));
    }

    void set(XmppConversation c, String accountLabel, XmppShow presence, boolean divider) {
        avatar.set(c.accountId, c.jid, c.displayName(), !c.groupChat, c.groupChat ? null : presence);
        String title = c.displayName();
        if (c.muted) {
            title += " 🔇";
        }
        if (c.pinned) {
            title = "📌 " + title;
        }
        name.setText(title);
        String body = c.lastBody == null ? "" : c.lastBody;
        if (body.isEmpty() && c.groupChat && !TextUtils.isEmpty(c.subject)) {
            body = c.subject;
        }
        if (body.isEmpty()) {
            body = c.groupChat && !c.joined ? LocaleController.getString(R.string.XmppNotInRoom) : "";
        } else if (c.lastOutgoing) {
            body = LocaleController.getString(R.string.XmppYouPrefix) + " " + body;
        }
        if (accountLabel != null) {
            body = body.isEmpty() ? accountLabel : body + "  ·  " + accountLabel;
        }
        preview.setText(body);
        time.setText(XmppUi.listTime(c.lastTime));
        if (c.unread > 0) {
            badge.setVisibility(VISIBLE);
            badge.setText(c.unread > 999 ? "999+" : String.valueOf(c.unread));
            badge.getBackground().setTint(c.muted ? Theme.getColor(Theme.key_chats_unreadCounterMuted) : Theme.getColor(Theme.key_chats_unreadCounter));
        } else {
            badge.setVisibility(GONE);
        }
        drawDivider = divider;
        setWillNotDraw(false);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(72) + (drawDivider ? 1 : 0), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (drawDivider) {
            canvas.drawLine(dp(78), getHeight() - 1, getWidth(), getHeight() - 1, divider);
        }
    }
}
