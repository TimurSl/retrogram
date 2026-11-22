package app.retrogram.xmpp.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

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

/** The XMPP chat list: conversations of every account, newest first. This is the entry point of the XMPP client. */
public class XmppChatsActivity extends BaseFragment implements XmppController.Events {

    private static final int MENU_SEARCH = 1;
    private static final int MENU_NEW = 2;
    private static final int MENU_ADD_CONTACT = 3;
    private static final int MENU_NEW_CHAT = 4;
    private static final int MENU_JOIN = 5;
    private static final int MENU_CREATE = 6;
    private static final int MENU_BROWSE = 7;
    private static final int MENU_CONTACTS = 8;
    private static final int MENU_STATUS = 9;
    private static final int MENU_ACCOUNTS = 10;

    private RecyclerListView listView;
    private Adapter adapter;
    private TextView emptyView;
    private TextView emptyAction;
    private LinearLayout emptyBox;
    private final List<Object> rows = new ArrayList<>();
    private String query = "";

    /** Marker row: "N contact requests". */
    private static final class RequestsRow {
        final int count;

        RequestsRow(int count) {
            this.count = count;
        }
    }

    @Override
    public boolean onFragmentCreate() {
        XmppManager.get().start();
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
        actionBar.setTitle(LocaleController.getString(R.string.XmppChats));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                onMenu(id);
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem search = menu.addItem(MENU_SEARCH, R.drawable.ic_ab_search).setIsSearchField(true)
                .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override
                    public void onSearchCollapse() {
                        query = "";
                        rebuild();
                    }

                    @Override
                    public void onTextChanged(EditText editText) {
                        query = editText.getText().toString().trim().toLowerCase(Locale.ROOT);
                        rebuild();
                    }
                });
        search.setSearchFieldHint(LocaleController.getString(R.string.Search));
        ActionBarMenuItem add = menu.addItem(MENU_NEW, R.drawable.msg_add);
        add.addSubItem(MENU_NEW_CHAT, R.drawable.msg_discussion, LocaleController.getString(R.string.XmppNewChat));
        add.addSubItem(MENU_ADD_CONTACT, R.drawable.msg_addcontact, LocaleController.getString(R.string.XmppAddContact));
        add.addSubItem(MENU_JOIN, R.drawable.msg_groups, LocaleController.getString(R.string.XmppJoinGroup));
        add.addSubItem(MENU_CREATE, R.drawable.msg_groups, LocaleController.getString(R.string.XmppCreateGroup));
        add.addSubItem(MENU_BROWSE, R.drawable.msg_search, LocaleController.getString(R.string.XmppBrowseGroups));
        ActionBarMenuItem other = menu.addItem(0, R.drawable.ic_ab_other);
        other.addSubItem(MENU_CONTACTS, R.drawable.msg_contacts, LocaleController.getString(R.string.XmppContacts));
        other.addSubItem(MENU_STATUS, R.drawable.msg_online, LocaleController.getString(R.string.XmppSetStatus));
        other.addSubItem(MENU_ACCOUNTS, R.drawable.msg_settings, LocaleController.getString(R.string.XmppAccounts));

        FrameLayout frame = new FrameLayout(context);
        fragmentView = frame;
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        adapter = new Adapter(context);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((view, position) -> {
            Object row = position >= 0 && position < rows.size() ? rows.get(position) : null;
            if (row instanceof RequestsRow) {
                presentFragment(new XmppContactsActivity());
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
        frame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyBox = new LinearLayout(context);
        emptyBox.setOrientation(LinearLayout.VERTICAL);
        emptyBox.setGravity(Gravity.CENTER);
        emptyView = new TextView(context);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyBox.addView(emptyView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 32, 0, 32, 0));
        emptyAction = new TextView(context);
        emptyAction.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        emptyAction.setTypeface(AndroidUtilities.bold());
        emptyAction.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        emptyAction.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        emptyAction.setOnClickListener(v -> presentFragment(new XmppAccountsActivity()));
        emptyBox.addView(emptyAction, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        frame.addView(emptyBox, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        rebuild();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        rebuild();
    }

    private void onMenu(int id) {
        switch (id) {
            case -1:
                finishFragment();
                break;
            case MENU_NEW_CHAT:
                XmppDialogs.pickAccount(this, -1, c -> XmppDialogs.newChat(this, c, jid -> {
                    c.openChat(jid);
                    presentFragment(new XmppChatActivity(c.accountId(), jid, false));
                }));
                break;
            case MENU_ADD_CONTACT:
                XmppDialogs.pickAccount(this, -1, c -> XmppDialogs.addContact(this, c, ""));
                break;
            case MENU_JOIN:
                XmppDialogs.pickAccount(this, -1, c -> XmppDialogs.joinRoom(this, c, "", false,
                        room -> presentFragment(new XmppChatActivity(c.accountId(), room, true))));
                break;
            case MENU_CREATE:
                XmppDialogs.pickAccount(this, -1, c -> XmppDialogs.joinRoom(this, c, "", true,
                        room -> presentFragment(new XmppChatActivity(c.accountId(), room, true))));
                break;
            case MENU_BROWSE:
                XmppDialogs.pickAccount(this, -1, c -> presentFragment(new XmppRoomsActivity(c.accountId())));
                break;
            case MENU_CONTACTS:
                presentFragment(new XmppContactsActivity());
                break;
            case MENU_STATUS:
                XmppDialogs.pickAccount(this, -1, c -> XmppDialogs.setStatus(this, c));
                break;
            case MENU_ACCOUNTS:
                presentFragment(new XmppAccountsActivity());
                break;
            default:
                break;
        }
    }

    private boolean multipleAccounts() {
        return XmppManager.get().controllers().size() > 1;
    }

    private void rebuild() {
        List<XmppConversation> all = new ArrayList<>();
        int requests = 0;
        for (XmppController c : XmppManager.get().controllers()) {
            for (XmppConversation conv : c.conversations()) {
                // A chat that was only opened and never written in is not worth a row; rooms stay while joined.
                boolean empty = conv.lastTime == 0 && conv.lastBody.isEmpty() && !(conv.groupChat && conv.joined);
                if (!empty && matches(c, conv)) {
                    all.add(conv);
                }
            }
            requests += c.pendingSubscribers().size();
        }
        Collections.sort(all, (a, b) -> {
            if (a.pinned != b.pinned) {
                return a.pinned ? -1 : 1;
            }
            return Long.compare(b.lastTime, a.lastTime);
        });
        rows.clear();
        if (requests > 0 && query.isEmpty()) {
            rows.add(new RequestsRow(requests));
        }
        rows.addAll(all);
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateEmpty(all.isEmpty());
        updateSubtitle();
    }

    private boolean matches(XmppController c, XmppConversation conv) {
        if (query.isEmpty()) {
            return true;
        }
        return conv.displayName().toLowerCase(Locale.ROOT).contains(query) || conv.jid.toLowerCase(Locale.ROOT).contains(query)
                || conv.lastBody.toLowerCase(Locale.ROOT).contains(query);
    }

    private void updateEmpty(boolean noConversations) {
        if (emptyBox == null) {
            return;
        }
        boolean hasAccounts = XmppManager.get().hasAccounts();
        boolean show = noConversations && rows.isEmpty();
        emptyBox.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!hasAccounts) {
            emptyView.setText(LocaleController.getString(R.string.XmppNoAccounts));
            emptyAction.setText(LocaleController.getString(R.string.XmppAddAccount));
            emptyAction.setVisibility(View.VISIBLE);
        } else {
            emptyView.setText(query.isEmpty() ? LocaleController.getString(R.string.XmppNoChats) : LocaleController.getString(R.string.XmppNothingFound));
            emptyAction.setVisibility(View.GONE);
        }
    }

    private void updateSubtitle() {
        if (actionBar == null) {
            return;
        }
        int total = 0;
        int online = 0;
        for (XmppController c : XmppManager.get().controllers()) {
            if (c.config().enabled) {
                total++;
                if (c.state() == XmppConnectionState.ONLINE) {
                    online++;
                }
            }
        }
        actionBar.setSubtitle(total == 0 ? null : online == total ? LocaleController.getString(R.string.XmppStateOnline)
                : online == 0 ? LocaleController.getString(R.string.XmppStateConnecting)
                : LocaleController.formatString(R.string.XmppAccountsOnline, online, total));
    }

    private void showConversationMenu(XmppConversation conv) {
        XmppController c = XmppManager.get().controller(conv.accountId);
        Context context = getParentActivity();
        if (c == null || context == null) {
            return;
        }
        List<CharSequence> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        items.add(LocaleController.getString(conv.pinned ? R.string.XmppUnpin : R.string.XmppPin));
        actions.add(() -> c.setPinned(conv.jid, !conv.pinned));
        items.add(LocaleController.getString(conv.muted ? R.string.XmppUnmute : R.string.XmppMute));
        actions.add(() -> c.setMuted(conv.jid, !conv.muted));
        if (conv.unread > 0) {
            items.add(LocaleController.getString(R.string.XmppMarkRead));
            actions.add(() -> {
                c.markRead(conv.jid);
                XmppNotifications.cancel(context, conv.accountId, conv.jid);
            });
        }
        if (!conv.groupChat) {
            items.add(LocaleController.getString(R.string.XmppContactInfo));
            actions.add(() -> XmppDialogs.contactMenu(this, c, conv.jid, null));
        }
        if (conv.groupChat) {
            items.add(LocaleController.getString(R.string.XmppLeaveGroup));
            actions.add(() -> XmppDialogs.confirm(this, LocaleController.getString(R.string.XmppLeaveGroupQuestion), R.string.XmppLeave,
                    () -> c.leaveRoom(conv.jid, true, XmppDialogs.reportErrors(this, null))));
        } else {
            items.add(LocaleController.getString(R.string.XmppDeleteChat));
            actions.add(() -> XmppDialogs.confirm(this, LocaleController.getString(R.string.XmppDeleteChatQuestion), R.string.Delete,
                    () -> c.deleteConversation(conv.jid)));
        }
        AlertDialog.Builder b = new AlertDialog.Builder(context, getResourceProvider());
        b.setTitle(conv.displayName());
        b.setItems(items.toArray(new CharSequence[0]), (d, which) -> actions.get(which).run());
        showDialog(b.create());
    }

    // ---------------------------------------------------------------------------------------------- events

    @Override
    public void onConversationsChanged(long accountId) {
        rebuild();
    }

    @Override
    public void onContactsChanged(long accountId) {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onConnectionState(long accountId, XmppConnectionState state, XmppError error) {
        updateSubtitle();
        if (state == XmppConnectionState.FAILED && error != null && error.kind == XmppError.Kind.TLS_CERTIFICATE) {
            XmppAccountsActivity.offerToTrust(this, accountId, error);
        }
    }

    @Override
    public void onSubscribeRequest(long accountId, String jid) {
        rebuild();
        XmppController c = XmppManager.get().controller(accountId);
        if (c == null || getParentActivity() == null) {
            return;
        }
        XmppDialogs.confirm(this, LocaleController.formatString(R.string.XmppSubscribeQuestion, jid), R.string.XmppAccept,
                () -> c.acceptSubscription(jid, true, XmppDialogs.reportErrors(this, this::rebuild)));
    }

    @Override
    public void onInvitation(long accountId, String roomJid, String from, String reason, String password) {
        XmppController c = XmppManager.get().controller(accountId);
        if (c == null || getParentActivity() == null) {
            return;
        }
        XmppDialogs.confirm(this, LocaleController.formatString(R.string.XmppInvitationQuestion, from.isEmpty() ? "?" : from, roomJid), R.string.XmppJoin,
                () -> c.joinRoom(roomJid, c.config().effectiveNickname(), password, false, true, XmppDialogs.reportErrors(this,
                        () -> presentFragment(new XmppChatActivity(accountId, roomJid, true)))));
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
                t.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
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
            XmppController ctl = XmppManager.get().controller(c.accountId);
            XmppShow show = XmppShow.OFFLINE;
            if (ctl != null && !c.groupChat) {
                XmppContact ct = ctl.contact(c.jid);
                if (ct != null) {
                    show = ct.show;
                }
            }
            String label = multipleAccounts() && ctl != null ? ctl.config().displayName() : null;
            ((XmppConversationCell) holder.itemView).set(c, label, show, position < rows.size() - 1);
        }
    }
}
