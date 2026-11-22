package app.retrogram.xmpp.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;

import app.retrogram.xmpp.XmppManager;
import app.retrogram.xmpp.core.XmppController;
import app.retrogram.xmpp.core.XmppConversation;
import app.retrogram.xmpp.core.XmppSession;

/** Room information: subject and the people in the room. Tap a person to start a private chat or to copy their address. */
public class XmppParticipantsActivity extends BaseFragment implements XmppController.Events {

    private final long accountId;
    private final String roomJid;
    private XmppController controller;
    private RecyclerListView listView;
    private Adapter adapter;
    private List<XmppSession.Occ> occupants = new ArrayList<>();

    public XmppParticipantsActivity(long accountId, String roomJid) {
        this.accountId = accountId;
        this.roomJid = roomJid;
    }

    @Override
    public boolean onFragmentCreate() {
        controller = XmppManager.get().controller(accountId);
        if (controller == null) {
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
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.XmppParticipants));
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
            int index = position - 2;
            if (index < 0 || index >= occupants.size()) {
                return;
            }
            XmppSession.Occ o = occupants.get(index);
            String target = o.realJid.isEmpty() ? null : o.realJid;
            if (target != null) {
                XmppDialogs.contactMenu(this, controller, target, () -> presentFragment(new XmppChatActivity(accountId, target, false)));
            } else {
                // The room hides real addresses: talk to the occupant through the room.
                String occupant = roomJid + "/" + o.nick;
                controller.openChat(occupant);
                presentFragment(new XmppChatActivity(accountId, occupant, false));
            }
        });
        frame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        refresh();
        return fragmentView;
    }

    private void refresh() {
        occupants = controller.session().occupants(roomJid);
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onOccupantsChanged(long account, String room) {
        if (account == accountId && roomJid.equalsIgnoreCase(room)) {
            refresh();
        }
    }

    private final class Adapter extends RecyclerListView.SelectionAdapter {
        private static final int HEADER = 0;
        private static final int INFO = 1;
        private static final int PERSON = 2;
        private static final int SHADOW = 3;
        private final Context context;

        Adapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == PERSON;
        }

        @Override
        public int getItemCount() {
            return 2 + occupants.size();
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? INFO : position == 1 ? HEADER : PERSON;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v;
            switch (viewType) {
                case HEADER:
                    v = new HeaderCell(context);
                    v.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case INFO:
                    v = new TextInfoPrivacyCell(context);
                    break;
                default:
                    v = new XmppContactCell(context);
                    v.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            v.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(v);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case INFO: {
                    XmppConversation c = controller.conversation(roomJid);
                    String subject = c == null || c.subject.isEmpty() ? LocaleController.getString(R.string.XmppNoSubject) : c.subject;
                    ((TextInfoPrivacyCell) holder.itemView).setText(roomJid + "\n\n" + subject);
                    break;
                }
                case HEADER:
                    ((HeaderCell) holder.itemView).setText(LocaleController.formatString(R.string.XmppParticipantCount, occupants.size()));
                    break;
                default: {
                    XmppSession.Occ o = occupants.get(position - 2);
                    String role = o.affiliation.equals("owner") ? LocaleController.getString(R.string.XmppOwner)
                            : o.affiliation.equals("admin") ? LocaleController.getString(R.string.XmppAdmin)
                            : o.role.equals("moderator") ? LocaleController.getString(R.string.XmppModerator) : "";
                    String line = role.isEmpty() ? XmppUi.showName(o.show) : role + " · " + XmppUi.showName(o.show);
                    if (!o.status.isEmpty()) {
                        line += " · " + o.status;
                    }
                    ((XmppContactCell) holder.itemView).set(accountId, o.realJid.isEmpty() ? roomJid + "/" + o.nick : o.realJid, o.nick, line, o.show, false,
                            position - 2 < occupants.size() - 1);
                    break;
                }
            }
        }
    }
}
