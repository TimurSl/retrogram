package app.retrogram.xmpp.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import app.retrogram.xmpp.core.XmppShow;

/**
 * The chat screen's title area, laid out like Telegram's {@code ChatAvatarContainer}: a 42dp round avatar, the name,
 * and the status line under it ("online", "typing...", last status) in the action bar colours.
 */
class XmppChatHeader extends FrameLayout {
    private final XmppAvatarView avatar;
    private final TextView title;
    private final TextView subtitle;

    XmppChatHeader(Context context) {
        super(context);
        setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarDefaultSelector), Theme.RIPPLE_MASK_ROUNDRECT_6DP));
        avatar = new XmppAvatarView(context);
        addView(avatar, LayoutHelper.createFrame(42, 42, Gravity.LEFT | Gravity.CENTER_VERTICAL, 4, 0, 0, 0));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setTypeface(AndroidUtilities.bold());
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setTextColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
        texts.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        subtitle = new TextView(context);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        subtitle.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubtitle));
        texts.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));
        addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 58, 0, 8, 0));
    }

    void set(long accountId, String jid, String name, boolean contact, XmppShow presence) {
        // No presence dot here: the status line says it, as in Telegram.
        avatar.set(accountId, jid, name, contact, null);
        title.setText(name);
    }

    /** @param online true while the contact is online or typing: Telegram shows that line in the accent colour. */
    void setSubtitle(CharSequence text, boolean online) {
        subtitle.setText(text);
        subtitle.setTextColor(Theme.getColor(online ? Theme.key_chat_status : Theme.key_actionBarDefaultSubtitle));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // The action bar gives extra children its full height, status bar included: keep the content below it.
        int extra = Math.max(0, MeasureSpec.getSize(heightMeasureSpec) - org.telegram.ui.ActionBar.ActionBar.getCurrentActionBarHeight());
        if (getPaddingTop() != extra) {
            setPadding(0, extra, 0, 0);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
