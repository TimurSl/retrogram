package app.retrogram.xmpp.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.retrogram.xmpp.XmppAccounts;
import app.retrogram.xmpp.XmppAvatars;
import app.retrogram.xmpp.XmppManager;
import app.retrogram.xmpp.core.Callback;
import app.retrogram.xmpp.core.PinnedTrustManager;
import app.retrogram.xmpp.core.XmppAccountConfig;
import app.retrogram.xmpp.core.XmppConnectionState;
import app.retrogram.xmpp.core.XmppController;
import app.retrogram.xmpp.core.XmppError;
import app.retrogram.xmpp.core.XmppSession;

/** Settings → XMPP accounts: add, edit, connect, remove. */
public class XmppAccountsActivity extends BaseFragment implements XmppController.Events {

    private static final int REQUEST_AVATAR = 4722;
    private static final Set<String> trustAsked = new HashSet<>();

    private RecyclerListView listView;
    private Adapter adapter;
    private List<XmppController> controllers = new ArrayList<>();
    private long avatarAccount;

    // row layout: header, accounts..., add, shadow, background switch, info
    private int rowCount;
    private int headerRow;
    private int accountsStart;
    private int addRow;
    private int shadowRow;
    private int backgroundRow;
    private int infoRow;

    @Override
    public boolean onFragmentCreate() {
        XmppManager.get().addListener(this);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        XmppManager.get().removeListener(this);
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.XmppAccounts));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        FrameLayout frame = new FrameLayout(context);
        fragmentView = frame;
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        adapter = new Adapter(context);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position >= accountsStart && position < accountsStart + controllers.size()) {
                showAccountMenu(controllers.get(position - accountsStart));
            } else if (position == addRow) {
                presentFragment(new XmppAccountEditActivity(0));
            } else if (position == backgroundRow) {
                boolean on = !XmppAccounts.backgroundService();
                XmppAccounts.setBackgroundService(on);
                ((TextCheckCell) view).setChecked(on);
                XmppManager.get().updateService();
            }
        });
        frame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        updateRows();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateRows();
    }

    private void updateRows() {
        controllers = XmppManager.get().controllers();
        rowCount = 0;
        headerRow = rowCount++;
        accountsStart = rowCount;
        rowCount += controllers.size();
        addRow = rowCount++;
        shadowRow = rowCount++;
        backgroundRow = rowCount++;
        infoRow = rowCount++;
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    // ---------------------------------------------------------------------------------------------- account menu

    private void showAccountMenu(XmppController c) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        XmppAccountConfig cfg = c.config();
        List<CharSequence> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        items.add(LocaleController.getString(R.string.XmppOpenChats));
        actions.add(() -> presentFragment(new XmppChatsActivity()));
        items.add(LocaleController.getString(R.string.XmppEditAccount));
        actions.add(() -> presentFragment(new XmppAccountEditActivity(cfg.id)));
        if (cfg.enabled) {
            items.add(LocaleController.getString(R.string.XmppSetStatus));
            actions.add(() -> XmppDialogs.setStatus(this, c));
            items.add(LocaleController.getString(R.string.XmppDisconnect));
            actions.add(() -> XmppManager.get().setEnabled(cfg.id, false));
            if (c.state() != XmppConnectionState.ONLINE) {
                items.add(LocaleController.getString(R.string.XmppReconnect));
                actions.add(c::reconnectNow);
            }
            if (c.state() == XmppConnectionState.ONLINE) {
                items.add(LocaleController.getString(R.string.XmppProfile));
                actions.add(() -> editProfile(c));
                items.add(LocaleController.getString(R.string.XmppChangeAvatar));
                actions.add(() -> pickAvatar(c));
                items.add(LocaleController.getString(R.string.XmppChangePassword));
                actions.add(() -> changePassword(c));
                items.add(LocaleController.getString(R.string.XmppServerInfo));
                actions.add(() -> showServerInfo(c));
            }
        } else {
            items.add(LocaleController.getString(R.string.XmppConnect));
            actions.add(() -> XmppManager.get().setEnabled(cfg.id, true));
        }
        items.add(LocaleController.getString(R.string.XmppRemoveAccount));
        actions.add(() -> removeAccount(c));
        AlertDialog.Builder b = new AlertDialog.Builder(context, getResourceProvider());
        b.setTitle(cfg.displayName());
        b.setItems(items.toArray(new CharSequence[0]), (d, which) -> actions.get(which).run());
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void removeAccount(XmppController c) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        XmppAccountConfig cfg = c.config();
        List<CharSequence> items = new ArrayList<>();
        items.add(LocaleController.getString(R.string.XmppRemoveFromDevice));
        if (c.state() == XmppConnectionState.ONLINE) {
            items.add(LocaleController.getString(R.string.XmppDeleteOnServer));
        }
        AlertDialog.Builder b = new AlertDialog.Builder(context, getResourceProvider());
        b.setTitle(LocaleController.formatString(R.string.XmppRemoveAccountQuestion, cfg.jid));
        b.setItems(items.toArray(new CharSequence[0]), (d, which) -> {
            if (which == 0) {
                XmppManager.get().removeAccount(cfg.id, true);
                updateRows();
                XmppHome.onAccountRemoved(cfg.id);
            } else {
                XmppDialogs.confirm(this, LocaleController.formatString(R.string.XmppDeleteOnServerQuestion, cfg.jid), R.string.Delete, () ->
                        c.session().deleteAccountOnServer(new Callback<Void>() {
                            @Override
                            public void onResult(Void v) {
                                AndroidUtilities.runOnUIThread(() -> {
                                    XmppManager.get().removeAccount(cfg.id, true);
                                    updateRows();
                                    XmppHome.onAccountRemoved(cfg.id);
                                });
                            }

                            @Override
                            public void onError(XmppError error) {
                                AndroidUtilities.runOnUIThread(() -> XmppUi.error(XmppAccountsActivity.this, error));
                            }
                        }));
            }
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void editProfile(XmppController c) {
        c.session().loadProfile("", new Callback<XmppSession.Profile>() {
            @Override
            public void onResult(XmppSession.Profile p) {
                AndroidUtilities.runOnUIThread(() -> XmppUi.form(XmppAccountsActivity.this, LocaleController.getString(R.string.XmppProfile),
                        new String[]{LocaleController.getString(R.string.XmppFullNameHint), LocaleController.getString(R.string.XmppNicknameHint)},
                        new String[]{p.fullName, p.nickname},
                        new int[]{InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, InputType.TYPE_CLASS_TEXT},
                        R.string.Save, values -> {
                            c.session().saveProfile(values[0], values[1], null, null, XmppDialogs.reportErrors(XmppAccountsActivity.this, null));
                            return true;
                        }));
            }

            @Override
            public void onError(XmppError error) {
                AndroidUtilities.runOnUIThread(() -> XmppUi.error(XmppAccountsActivity.this, error));
            }
        });
    }

    private void pickAvatar(XmppController c) {
        avatarAccount = c.accountId();
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(i, REQUEST_AVATAR);
        } catch (Exception e) {
            XmppUi.toast(this, LocaleController.getString(R.string.XmppNoAppToOpen));
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_AVATAR || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        XmppController c = XmppManager.get().controller(avatarAccount);
        Context context = getParentActivity();
        if (c == null || context == null) {
            return;
        }
        Uri uri = data.getData();
        Utilities.globalQueue.postRunnable(() -> {
            byte[] jpeg = null;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                Bitmap src = android.graphics.BitmapFactory.decodeStream(in);
                if (src != null) {
                    int side = Math.min(src.getWidth(), src.getHeight());
                    Bitmap square = Bitmap.createBitmap(src, (src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
                    Bitmap scaled = Bitmap.createScaledBitmap(square, Math.min(256, side), Math.min(256, side), true);
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    scaled.compress(Bitmap.CompressFormat.JPEG, 85, out);
                    jpeg = out.toByteArray();
                }
            } catch (Exception e) {
                org.telegram.messenger.FileLog.e(e);
            }
            final byte[] bytes = jpeg;
            AndroidUtilities.runOnUIThread(() -> {
                if (bytes == null) {
                    XmppUi.toast(this, LocaleController.getString(R.string.XmppCannotReadFile));
                    return;
                }
                c.session().saveProfile(null, null, bytes, "image/jpeg", XmppDialogs.reportErrors(this, () -> {
                    XmppAvatars.invalidate(c.accountId(), c.config().jid);
                    XmppUi.toast(this, LocaleController.getString(R.string.XmppAvatarUpdated));
                }));
            });
        });
    }

    private void changePassword(XmppController c) {
        XmppUi.form(this, LocaleController.getString(R.string.XmppChangePassword),
                new String[]{LocaleController.getString(R.string.XmppNewPasswordHint), LocaleController.getString(R.string.XmppRepeatPasswordHint)},
                null,
                new int[]{InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD},
                R.string.Save, values -> {
                    if (values[0].isEmpty() || !values[0].equals(values[1])) {
                        XmppUi.toast(this, LocaleController.getString(R.string.XmppPasswordsDiffer));
                        return false;
                    }
                    c.session().changePassword(values[0], new Callback<Void>() {
                        @Override
                        public void onResult(Void v) {
                            AndroidUtilities.runOnUIThread(() -> {
                                XmppAccountConfig cfg = XmppAccounts.get(c.accountId());
                                if (cfg != null) {
                                    cfg.password = values[0];
                                    XmppAccounts.save(cfg);
                                }
                                XmppUi.toast(XmppAccountsActivity.this, LocaleController.getString(R.string.XmppPasswordChanged));
                            });
                        }

                        @Override
                        public void onError(XmppError error) {
                            AndroidUtilities.runOnUIThread(() -> XmppUi.error(XmppAccountsActivity.this, error));
                        }
                    });
                    return true;
                });
    }

    private void showServerInfo(XmppController c) {
        Utilities.globalQueue.postRunnable(() -> {
            XmppSession s = c.session();
            List<String> features = s.serverFeatures();
            boolean upload = s.isUploadAvailable();
            boolean archive = s.isArchiveAvailable();
            boolean tls = s.isEncryptedTransport();
            AndroidUtilities.runOnUIThread(() -> {
                Context context = getParentActivity();
                if (context == null) {
                    return;
                }
                String yes = LocaleController.getString(R.string.XmppYes);
                String no = LocaleController.getString(R.string.XmppNo);
                StringBuilder sb = new StringBuilder();
                sb.append(LocaleController.getString(R.string.XmppInfoEncryption)).append(": ").append(tls ? yes : no).append('\n');
                sb.append(LocaleController.getString(R.string.XmppInfoArchive)).append(": ").append(archive ? yes : no).append('\n');
                sb.append(LocaleController.getString(R.string.XmppInfoUpload)).append(": ").append(upload ? yes : no).append('\n');
                sb.append(LocaleController.getString(R.string.XmppInfoCarbons)).append(": ").append(features.contains("urn:xmpp:carbons:2") ? yes : no).append('\n');
                sb.append(LocaleController.getString(R.string.XmppInfoStreamMgmt)).append(": ").append(features.contains("urn:xmpp:sm:3") ? yes : no).append('\n');
                sb.append(LocaleController.getString(R.string.XmppInfoBlocking)).append(": ").append(features.contains("urn:xmpp:blocking") ? yes : no);
                AlertDialog.Builder b = new AlertDialog.Builder(context, getResourceProvider());
                b.setTitle(c.config().domain());
                b.setMessage(sb.toString());
                b.setPositiveButton(LocaleController.getString(R.string.OK), null);
                showDialog(b.create());
            });
        });
    }

    /** Asks whether to trust a certificate the platform refused. Shown at most once per certificate. */
    static void offerToTrust(BaseFragment f, long accountId, XmppError error) {
        if (error.certFingerprint.isEmpty() || f.getParentActivity() == null) {
            return;
        }
        String key = accountId + "/" + error.certFingerprint;
        if (!trustAsked.add(key)) {
            return;
        }
        XmppAccountConfig cfg = XmppAccounts.get(accountId);
        String name = cfg == null ? "" : cfg.domain();
        AlertDialog.Builder b = new AlertDialog.Builder(f.getParentActivity(), f.getResourceProvider());
        b.setTitle(LocaleController.getString(R.string.XmppUntrustedCertificate));
        b.setMessage(LocaleController.formatString(R.string.XmppUntrustedCertificateInfo, name, error.certSubject, PinnedTrustManager.format(error.certFingerprint)));
        b.setPositiveButton(LocaleController.getString(R.string.XmppTrust), (d, w) -> XmppManager.get().trustCertificate(accountId, error.certFingerprint));
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        f.showDialog(b.create());
    }

    // ---------------------------------------------------------------------------------------------- events

    @Override
    public void onConnectionState(long accountId, XmppConnectionState state, XmppError error) {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        if (state == XmppConnectionState.FAILED && error != null && error.kind == XmppError.Kind.TLS_CERTIFICATE) {
            offerToTrust(this, accountId, error);
        }
    }

    private final class Adapter extends RecyclerListView.SelectionAdapter {
        private static final int HEADER = 0;
        private static final int ACCOUNT = 1;
        private static final int TEXT = 2;
        private static final int SHADOW = 3;
        private static final int CHECK = 4;
        private static final int INFO = 5;
        private final Context context;

        Adapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int t = holder.getItemViewType();
            return t == ACCOUNT || t == TEXT || t == CHECK;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow) {
                return HEADER;
            }
            if (position >= accountsStart && position < accountsStart + controllers.size()) {
                return ACCOUNT;
            }
            if (position == addRow) {
                return TEXT;
            }
            if (position == shadowRow) {
                return SHADOW;
            }
            if (position == backgroundRow) {
                return CHECK;
            }
            return INFO;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v;
            switch (viewType) {
                case HEADER:
                    v = new HeaderCell(context);
                    v.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case ACCOUNT:
                    v = new TextDetailSettingsCell(context);
                    v.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TEXT:
                    v = new TextSettingsCell(context);
                    v.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case SHADOW:
                    v = new ShadowSectionCell(context);
                    break;
                case CHECK:
                    v = new TextCheckCell(context);
                    v.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                default:
                    v = new TextInfoPrivacyCell(context);
                    break;
            }
            v.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(v);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case HEADER:
                    ((HeaderCell) holder.itemView).setText(LocaleController.getString(R.string.XmppAccounts));
                    break;
                case ACCOUNT: {
                    XmppController c = controllers.get(position - accountsStart);
                    String state = c.config().enabled ? XmppUi.stateText(c.state(), c.session().lastError()) : LocaleController.getString(R.string.XmppDisabled);
                    ((TextDetailSettingsCell) holder.itemView).setTextAndValue(c.config().displayName(), state, true);
                    break;
                }
                case TEXT:
                    ((TextSettingsCell) holder.itemView).setText(LocaleController.getString(R.string.XmppAddAccount), false);
                    ((TextSettingsCell) holder.itemView).setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                    break;
                case CHECK:
                    ((TextCheckCell) holder.itemView).setTextAndCheck(LocaleController.getString(R.string.XmppBackgroundService), XmppAccounts.backgroundService(), false);
                    break;
                case INFO:
                    ((TextInfoPrivacyCell) holder.itemView).setText(LocaleController.getString(R.string.XmppBackgroundServiceInfo));
                    break;
                default:
                    break;
            }
        }
    }
}
