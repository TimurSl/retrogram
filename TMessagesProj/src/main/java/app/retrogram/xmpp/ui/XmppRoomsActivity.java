package app.retrogram.xmpp.ui;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import app.retrogram.xmpp.XmppManager;
import app.retrogram.xmpp.core.Callback;
import app.retrogram.xmpp.core.XmppController;
import app.retrogram.xmpp.core.XmppError;

/** Public rooms of the account's group chat service. Tap one to join. */
public class XmppRoomsActivity extends BaseFragment {

    private final long accountId;
    private XmppController controller;
    private RecyclerListView listView;
    private TextView status;
    private final List<String[]> rooms = new ArrayList<>(); // {jid, name}

    public XmppRoomsActivity(long accountId) {
        this.accountId = accountId;
    }

    @Override
    public boolean onFragmentCreate() {
        controller = XmppManager.get().controller(accountId);
        return controller != null && super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.XmppBrowseGroups));
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
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(new RecyclerListView.SelectionAdapter() {
            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return true;
            }

            @Override
            public int getItemCount() {
                return rooms.size();
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                XmppContactCell v = new XmppContactCell(context);
                v.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(v);
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                String[] r = rooms.get(position);
                ((XmppContactCell) holder.itemView).set(accountId, r[0], r[1].isEmpty() ? r[0] : r[1], r[0], null, false, position < rooms.size() - 1);
            }
        });
        listView.setOnItemClickListener((view, position) -> {
            String room = rooms.get(position)[0];
            XmppDialogs.joinRoom(this, controller, room, false, joined -> presentFragment(new XmppChatActivity(accountId, joined, true), true));
        });
        frame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        status = new TextView(context);
        status.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        status.setGravity(Gravity.CENTER);
        status.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        status.setText(LocaleController.getString(R.string.XmppLoading));
        frame.addView(status, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 32, 0, 32, 0));

        controller.session().listRooms(null, new Callback<Map<String, String>>() {
            @Override
            public void onResult(Map<String, String> value) {
                AndroidUtilities.runOnUIThread(() -> {
                    rooms.clear();
                    for (Map.Entry<String, String> e : value.entrySet()) {
                        rooms.add(new String[]{e.getKey(), e.getValue()});
                    }
                    rooms.sort((a, b) -> a[1].compareToIgnoreCase(b[1]));
                    listView.getAdapter().notifyDataSetChanged();
                    status.setText(LocaleController.getString(R.string.XmppNoPublicRooms));
                    status.setVisibility(rooms.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }

            @Override
            public void onError(XmppError error) {
                AndroidUtilities.runOnUIThread(() -> {
                    status.setText(XmppUi.errorText(error));
                    status.setVisibility(View.VISIBLE);
                });
            }
        });
        return fragmentView;
    }
}
