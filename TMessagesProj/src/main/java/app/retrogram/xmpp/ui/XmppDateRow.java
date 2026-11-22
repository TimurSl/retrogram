package app.retrogram.xmpp.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/** The day label between messages. */
class XmppDateRow extends FrameLayout {
    private final TextView label;

    XmppDateRow(Context context) {
        super(context);
        label = new TextView(context);
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        label.setPadding(dp(12), dp(4), dp(12), dp(4));
        addView(label, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 8));
    }

    void set(String text) {
        label.setText(text);
        label.setTextColor(Theme.getColor(Theme.key_chat_serviceText));
        GradientDrawable d = new GradientDrawable();
        d.setColor(Theme.getColor(Theme.key_chat_serviceBackground));
        d.setCornerRadius(dp(12));
        label.setBackground(d);
    }
}
