package app.retrogram.xmpp.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import app.retrogram.xmpp.XmppAccounts;
import app.retrogram.xmpp.XmppManager;
import app.retrogram.xmpp.core.Callback;
import app.retrogram.xmpp.core.PinnedTrustManager;
import app.retrogram.xmpp.core.XmppAccountConfig;
import app.retrogram.xmpp.core.XmppAccountConfig.Security;
import app.retrogram.xmpp.core.XmppError;
import app.retrogram.xmpp.core.XmppSession;

/**
 * Add an account or edit one. A new or changed account is test-logged-in before it is saved.
 * <p>
 * {@link #forLogin()} is the same form laid out like the Telegram login screen; it is opened from the login screen's
 * ⋮ menu (Add account → ⋮ → XMPP) and, once signed in, makes the new account the app's current account.
 */
public class XmppAccountEditActivity extends BaseFragment {

    private static final int DONE = 1;

    private final long accountId;
    private XmppAccountConfig config;

    private EditTextBoldCursor jid;
    private EditTextBoldCursor password;
    private EditTextBoldCursor host;
    private EditTextBoldCursor port;
    private EditTextBoldCursor resource;
    private EditTextBoldCursor nickname;
    private EditTextBoldCursor label;
    private TextSettingsCell securityCell;
    private TextCheckCell registerCell;
    private LinearLayout advanced;
    private TextView advancedToggle;
    private Security security = Security.STARTTLS_REQUIRED;
    private AlertDialog progress;
    private boolean loginMode;

    public XmppAccountEditActivity(long accountId) {
        this.accountId = accountId;
    }

    /** "Sign in with XMPP", opened from the login screen. Afterwards the new account is shown like a Telegram account. */
    public static XmppAccountEditActivity forLogin() {
        XmppAccountEditActivity f = new XmppAccountEditActivity(0);
        f.loginMode = true;
        return f;
    }

    @Override
    public boolean isLightStatusBar() {
        if (!loginMode) {
            return super.isLightStatusBar();
        }
        return ColorUtils.calculateLuminance(Theme.getColor(Theme.key_windowBackgroundWhite)) > 0.7f;
    }

