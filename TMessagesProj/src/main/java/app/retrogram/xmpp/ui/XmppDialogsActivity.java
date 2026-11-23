package app.retrogram.xmpp.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.ui.ActionBar.MenuDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import app.retrogram.xmpp.XmppManager;
import app.retrogram.xmpp.XmppNotifications;
import app.retrogram.xmpp.core.XmppConnectionState;
import app.retrogram.xmpp.core.XmppContact;
import app.retrogram.xmpp.core.XmppController;
import app.retrogram.xmpp.core.XmppConversation;
import app.retrogram.xmpp.core.XmppError;
import app.retrogram.xmpp.core.XmppShow;
import tw.nekomimi.nekogram.NekoConfig;

/**
 * The main screen while an XMPP account is the app's current account: the Telegram chat list, for one XMPP account.
 * Hamburger that opens the drawer (account switcher), "Connecting..." in the title while offline, search, the
 * pencil button for a new message, Telegram-style rows. Opened by {@code LaunchActivity.switchToXmppAccount}.
 */
public class XmppDialogsActivity extends BaseFragment implements XmppController.Events {

    private static final int MENU_SEARCH = 1;

    private final long accountId;

    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private Adapter adapter;
    private LinearLayout emptyBox;
    private TextView emptyTitle;
    private TextView emptyText;
    private ImageView floatingButton;
    private FrameLayout floatingButtonContainer;
    private boolean floatingHidden;
    private MenuDrawable menuDrawable;
    private final List<Object> rows = new ArrayList<>();
    private String query = "";
    private boolean searching;

    /** Marker row: "N contact requests". */
    private static final class RequestsRow {
        final int count;

        RequestsRow(int count) {
            this.count = count;
        }
    }

    public XmppDialogsActivity(long accountId) {
        this.accountId = accountId;
    }

    public long getAccountId() {
        return accountId;
    }

    /** Looked up every time: editing the account replaces its controller. */
    private XmppController controller() {
        return XmppManager.get().controller(accountId);
    }

