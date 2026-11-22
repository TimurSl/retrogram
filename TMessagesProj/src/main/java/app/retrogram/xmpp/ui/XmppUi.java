package app.retrogram.xmpp.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.util.Calendar;
import java.util.Date;
import java.util.function.Consumer;

import app.retrogram.xmpp.core.XmppConnectionState;
import app.retrogram.xmpp.core.XmppError;
import app.retrogram.xmpp.core.XmppMessage;
import app.retrogram.xmpp.core.XmppShow;

/** Small helpers shared by the XMPP screens. */
final class XmppUi {
    private XmppUi() {
    }

    static String errorText(XmppError e) {
        if (e == null) {
            return "";
        }
        String base;
        switch (e.kind) {
            case AUTH:
                base = LocaleController.getString(R.string.XmppErrorAuth);
                break;
            case TLS_CERTIFICATE:
                base = LocaleController.getString(R.string.XmppErrorCertificate);
                break;
            case TLS_REQUIRED:
                base = LocaleController.getString(R.string.XmppErrorTlsRequired);
                break;
            case DNS:
                base = LocaleController.getString(R.string.XmppErrorDns);
                break;
            case TIMEOUT:
                base = LocaleController.getString(R.string.XmppErrorTimeout);
                break;
            case NETWORK:
                base = LocaleController.getString(R.string.XmppErrorNetwork);
                break;
            case UNSUPPORTED:
                base = LocaleController.getString(R.string.XmppErrorUnsupported);
                break;
            case NOT_CONNECTED:
                base = LocaleController.getString(R.string.XmppErrorNotConnected);
                break;
            case INVALID:
                base = LocaleController.getString(R.string.XmppErrorInvalid);
                break;
            case SERVER:
                base = serverCondition(e);
                break;
            default:
                base = LocaleController.getString(R.string.XmppErrorGeneric);
                break;
        }
        return base;
    }

    private static String serverCondition(XmppError e) {
        switch (e.condition) {
            case "conflict":
                return LocaleController.getString(R.string.XmppErrorConflict);
            case "forbidden":
            case "not-allowed":
            case "registration-required":
                return LocaleController.getString(R.string.XmppErrorForbidden);
            case "item-not-found":
            case "gone":
                return LocaleController.getString(R.string.XmppErrorNotFound);
            case "not-authorized":
                return LocaleController.getString(R.string.XmppErrorAuth);
            case "kicked":
                return LocaleController.getString(R.string.XmppErrorKicked);
            case "banned":
                return LocaleController.getString(R.string.XmppErrorBanned);
            default:
                return e.detail.isEmpty() ? LocaleController.getString(R.string.XmppErrorGeneric)
                        : LocaleController.formatString(R.string.XmppErrorServer, e.detail);
        }
    }

    static String stateText(XmppConnectionState state, XmppError error) {
        switch (state) {
            case ONLINE:
                return LocaleController.getString(R.string.XmppStateOnline);
            case CONNECTING:
                return error == null ? LocaleController.getString(R.string.XmppStateConnecting)
                        : LocaleController.formatString(R.string.XmppStateRetrying, errorText(error));
            case FAILED:
                return LocaleController.formatString(R.string.XmppStateFailed, errorText(error));
            default:
                return LocaleController.getString(R.string.XmppStateOffline);
        }
    }

    static String showName(XmppShow show) {
        switch (show) {
            case CHAT:
                return LocaleController.getString(R.string.XmppShowChat);
            case AWAY:
                return LocaleController.getString(R.string.XmppShowAway);
            case XA:
                return LocaleController.getString(R.string.XmppShowXa);
            case DND:
                return LocaleController.getString(R.string.XmppShowDnd);
            case OFFLINE:
                return LocaleController.getString(R.string.XmppShowOffline);
            default:
                return LocaleController.getString(R.string.XmppShowOnline);
        }
    }

    static int showColor(XmppShow show) {
        switch (show) {
            case CHAT:
            case ONLINE:
                return 0xFF4CAF50;
            case AWAY:
            case XA:
                return 0xFFFFA000;
            case DND:
                return 0xFFE53935;
            default:
                return 0xFF9E9E9E;
        }
    }

    static String messageTime(long ms) {
        Date d = new Date(ms);
        return LocaleController.getInstance().getFormatterDay().format(d);
    }