    @Override
    public boolean onFragmentCreate() {
        config = accountId == 0 ? new XmppAccountConfig() : XmppAccounts.get(accountId);
        if (config == null) {
            return false;
        }
        security = config.security;
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(accountId == 0 ? R.string.XmppAddAccount : R.string.XmppEditAccount));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == DONE) {
                    save();
                }
            }
        });
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(12), dp(20), dp(24));
        scroll.addView(form, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        if (loginMode) {
            // Telegram login look: no title bar colour, a centred header, the arrow button at the bottom right.
            actionBar.setTitle("");
            actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
            actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_listSelector), false);
            actionBar.setCastShadows(false);
            form.setPadding(dp(24), dp(8), dp(24), dp(96));
            addLoginHeader(context, form);
            FrameLayout root = new FrameLayout(context);
            root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            root.addView(createNextButton(context), LayoutHelper.createFrame(56, 56, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 24, 24));
            fragmentView = root;
        } else {
            ActionBarMenu menu = actionBar.createMenu();
            menu.addItemWithWidth(DONE, R.drawable.ic_ab_done, dp(56), LocaleController.getString(R.string.Done));
            fragmentView = scroll;
        }

        jid = XmppUi.editText(context, LocaleController.getString(R.string.XmppAddressHint), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        jid.setText(config.jid);
        jid.setEnabled(accountId == 0);
        form.addView(jid, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        password = XmppUi.editText(context, LocaleController.getString(R.string.XmppPasswordHint), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setText(config.password);
        form.addView(password, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        if (accountId == 0) {
            registerCell = new TextCheckCell(context);
            registerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            registerCell.setTextAndValueAndCheck(LocaleController.getString(R.string.XmppRegisterNew), LocaleController.getString(R.string.XmppRegisterNewInfo), false, true, false);
            registerCell.setOnClickListener(v -> registerCell.setChecked(!registerCell.isChecked()));
            form.addView(registerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, -20, 0, -20, 0));
        }

        advancedToggle = new TextView(context);
        advancedToggle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        advancedToggle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        advancedToggle.setPadding(0, dp(14), 0, dp(14));
        advancedToggle.setText(LocaleController.getString(R.string.XmppAdvanced));
        advancedToggle.setOnClickListener(v -> {
            boolean show = advanced.getVisibility() != View.VISIBLE;
            advanced.setVisibility(show ? View.VISIBLE : View.GONE);
            advancedToggle.setText(LocaleController.getString(show ? R.string.XmppAdvancedHide : R.string.XmppAdvanced));
        });
        form.addView(advancedToggle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        advanced = new LinearLayout(context);
        advanced.setOrientation(LinearLayout.VERTICAL);
        boolean hasAdvanced = !config.host.isEmpty() || config.port != 0 || !config.resource.isEmpty() || !config.nickname.isEmpty()
                || !config.label.isEmpty() || security != Security.STARTTLS_REQUIRED;
        advanced.setVisibility(hasAdvanced ? View.VISIBLE : View.GONE);
        if (hasAdvanced) {
            advancedToggle.setText(LocaleController.getString(R.string.XmppAdvancedHide));
        }
        form.addView(advanced, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        host = addField(context, R.string.XmppServerHint, config.host, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        port = addField(context, R.string.XmppPortHint, config.port == 0 ? "" : String.valueOf(config.port), InputType.TYPE_CLASS_NUMBER);
        securityCell = new TextSettingsCell(context);
        securityCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        securityCell.setOnClickListener(v -> chooseSecurity());
        advanced.addView(securityCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, -20, 0, -20, 0));
        updateSecurityCell();
        resource = addField(context, R.string.XmppResourceHint, config.resource, InputType.TYPE_CLASS_TEXT);
        nickname = addField(context, R.string.XmppNicknameHint, config.nickname, InputType.TYPE_CLASS_TEXT);
        label = addField(context, R.string.XmppLabelHint, config.label, InputType.TYPE_CLASS_TEXT);

        TextView hint = new TextView(context);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        hint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        hint.setText(LocaleController.getString(R.string.XmppAccountFormInfo));
        hint.setPadding(0, dp(12), 0, 0);
        form.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return fragmentView;
    }

    private void addLoginHeader(Context context, LinearLayout form) {
        FrameLayout iconFrame = new FrameLayout(context);
        iconFrame.setBackground(Theme.createCircleDrawable(dp(100), Theme.getColor(Theme.key_chats_actionBackground)));
        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.msg_discussion);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setColorFilter(Theme.getColor(Theme.key_chats_actionIcon));
        iconFrame.addView(icon, LayoutHelper.createFrame(52, 52, Gravity.CENTER));
        form.addView(iconFrame, LayoutHelper.createLinear(100, 100, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

        TextView title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setText(LocaleController.getString(R.string.XmppLoginTitle));
        form.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 20, 0, 0));

        TextView subtitle = new TextView(context);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLineSpacing(dp(2), 1f);
        subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
        subtitle.setText(LocaleController.getString(R.string.XmppLoginSubtitle));
        form.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 8, 8, 8, 24));
    }

    private View createNextButton(Context context) {
        ImageView button = new ImageView(context);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setImageResource(R.drawable.msg_arrow_forward);
        button.setColorFilter(Theme.getColor(Theme.key_chats_actionIcon));
        button.setBackground(Theme.createSimpleSelectorCircleDrawable(dp(56), Theme.getColor(Theme.key_chats_actionBackground),
                Theme.getColor(Theme.key_chats_actionPressedBackground)));
        button.setElevation(dp(2));
        button.setContentDescription(LocaleController.getString(R.string.Done));
        button.setOnClickListener(v -> save());
        return button;
    }

    private EditTextBoldCursor addField(Context context, int hintRes, String value, int type) {
        EditTextBoldCursor e = XmppUi.editText(context, LocaleController.getString(hintRes), type);
        e.setText(value);
        advanced.addView(e, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        return e;
    }

    private void updateSecurityCell() {
        String name;
        switch (security) {
            case DIRECT_TLS:
                name = LocaleController.getString(R.string.XmppSecurityDirectTls);
                break;
            case STARTTLS_OPTIONAL:
                name = LocaleController.getString(R.string.XmppSecurityOptional);
                break;
            default:
                name = LocaleController.getString(R.string.XmppSecurityStartTls);
                break;
        }
        securityCell.setTextAndValue(LocaleController.getString(R.string.XmppSecurity), name, false);
    }

    private void chooseSecurity() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        CharSequence[] items = {
                LocaleController.getString(R.string.XmppSecurityStartTls),
                LocaleController.getString(R.string.XmppSecurityDirectTls),
                LocaleController.getString(R.string.XmppSecurityOptional)
        };
        Security[] values = {Security.STARTTLS_REQUIRED, Security.DIRECT_TLS, Security.STARTTLS_OPTIONAL};
        AlertDialog.Builder b = new AlertDialog.Builder(context, getResourceProvider());
        b.setTitle(LocaleController.getString(R.string.XmppSecurity));
        b.setItems(items, (d, which) -> {
            security = values[which];
            updateSecurityCell();
        });
        showDialog(b.create());
    }

    private void save() {
        String address = XmppAccountConfig.normalizeJid(jid.getText().toString());
        if (address == null || app.retrogram.xmpp.core.Jids.entityBare(address) == null) {
            XmppUi.toast(this, LocaleController.getString(R.string.XmppErrorInvalid));
            jid.requestFocus();
            return;
        }
        String pw = password.getText().toString();
        if (pw.isEmpty()) {
            XmppUi.toast(this, LocaleController.getString(R.string.XmppPasswordRequired));
            password.requestFocus();
            return;
        }
        if (accountId == 0 && XmppAccounts.hasAccount(address)) {
            XmppUi.toast(this, LocaleController.getString(R.string.XmppAccountExists));
            return;
        }
        int portNumber = 0;
        String portText = port.getText().toString().trim();
        if (!portText.isEmpty()) {
            try {
                portNumber = Integer.parseInt(portText);
            } catch (NumberFormatException e) {
                portNumber = -1;
            }
            if (portNumber <= 0 || portNumber > 65535) {
                XmppUi.toast(this, LocaleController.getString(R.string.XmppErrorInvalid));
                advanced.setVisibility(View.VISIBLE);
                port.requestFocus();
                return;
            }
        }
        XmppAccountConfig c = config.copy();
        c.jid = address;
        c.password = pw;
        c.host = host.getText().toString().trim();
        c.port = portNumber;
        c.security = security;
        c.resource = resource.getText().toString().trim();
        c.nickname = nickname.getText().toString().trim();
        c.label = label.getText().toString().trim();
        c.enabled = true;
        // A different server or security mode invalidates a certificate the user trusted before.
        if (!c.host.equals(config.host) || c.security != config.security) {
            c.pinnedCertificate = "";
        }
        AndroidUtilities.hideKeyboard(fragmentView);
        showProgress();
        boolean register = registerCell != null && registerCell.isChecked();
        if (register) {
            XmppSession.register(c, null, new Callback<Void>() {
                @Override
                public void onResult(Void v) {
                    AndroidUtilities.runOnUIThread(() -> test(c));
                }

                @Override
                public void onError(XmppError error) {
                    AndroidUtilities.runOnUIThread(() -> failed(c, error, true));
                }
            });
        } else {
            test(c);
        }
    }

    private void test(XmppAccountConfig c) {
        XmppLoginTester.test(c, new XmppLoginTester.Result() {
            @Override
            public void onSuccess() {
                hideProgress();
                long savedId;
                if (accountId == 0) {
                    savedId = XmppManager.get().addAccount(c).accountId();
                } else {
                    savedId = XmppManager.get().updateAccount(c).accountId();
                }
                if (loginMode && getParentActivity() instanceof org.telegram.ui.LaunchActivity) {
                    // Like a Telegram login: the new account becomes the current one, the login screens go away.
                    ((org.telegram.ui.LaunchActivity) getParentActivity()).switchToXmppAccount(savedId);
                    return;
                }
                XmppHome.onAccountsChanged();
                finishFragment();
            }

            @Override
            public void onFailure(XmppError error) {
                failed(c, error, false);
            }
        });
    }

    private void failed(XmppAccountConfig c, XmppError error, boolean registering) {
        hideProgress();
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        if (error.kind == XmppError.Kind.TLS_CERTIFICATE && !error.certFingerprint.isEmpty()) {
            AlertDialog.Builder b = new AlertDialog.Builder(context, getResourceProvider());
            b.setTitle(LocaleController.getString(R.string.XmppUntrustedCertificate));
            b.setMessage(LocaleController.formatString(R.string.XmppUntrustedCertificateInfo, c.domain(), error.certSubject, PinnedTrustManager.format(error.certFingerprint)));
            b.setPositiveButton(LocaleController.getString(R.string.XmppTrust), (d, w) -> {
                c.pinnedCertificate = error.certFingerprint;
                config.pinnedCertificate = error.certFingerprint;
                showProgress();
                if (registering) {
                    XmppSession.register(c, null, new Callback<Void>() {
                        @Override
                        public void onResult(Void v) {
                            AndroidUtilities.runOnUIThread(() -> test(c));
                        }

                        @Override
                        public void onError(XmppError e) {
                            AndroidUtilities.runOnUIThread(() -> failed(c, e, true));
                        }
                    });
                } else {
                    test(c);
                }
            });
            b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            showDialog(b.create());
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(context, getResourceProvider());
        b.setTitle(LocaleController.getString(registering ? R.string.XmppRegistrationFailed : R.string.XmppLoginFailed));
        String text = XmppUi.errorText(error);
        if (!error.detail.isEmpty() && error.kind != XmppError.Kind.AUTH) {
            text += "\n\n" + error.detail;
        }
        b.setMessage(text);
        b.setPositiveButton(LocaleController.getString(R.string.OK), null);
        showDialog(b.create());
    }

    private void showProgress() {
        if (getParentActivity() == null) {
            return;
        }
        progress = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();
    }

    private void hideProgress() {
        if (progress != null) {
            try {
                progress.dismiss();
            } catch (Exception ignored) {
            }
            progress = null;
        }
    }

    @Override
    public void onFragmentDestroy() {
        hideProgress();
        super.onFragmentDestroy();
    }
}