    @Override
    public boolean onFragmentCreate() {
        if (controller() == null) {
            return false;
        }
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
        Theme.createDialogsResources(context);
        searching = false;
        query = "";

        actionBar.setBackButtonDrawable(menuDrawable = new MenuDrawable());
        menuDrawable.setRoundCap();
        actionBar.setBackButtonContentDescription(LocaleController.getString(R.string.AccDescrOpenMenu));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    openDrawer();
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem search = menu.addItem(MENU_SEARCH, R.drawable.ic_ab_search).setIsSearchField(true)
                .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override
                    public void onSearchExpand() {
                        searching = true;
                        updateDrawerAvailability();
                        setFloatingHidden(true);
                    }

                    @Override
                    public void onSearchCollapse() {
                        searching = false;
                        query = "";
                        rebuild();
                        updateDrawerAvailability();
                        setFloatingHidden(false);
                    }

                    @Override
                    public void onTextChanged(EditText editText) {
                        query = editText.getText().toString().trim().toLowerCase(Locale.ROOT);
                        rebuild();
                    }
                });
        search.setSearchFieldHint(LocaleController.getString(R.string.Search));
        search.setContentDescription(LocaleController.getString(R.string.Search));

        FrameLayout frame = new FrameLayout(context);
        fragmentView = frame;
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        listView = new RecyclerListView(context);
        layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        listView.setLayoutManager(layoutManager);
        listView.setVerticalScrollBarEnabled(true);
        listView.setClipToPadding(false);
        listView.setPadding(0, 0, 0, dp(80));
        adapter = new Adapter(context);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((view, position) -> {
            Object row = position >= 0 && position < rows.size() ? rows.get(position) : null;
            if (row instanceof RequestsRow) {
                presentFragment(new XmppContactsActivity(accountId));
            } else if (row instanceof XmppConversation) {
                XmppConversation c = (XmppConversation) row;
                presentFragment(new XmppChatActivity(c.accountId, c.jid, c.groupChat));
            }
        });
        listView.setOnItemLongClickListener((view, position) -> {
            Object row = position >= 0 && position < rows.size() ? rows.get(position) : null;
            if (row instanceof XmppConversation) {
                showConversationMenu((XmppConversation) row);
                return true;
            }
            return false;
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                // Like the Telegram chat list: the pencil hides while scrolling down and comes back when scrolling up.
                if (searching || Math.abs(dy) < dp(2)) {
                    return;
                }
                setFloatingHidden(dy > 0 && layoutManager.findFirstVisibleItemPosition() > 0);
            }
        });
        frame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyBox = new LinearLayout(context);
        emptyBox.setOrientation(LinearLayout.VERTICAL);
        emptyBox.setGravity(Gravity.CENTER);
        emptyTitle = new TextView(context);
        emptyTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        emptyTitle.setTypeface(AndroidUtilities.bold());
        emptyTitle.setGravity(Gravity.CENTER);
        emptyTitle.setTextColor(Theme.getColor(Theme.key_chats_nameMessage_threeLines));
        emptyBox.addView(emptyTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 32, 0, 32, 0));
        emptyText = new TextView(context);
        emptyText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setLineSpacing(dp(2), 1f);
        emptyText.setTextColor(Theme.getColor(Theme.key_chats_message));
        emptyBox.addView(emptyText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 40, 8, 40, 0));
        frame.addView(emptyBox, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        floatingButtonContainer = new FrameLayout(context);
        floatingButton = new ImageView(context);
        floatingButton.setScaleType(ImageView.ScaleType.CENTER);
        floatingButton.setImageResource(R.drawable.floating_pencil);
        floatingButton.setColorFilter(Theme.getColor(Theme.key_chats_actionIcon));
        floatingButton.setBackground(Theme.createSimpleSelectorCircleDrawable(dp(56), Theme.getColor(Theme.key_chats_actionBackground),
                Theme.getColor(Theme.key_chats_actionPressedBackground)));
        floatingButton.setElevation(dp(2));
        floatingButton.setTranslationZ(dp(2));
        floatingButton.setContentDescription(LocaleController.getString(R.string.NewMessageTitle));
        floatingButton.setOnClickListener(v -> presentFragment(new XmppContactsActivity(accountId)));
        floatingButtonContainer.addView(floatingButton, LayoutHelper.createFrame(56, 56, Gravity.CENTER));
        frame.addView(floatingButtonContainer, LayoutHelper.createFrame(64, 64,
                (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, LocaleController.isRTL ? 10 : 0, 0, LocaleController.isRTL ? 0 : 10, 10));
        floatingHidden = false;

        rebuild();
        updateTitle();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        rebuild();
        updateTitle();
        updateDrawerAvailability();
    }

    @Override
    public void onPause() {
        super.onPause();
        DrawerLayoutContainer drawer = drawer();
        if (drawer != null) {
            drawer.setAllowOpenDrawer(false, false);
            drawer.setAllowOpenDrawerBySwipe(false);
        }
    }

    @Override
    public void onBecomeFullyVisible() {
        super.onBecomeFullyVisible();
        updateDrawerAvailability();
    }

    // ---------------------------------------------------------------------------------------------- drawer

    private DrawerLayoutContainer drawer() {
        return getParentLayout() != null ? getParentLayout().getDrawerLayoutContainer() : null;
    }

    private boolean drawerEnabled() {
        return NekoConfig.navigationDrawerEnabled.Bool();
    }

    private void updateDrawerAvailability() {
        DrawerLayoutContainer drawer = drawer();
        if (drawer == null) {
            return;
        }
        boolean allow = drawerEnabled() && !searching && (actionBar == null || !actionBar.isActionModeShowed());
        drawer.setAllowOpenDrawer(allow, false);
        drawer.setAllowOpenDrawerBySwipe(allow);
    }

    private void openDrawer() {
        DrawerLayoutContainer drawer = drawer();
        if (drawer != null && drawerEnabled()) {
            if (drawer.isDrawerOpened()) {
                drawer.closeDrawer(true);
            } else {
                drawer.openDrawer(true);
            }
            return;
        }
        // The drawer is turned off in Retrogram's settings: switch accounts from a list instead.
        showAccountSwitcher();
    }

    private void showAccountSwitcher() {
        Context context = getParentActivity();
        if (context == null || !(getParentActivity() instanceof LaunchActivity)) {
            return;
        }
        LaunchActivity launch = (LaunchActivity) getParentActivity();
        List<CharSequence> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            UserConfig uc = UserConfig.getInstance(a);
            TLRPC.User user = uc.isClientActivated() ? uc.getCurrentUser() : null;
            if (user == null) {
                continue;
            }
            final int account = a;
            items.add(UserObject.getUserName(user));
            actions.add(() -> launch.switchToAccount(account, true));
        }
        for (XmppController c : XmppManager.get().controllers()) {
            if (c.accountId() == accountId) {
                continue;
            }
            final long id = c.accountId();
            items.add(XmppHome.accountName(c.config()) + " (XMPP)");
            actions.add(() -> launch.switchToXmppAccount(id));
        }
        items.add(LocaleController.getString(R.string.XmppAddAccount));
        actions.add(() -> presentFragment(XmppAccountEditActivity.forLogin()));
        AlertDialog.Builder b = new AlertDialog.Builder(context, getResourceProvider());
        XmppController current = controller();
        b.setTitle(current != null ? XmppHome.accountName(current.config()) : LocaleController.getString(R.string.XmppAccounts));
        b.setItems(items.toArray(new CharSequence[0]), (d, which) -> actions.get(which).run());
        showDialog(b.create());
    }

    // ---------------------------------------------------------------------------------------------- list

    private void setFloatingHidden(boolean hidden) {
        if (floatingButtonContainer == null || floatingHidden == hidden) {
            return;
        }
        floatingHidden = hidden;
        ObjectAnimator a = ObjectAnimator.ofFloat(floatingButtonContainer, View.TRANSLATION_Y, hidden ? dp(100) : 0);
        a.setDuration(300);
        a.setInterpolator(new DecelerateInterpolator());
        a.start();
        floatingButtonContainer.setClickable(!hidden);
    }

    private void rebuild() {
        if (controller() == null) {
            return;
        }
        List<XmppConversation> all = new ArrayList<>();
        for (XmppConversation conv : controller().conversations()) {
            // A chat that was only opened and never written in is not worth a row; rooms stay while joined.
            boolean empty = conv.lastTime == 0 && conv.lastBody.isEmpty() && !(conv.groupChat && conv.joined);
            if (!empty && matches(conv)) {
                all.add(conv);
            }
        }
        Collections.sort(all, (a, b) -> {
            if (a.pinned != b.pinned) {
                return a.pinned ? -1 : 1;
            }
            return Long.compare(b.lastTime, a.lastTime);
        });
        rows.clear();
        int requests = controller().pendingSubscribers().size();
        if (requests > 0 && query.isEmpty()) {
            rows.add(new RequestsRow(requests));
        }
        rows.addAll(all);
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateEmpty();
    }

    private boolean matches(XmppConversation conv) {
        if (query.isEmpty()) {
            return true;
        }
        return conv.displayName().toLowerCase(Locale.ROOT).contains(query) || conv.jid.toLowerCase(Locale.ROOT).contains(query)
                || conv.lastBody.toLowerCase(Locale.ROOT).contains(query);
    }

    private void updateEmpty() {
        if (emptyBox == null) {
            return;
        }
        emptyBox.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        if (query.isEmpty()) {
            emptyTitle.setText(LocaleController.getString(R.string.XmppHomeEmptyTitle));
            emptyText.setText(LocaleController.getString(R.string.NoChatsHelp));
            emptyText.setVisibility(View.VISIBLE);
        } else {
            emptyTitle.setText(LocaleController.getString(R.string.XmppNothingFound));
            emptyText.setVisibility(View.GONE);
        }
    }

    /** Title like the Telegram chat list: the app name when connected, otherwise what the connection is doing. */
    private void updateTitle() {
        if (actionBar == null || controller() == null) {
            return;
        }
        XmppConnectionState state = controller().config().enabled ? controller().state() : XmppConnectionState.DISCONNECTED;
        String title;
        switch (state) {
            case ONLINE:
                title = LocaleController.getString(R.string.AppName);
                break;
            case CONNECTING:
                title = LocaleController.getString(R.string.Connecting);
                break;
            default:
                title = XmppUi.stateText(state, null);
                break;
        }
        actionBar.setTitle(title);
        actionBar.setTitleActionRunnable(state == XmppConnectionState.ONLINE || state == XmppConnectionState.CONNECTING ? null
                : () -> presentFragment(new XmppAccountsActivity()));
    }

    private void showConversationMenu(XmppConversation conv) {
        Context context = getParentActivity();
        if (controller() == null || context == null) {
            return;
        }
        XmppController c = controller();
        List<CharSequence> items = new ArrayList<>();
        List<Integer> icons = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        items.add(LocaleController.getString(conv.pinned ? R.string.XmppUnpin : R.string.XmppPin));
        icons.add(conv.pinned ? R.drawable.msg_unpin : R.drawable.msg_pin);
        actions.add(() -> c.setPinned(conv.jid, !conv.pinned));
        items.add(LocaleController.getString(conv.muted ? R.string.XmppUnmute : R.string.XmppMute));
        icons.add(conv.muted ? R.drawable.msg_unmute : R.drawable.msg_mute);
        actions.add(() -> c.setMuted(conv.jid, !conv.muted));
        if (conv.unread > 0) {
            items.add(LocaleController.getString(R.string.XmppMarkRead));
            icons.add(R.drawable.msg_markread);
            actions.add(() -> {
                c.markRead(conv.jid);
                XmppNotifications.cancel(context, conv.accountId, conv.jid);
            });
        }
        if (!conv.groupChat) {
            items.add(LocaleController.getString(R.string.XmppContactInfo));
            icons.add(R.drawable.msg_info);
            actions.add(() -> XmppDialogs.contactMenu(this, c, conv.jid, null));
            items.add(LocaleController.getString(R.string.XmppDeleteChat));
            icons.add(R.drawable.msg_delete);
            actions.add(() -> XmppDialogs.confirm(this, LocaleController.getString(R.string.XmppDeleteChatQuestion), R.string.Delete,
                    () -> c.deleteConversation(conv.jid)));
        } else {
            items.add(LocaleController.getString(R.string.XmppLeaveGroup));
            icons.add(R.drawable.msg_leave);
            actions.add(() -> XmppDialogs.confirm(this, LocaleController.getString(R.string.XmppLeaveGroupQuestion), R.string.XmppLeave,
                    () -> c.leaveRoom(conv.jid, true, XmppDialogs.reportErrors(this, null))));
        }
        int[] iconArray = new int[icons.size()];
        for (int i = 0; i < iconArray.length; i++) {
            iconArray[i] = icons.get(i);
        }
        AlertDialog.Builder b = new AlertDialog.Builder(context, getResourceProvider());
        b.setTitle(conv.displayName());
        b.setItems(items.toArray(new CharSequence[0]), iconArray, (d, which) -> actions.get(which).run());
        showDialog(b.create());
    }

    // ---------------------------------------------------------------------------------------------- events

    @Override
    public void onConversationsChanged(long account) {
        if (account == accountId) {
            rebuild();
        }
    }

    @Override
    public void onContactsChanged(long account) {
        if (account == accountId && adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onConnectionState(long account, XmppConnectionState state, XmppError error) {
        if (account != accountId) {
            return;
        }
        updateTitle();
        if (state == XmppConnectionState.FAILED && error != null && error.kind == XmppError.Kind.TLS_CERTIFICATE) {
            XmppAccountsActivity.offerToTrust(this, account, error);
        }
    }

    @Override
    public void onSubscribeRequest(long account, String jid) {
        if (account != accountId) {
            return;
        }
        rebuild();
        if (getParentActivity() == null || !isLastFragment() || controller() == null) {
            return;
        }
        XmppDialogs.confirm(this, LocaleController.formatString(R.string.XmppSubscribeQuestion, jid), R.string.XmppAccept,
                () -> controller().acceptSubscription(jid, true, XmppDialogs.reportErrors(this, this::rebuild)));
    }

    @Override
    public void onInvitation(long account, String roomJid, String from, String reason, String password) {
        if (account != accountId || getParentActivity() == null || controller() == null) {
            return;
        }
        XmppDialogs.confirm(this, LocaleController.formatString(R.string.XmppInvitationQuestion, from.isEmpty() ? "?" : from, roomJid), R.string.XmppJoin,
                () -> controller().joinRoom(roomJid, controller().config().effectiveNickname(), password, false, true, XmppDialogs.reportErrors(this,
                        () -> presentFragment(new XmppChatActivity(account, roomJid, true)))));
    }

    private final class Adapter extends RecyclerListView.SelectionAdapter {
        private static final int TYPE_CHAT = 0;
        private static final int TYPE_REQUESTS = 1;
        private final Context context;

        Adapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position) instanceof RequestsRow ? TYPE_REQUESTS : TYPE_CHAT;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v;
            if (viewType == TYPE_REQUESTS) {
                TextView t = new TextView(context);
                t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                t.setTypeface(Typeface.DEFAULT_BOLD);
                t.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                t.setPadding(dp(16), dp(14), dp(16), dp(14));
                t.setBackground(Theme.getSelectorDrawable(false));
                v = t;
            } else {
                v = new XmppConversationCell(context);
            }
            v.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(v);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Object row = rows.get(position);
            if (row instanceof RequestsRow) {
                ((TextView) holder.itemView).setText(LocaleController.formatString(R.string.XmppContactRequests, ((RequestsRow) row).count));
                return;
            }
            XmppConversation c = (XmppConversation) row;
            XmppShow show = XmppShow.OFFLINE;
            XmppController ctl = controller();
            if (!c.groupChat && ctl != null) {
                XmppContact ct = ctl.contact(c.jid);
                if (ct != null) {
                    show = ct.show;
                }
            }
            ((XmppConversationCell) holder.itemView).set(c, null, show, position < rows.size() - 1);
        }
    }
}