    /** Time today, weekday-less date otherwise: what the chat list shows. */
    static String listTime(long ms) {
        if (ms <= 0) {
            return "";
        }
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(ms);
        if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)) {
            return messageTime(ms);
        }
        return LocaleController.formatDateChat(ms / 1000, true);
    }

    static String statusMark(XmppMessage m) {
        if (!m.outgoing) {
            return "";
        }
        switch (m.status) {
            case XmppMessage.STATUS_PENDING:
                return "⏳";
            case XmppMessage.STATUS_SENT:
                return "✓";
            case XmppMessage.STATUS_DELIVERED:
                return "✓✓";
            case XmppMessage.STATUS_DISPLAYED:
                return "✓✓";
            case XmppMessage.STATUS_FAILED:
                return "⚠";
            default:
                return "";
        }
    }

    static void toast(BaseFragment f, String text) {
        if (f == null || f.getParentActivity() == null || TextUtils.isEmpty(text)) {
            return;
        }
        BulletinFactory.of(f).createSimpleBulletin(R.raw.info, text).show();
    }

    static void error(BaseFragment f, XmppError e) {
        if (f == null || f.getParentActivity() == null) {
            return;
        }
        BulletinFactory.of(f).createErrorBulletin(errorText(e)).show();
    }

    static EditTextBoldCursor editText(Context context, String hint, int inputType) {
        EditTextBoldCursor e = new EditTextBoldCursor(context);
        e.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        e.setHintText(hint);
        e.setInputType(inputType);
        e.setSingleLine(true);
        e.setBackground(null);
        e.setPadding(0, dp(8), 0, dp(8));
        e.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        e.setHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        e.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        e.setCursorSize(dp(20));
        e.setCursorWidth(1.5f);
        e.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField),
                Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated),
                Theme.getColor(Theme.key_text_RedRegular));
        return e;
    }

    /** A dialog with one text field. {@code onOk} gets the trimmed text (possibly empty). */
    static void input(BaseFragment f, String title, String hint, String initial, int positive, Consumer<String> onOk) {
        Context context = f.getParentActivity();
        if (context == null) {
            return;
        }
        FrameLayout frame = new FrameLayout(context);
        EditTextBoldCursor edit = editText(context, hint, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        edit.setSingleLine(false);
        edit.setMaxLines(6);
        edit.setText(initial == null ? "" : initial);
        edit.setSelection(edit.length());
        frame.addView(edit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 24, 8, 24, 0));
        AlertDialog.Builder b = new AlertDialog.Builder(context, f.getResourceProvider());
        b.setTitle(title);
        b.setView(frame);
        b.setPositiveButton(LocaleController.getString(positive), (d, w) -> onOk.accept(edit.getText().toString().trim()));
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = b.create();
        f.showDialog(dialog);
        edit.requestFocus();
        org.telegram.messenger.AndroidUtilities.showKeyboard(edit);
    }

    /**
     * A dialog with several one-line fields. {@code onOk} gets the trimmed values and returns true to close
     * the dialog (false keeps it open, e.g. after a validation error).
     */
    static void form(BaseFragment f, String title, String[] hints, String[] initial, int[] inputTypes, int positive,
                     java.util.function.Function<String[], Boolean> onOk) {
        Context context = f.getParentActivity();
        if (context == null) {
            return;
        }
        android.widget.LinearLayout box = new android.widget.LinearLayout(context);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        EditTextBoldCursor[] edits = new EditTextBoldCursor[hints.length];
        for (int i = 0; i < hints.length; i++) {
            edits[i] = editText(context, hints[i], inputTypes == null ? InputType.TYPE_CLASS_TEXT : inputTypes[i]);
            if (initial != null && initial[i] != null) {
                edits[i].setText(initial[i]);
            }
            box.addView(edits[i], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));
        }
        AlertDialog.Builder b = new AlertDialog.Builder(context, f.getResourceProvider());
        b.setTitle(title);
        b.setView(box);
        b.setPositiveButton(LocaleController.getString(positive), null);
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = b.create();
        f.showDialog(dialog);
        // Set the click handler after show() so a rejected input does not dismiss the dialog.
        android.view.View ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (ok != null) {
            ok.setOnClickListener(v -> {
                String[] values = new String[edits.length];
                for (int i = 0; i < edits.length; i++) {
                    values[i] = edits[i].getText().toString().trim();
                }
                if (Boolean.TRUE.equals(onOk.apply(values))) {
                    dialog.dismiss();
                }
            });
        }
        edits[0].requestFocus();
        org.telegram.messenger.AndroidUtilities.showKeyboard(edits[0]);
    }

    static int dimColor(int color, float alpha) {
        return Color.argb((int) (255 * alpha), Color.red(color), Color.green(color), Color.blue(color));
    }
}
