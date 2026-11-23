package app.retrogram.xmpp.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.GroupCreateCheckBox;
import org.telegram.ui.Components.LayoutHelper;

import app.retrogram.xmpp.XmppAccounts;
import app.retrogram.xmpp.XmppAvatars;
import app.retrogram.xmpp.XmppManager;
import app.retrogram.xmpp.core.XmppAccountConfig;
import app.retrogram.xmpp.core.XmppConnectionState;
import app.retrogram.xmpp.core.XmppController;

/**
 * An XMPP account in the drawer's account list. Same size and layout as Telegram's {@code DrawerUserCell}: 36dp avatar,
 * bold name, a check on the current account, the unread counter on the right. A second, small line names the
 * protocol so the account is not mistaken for a Telegram one.
 */
public class DrawerXmppAccountCell extends FrameLayout implements XmppAvatars.Listener {

    private final XmppAvatarView avatar;
    private final TextView name;
    private final TextView address;
    private final LinearLayout texts;
    private final GroupCreateCheckBox checkBox;
    private final RectF rect = new RectF();
    private long accountId;
    private int unread;

    public DrawerXmppAccountCell(Context context) {
        super(context);
        avatar = new XmppAvatarView(context);
        addView(avatar, LayoutHelper.createFrame(36, 36, Gravity.LEFT | Gravity.TOP, 14, 6, 0, 0));

        texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        name = new TextView(context);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        name.setTypeface(AndroidUtilities.bold());
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(name, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        address = new TextView(context);
        address.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        address.setSingleLine(true);
        address.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(address, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 72, 0, 14, 0));

        checkBox = new GroupCreateCheckBox(context);
        checkBox.setChecked(true, false);
        checkBox.setCheckScale(0.9f);
        checkBox.setInnerRadDiff(dp(1.5f));
        checkBox.setColorKeysOverrides(Theme.key_chats_unreadCounterText, Theme.key_chats_unreadCounter, Theme.key_chats_menuBackground);
        addView(checkBox, LayoutHelper.createFrame(18, 18, Gravity.LEFT | Gravity.TOP, 37, 27, 0, 0));
        setWillNotDraw(false);
    }

    public long getAccountId() {
        return accountId;
    }

    public void setAccount(long id) {
        accountId = id;
        XmppController c = XmppManager.get().controller(id);
        if (c == null) {
            return;
        }
        XmppAccountConfig config = c.config();
        String title = XmppHome.accountName(config);
        name.setText(title);
        name.setTextColor(Theme.getColor(Theme.key_chats_menuItemText));
        String line = "XMPP · " + config.jid;
        if (!config.enabled) {
            line = "XMPP · " + XmppUi.stateText(XmppConnectionState.DISCONNECTED, null);
        } else if (c.state() != XmppConnectionState.ONLINE) {
            line = "XMPP · " + XmppUi.stateText(c.state(), null);
        }
        address.setText(line);
        address.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        avatar.set(id, config.jid, title, true, c.state() == XmppConnectionState.ONLINE ? XmppAccounts.show(id) : null);
        checkBox.setVisibility(id == XmppHome.activeAccount() ? VISIBLE : INVISIBLE);
        unread = c.totalUnread();
        texts.setPadding(0, 0, unread > 0 ? dp(40) : 0, 0);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(48), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        XmppAvatars.addListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        XmppAvatars.removeListener(this);
    }

    @Override
    public void onAvatarChanged(long account, String jid) {
        if (account == accountId) {
            setAccount(accountId);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (unread <= 0 || Theme.dialogs_countPaint == null || Theme.dialogs_countTextPaint == null) {
            return;
        }
        // Same badge as DrawerUserCell.
        String text = unread > 999 ? "999+" : String.valueOf(unread);
        int countTop = dp(12.5f);
        int textWidth = (int) Math.ceil(Theme.dialogs_countTextPaint.measureText(text));
        int countWidth = Math.max(dp(10), textWidth);
        int countLeft = getMeasuredWidth() - countWidth - dp(25);
        int x = countLeft - dp(5.5f);
        rect.set(x, countTop, x + countWidth + dp(14), countTop + dp(23));
        canvas.drawRoundRect(rect, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, Theme.dialogs_countPaint);
        canvas.drawText(text, rect.left + (rect.width() - textWidth) / 2, countTop + dp(16), Theme.dialogs_countTextPaint);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
    }
}
