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

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import app.retrogram.xmpp.core.XmppShow;

/** A contact, an occupant of a room or a pending request: avatar, name and one line of status. */
class XmppContactCell extends FrameLayout {
    private final XmppAvatarView avatar;
    private final TextView name;
    private final TextView status;
    private final TextView action;
    private final Paint divider = new Paint();
    private boolean drawDivider;

    XmppContactCell(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackground(Theme.getSelectorDrawable(false));
        avatar = new XmppAvatarView(context);
        addView(avatar, LayoutHelper.createFrame(42, 42, Gravity.LEFT | Gravity.CENTER_VERTICAL, 14, 0, 0, 0));

        name = new TextView(context);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        addView(name, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 72, 8, 14, 0));

        status = new TextView(context);
        status.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        status.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        addView(status, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 72, 31, 14, 0));

        action = new TextView(context);
        action.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        action.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
        action.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        action.setPadding(dp(12), dp(8), dp(12), dp(8));
        action.setVisibility(GONE);
        addView(action, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 6, 0));
        divider.setColor(Theme.getColor(Theme.key_divider));
    }

    void set(long accountId, String jid, String displayName, String statusLine, XmppShow show, boolean photo, boolean divider) {
        avatar.set(accountId, jid, displayName, photo, show);
        name.setText(displayName);
        status.setText(statusLine);
        drawDivider = divider;
        invalidate();
    }

    /** A trailing text button (accept / decline of a request). Pass null to hide it. */
    void setAction(String text, OnClickListener l) {
        if (text == null) {
            action.setVisibility(GONE);
        } else {
            action.setText(text);
            action.setOnClickListener(l);
            action.setVisibility(VISIBLE);
        }
        name.setPadding(0, 0, action.getVisibility() == VISIBLE ? dp(96) : 0, 0);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(60) + (drawDivider ? 1 : 0), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (drawDivider) {
            canvas.drawLine(dp(72), getHeight() - 1, getWidth(), getHeight() - 1, divider);
        }
    }
}
