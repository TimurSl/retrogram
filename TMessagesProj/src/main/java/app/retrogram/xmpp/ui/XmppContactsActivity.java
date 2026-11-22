package app.retrogram.xmpp.ui;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import app.retrogram.xmpp.XmppManager;
import app.retrogram.xmpp.core.XmppContact;
import app.retrogram.xmpp.core.XmppController;
import app.retrogram.xmpp.core.XmppShow;

/** The roster of every account, with pending contact requests on top. */
public class XmppContactsActivity extends BaseFragment implements XmppController.Events {

    private static final int MENU_SEARCH = 1;
    private static final int MENU_ADD = 2;

    private static final class Header {
        final String text;

        Header(String text) {
            this.text = text;
        }
    }

    private static final class Request {
        final XmppController controller;
        final String jid;

        Request(XmppController controller, String jid) {
            this.controller = controller;
            this.jid = jid;
        }
    }

    private static final class Entry {
        final XmppController controller;
        final XmppContact contact;

        Entry(XmppController controller, XmppContact contact) {
            this.controller = controller;
            this.contact = contact;
        }
    }

    private RecyclerListView listView;
    private Adapter adapter;
    private TextView emptyView;
    private final List<Object> rows = new ArrayList<>();
    private String query = "";

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
        actionBar.setTitle(LocaleController.getString(R.string.XmppContacts));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_ADD) {
                    XmppDialogs.pickAccount(XmppContactsActivity.this, -1, c -> XmppDialogs.addContact(XmppContactsActivity.this, c, ""));
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(MENU_SEARCH, R.drawable.ic_ab_search).setIsSearchField(true)
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
        menu.addItem(MENU_ADD, R.drawable.msg_addcontact);

        FrameLayout frame = new FrameLayout(context);
        fragmentView = frame;
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        adapter = new Adapter(context);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((view, position) -> {
            Object row = rows.get(position);
            if (row instanceof Entry) {
                Entry e = (Entry) row;
                e.controller.openChat(e.contact.jid);
                presentFragment(new XmppChatActivity(e.controller.accountId(), e.contact.jid, false));
            }
        });
        listView.setOnItemLongClickListener((view, position) -> {
            Object row = rows.get(position);
            if (row instanceof Entry) {
                Entry e = (Entry) row;
                XmppDialogs.contactMenu(this, e.controller, e.contact.jid, () -> presentFragment(new XmppChatActivity(e.controller.accountId(), e.contact.jid, false)));
                return true;
            }
            return false;
        });
        frame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new TextView(context);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyView.setText(LocaleController.getString(R.string.XmppNoContacts));
        frame.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 32, 0, 32, 0));
        rebuild();
        return fragmentView;
    }

    private void rebuild() {
        rows.clear();
        List<XmppController> controllers = XmppManager.get().controllers();
        boolean multiple = controllers.size() > 1;
        for (XmppController c : controllers) {
            List<Object> mine = new ArrayList<>();
            for (String jid : new ArrayList<>(c.pendingSubscribers())) {
                if (query.isEmpty() || jid.toLowerCase(Locale.ROOT).contains(query)) {
                    mine.add(new Request(c, jid));
                }
            }
            List<XmppContact> contacts = c.contacts();
            Collections.sort(contacts, (a, b) -> {
                boolean oa = a.show != XmppShow.OFFLINE;
                boolean ob = b.show != XmppShow.OFFLINE;
                if (oa != ob) {
                    return oa ? -1 : 1;
                }
                return a.displayName().compareToIgnoreCase(b.displayName());
            });
            for (XmppContact ct : contacts) {
                if (query.isEmpty() || ct.displayName().toLowerCase(Locale.ROOT).contains(query) || ct.jid.toLowerCase(Locale.ROOT).contains(query)) {
                    mine.add(new Entry(c, ct));
                }
            }
            if (!mine.isEmpty()) {
                if (multiple) {
                    rows.add(new Header(c.config().displayName()));
                }
                rows.addAll(mine);
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        if (emptyView != null) {
            emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onContactsChanged(long accountId) {
        rebuild();
    }

    @Override
    public void onSubscribeRequest(long accountId, String jid) {
        rebuild();
    }

    private final class Adapter extends RecyclerListView.SelectionAdapter {
        private static final int HEADER = 0;
        private static final int REQUEST = 1;
        private static final int CONTACT = 2;
        private final Context context;

        Adapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == CONTACT;
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @Override
        public int getItemViewType(int position) {
            Object r = rows.get(position);
            return r instanceof Header ? HEADER : r instanceof Request ? REQUEST : CONTACT;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v;
            if (viewType == HEADER) {
                HeaderCell h = new HeaderCell(context);
                h.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                v = h;
            } else {
                v = new XmppContactCell(context);
            }
            v.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(v);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Object r = rows.get(position);
            if (r instanceof Header) {
                ((HeaderCell) holder.itemView).setText(((Header) r).text);
            } else if (r instanceof Request) {
                Request q = (Request) r;
                XmppContactCell cell = (XmppContactCell) holder.itemView;
                cell.set(q.controller.accountId(), q.jid, q.jid, LocaleController.getString(R.string.XmppWantsToSeeYou), null, true, true);
                cell.setAction(LocaleController.getString(R.string.XmppAccept), v -> q.controller.acceptSubscription(q.jid, true, XmppDialogs.reportErrors(XmppContactsActivity.this, XmppContactsActivity.this::rebuild)));
            } else {
                Entry e = (Entry) r;
                XmppContact c = e.contact;
                String line = c.show == XmppShow.OFFLINE ? LocaleController.getString(R.string.XmppShowOffline) : XmppUi.showName(c.show);
                if (!c.status.isEmpty()) {
                    line += " · " + c.status;
                }
                if (c.pendingOut) {
                    line = LocaleController.getString(R.string.XmppRequestPending);
                } else if (!c.subscription.receives()) {
                    line = LocaleController.getString(R.string.XmppNotSubscribed);
                }
                XmppContactCell cell = (XmppContactCell) holder.itemView;
                cell.set(e.controller.accountId(), c.jid, c.displayName(), line, c.show, true, position < rows.size() - 1);
                cell.setAction(null, null);
            }
        }
    }
}
