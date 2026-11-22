package app.retrogram.xmpp.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.jivesoftware.smackx.chatstates.ChatState;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.retrogram.xmpp.XmppManager;
import app.retrogram.xmpp.XmppNotifications;
import app.retrogram.xmpp.core.Callback;
import app.retrogram.xmpp.core.XmppAccountConfig;
import app.retrogram.xmpp.core.XmppConnectionState;
import app.retrogram.xmpp.core.XmppContact;
import app.retrogram.xmpp.core.XmppController;
import app.retrogram.xmpp.core.XmppConversation;
import app.retrogram.xmpp.core.XmppError;
import app.retrogram.xmpp.core.XmppMessage;
import app.retrogram.xmpp.core.XmppShow;

/** One conversation: a contact or a group chat. */
public class XmppChatActivity extends BaseFragment implements XmppController.Events {

    private static final int PAGE = 50;
    private static final int REQUEST_PICK_FILE = 4721;
    private static final int MENU_INFO = 1;
    private static final int MENU_MUTE = 2;
    private static final int MENU_OLDER = 3;
    private static final int MENU_CLEAR = 4;
    private static final int MENU_DELETE = 5;
    private static final int MENU_PARTICIPANTS = 6;
    private static final int MENU_INVITE = 7;
    private static final int MENU_SUBJECT = 8;
    private static final int MENU_LEAVE = 9;
    private static final int MENU_NOTIFY_ALL = 10;

    private final long accountId;
    private final String jid;
    private final boolean group;

    private XmppController controller;
    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private Adapter adapter;
    private EditTextBoldCursor input;
    private ImageView sendButton;
    private ImageView attachButton;
    private LinearLayout banner;
    private TextView bannerTitle;
    private TextView bannerText;
    private TextView emptyView;
    private ActionBarMenuItem otherItem;

    private final List<Object> rows = new ArrayList<>();
    private final Map<Long, Float> uploads = new HashMap<>();
    private final Set<String> approvedImages = new HashSet<>();
    private List<XmppMessage> messages = new ArrayList<>();
    private int limit = PAGE;
    private boolean loadingOlder;
    private boolean archiveExhausted;

    private XmppMessage replyingTo;
    private XmppMessage editing;
    private String typingFrom;
    private final Runnable clearTyping = () -> {
        typingFrom = null;
        updateSubtitle();
    };
    private long lastComposingSent;
    private boolean composing;
    private final Runnable pauseTyping;

    public XmppChatActivity(long accountId, String jid, boolean group) {
        this.accountId = accountId;
        this.jid = jid;
        this.group = group;
        this.pauseTyping = () -> {
            if (composing) {
                composing = false;
                if (controller != null) {
                    controller.sendTyping(jid, group, ChatState.paused);
                }
            }
        };
    }

    @Override
    public boolean onFragmentCreate() {
        controller = XmppManager.get().controller(accountId);
        if (controller == null) {
            return false;
        }
        controller.openChat(jid);
        XmppManager.get().addListener(this);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        XmppManager.get().removeListener(this);
        AndroidUtilities.cancelRunOnUIThread(clearTyping);
        AndroidUtilities.cancelRunOnUIThread(pauseTyping);
        if (controller != null) {
            controller.setActiveConversation("");
            if (composing) {
                controller.sendTyping(jid, group, ChatState.gone);
            }
        }
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                onMenu(id);
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        otherItem = menu.addItem(0, R.drawable.ic_ab_other);
        buildMenu();
        actionBar.setTitleActionRunnable(this::showInfo);

        FrameLayout root = new FrameLayout(context);
        fragmentView = root;
        root.setBackgroundColor(Theme.getColor(Theme.key_chat_wallpaper));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        FrameLayout listFrame = new FrameLayout(context);
        column.addView(listFrame, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        listView = new RecyclerListView(context);
        layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        listView.setLayoutManager(layoutManager);
        listView.setVerticalScrollBarEnabled(true);
        listView.setClipToPadding(false);
        listView.setPadding(0, dp(4), 0, dp(4));
        adapter = new Adapter(context);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((view, position) -> {
            Object row = position >= 0 && position < rows.size() ? rows.get(position) : null;
            if (row instanceof XmppMessage) {
                showMessageMenu((XmppMessage) row);
            }
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                if (layoutManager.findFirstVisibleItemPosition() <= 2 && dy < 0) {
                    loadOlder();
                }
            }
        });
        listFrame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new TextView(context);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptyView.setTextColor(Theme.getColor(Theme.key_chat_serviceText));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setText(LocaleController.getString(R.string.XmppNoMessages));
        listFrame.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        banner = new LinearLayout(context);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setBackgroundColor(Theme.getColor(Theme.key_chat_messagePanelBackground));
        banner.setPadding(dp(14), dp(6), dp(4), dp(6));
        LinearLayout bannerCol = new LinearLayout(context);
        bannerCol.setOrientation(LinearLayout.VERTICAL);
        bannerTitle = new TextView(context);
        bannerTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        bannerTitle.setTypeface(AndroidUtilities.bold());
        bannerTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        bannerText = new TextView(context);
        bannerText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        bannerText.setSingleLine(true);
        bannerText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bannerText.setTextColor(Theme.getColor(Theme.key_chat_messagePanelText));
        bannerCol.addView(bannerTitle);
        bannerCol.addView(bannerText);
        banner.addView(bannerCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        ImageView closeBanner = new ImageView(context);
        closeBanner.setImageResource(R.drawable.ic_close_white);
        closeBanner.setColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons));
        closeBanner.setScaleType(ImageView.ScaleType.CENTER);
        closeBanner.setOnClickListener(v -> clearBanner());
        banner.addView(closeBanner, LayoutHelper.createLinear(40, 40));
        banner.setVisibility(View.GONE);
        column.addView(banner, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.BOTTOM);
        bar.setBackgroundColor(Theme.getColor(Theme.key_chat_messagePanelBackground));
        column.addView(bar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        attachButton = new ImageView(context);
        attachButton.setImageResource(R.drawable.input_attach);
        attachButton.setColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons));
        attachButton.setScaleType(ImageView.ScaleType.CENTER);
        attachButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1));
        attachButton.setOnClickListener(v -> pickFile());
        bar.addView(attachButton, LayoutHelper.createLinear(48, 48));

        input = new EditTextBoldCursor(context);
        input.setHint(LocaleController.getString(R.string.XmppMessageHint));
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMaxLines(6);
        input.setBackground(null);
        input.setPadding(dp(4), dp(12), dp(4), dp(12));
        input.setTextColor(Theme.getColor(Theme.key_chat_messagePanelText));
        input.setHintTextColor(Theme.getColor(Theme.key_chat_messagePanelHint));
        input.setCursorColor(Theme.getColor(Theme.key_chat_messagePanelCursor));
        input.setCursorSize(dp(20));
        input.setCursorWidth(1.5f);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                boolean has = s.toString().trim().length() > 0;
                sendButton.setVisibility(has ? View.VISIBLE : View.GONE);
                attachButton.setVisibility(has || editing != null ? View.GONE : View.VISIBLE);
                if (has) {
                    typed();
                }
            }
        });
        bar.addView(input, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        sendButton = new ImageView(context);
        sendButton.setImageResource(R.drawable.ic_send);
        sendButton.setColorFilter(Theme.getColor(Theme.key_chat_messagePanelSend));
        sendButton.setScaleType(ImageView.ScaleType.CENTER);
        sendButton.setVisibility(View.GONE);
        sendButton.setOnClickListener(v -> send());
        bar.addView(sendButton, LayoutHelper.createLinear(48, 48));

        updateTitle();
        reload(true);
        return fragmentView;
    }

    private void buildMenu() {
        otherItem.removeAllSubItems();
        XmppConversation c = controller.conversation(jid);
        otherItem.addSubItem(MENU_INFO, R.drawable.msg_info, LocaleController.getString(group ? R.string.XmppRoomInfo : R.string.XmppContactInfo));
        if (group) {
            otherItem.addSubItem(MENU_PARTICIPANTS, R.drawable.msg_groups, LocaleController.getString(R.string.XmppParticipants));
            otherItem.addSubItem(MENU_INVITE, R.drawable.msg_addcontact, LocaleController.getString(R.string.XmppInvite));
            otherItem.addSubItem(MENU_SUBJECT, R.drawable.msg_edit, LocaleController.getString(R.string.XmppChangeSubject));
            otherItem.addSubItem(MENU_NOTIFY_ALL, R.drawable.msg_mention, LocaleController.getString(c != null && c.notifyAll ? R.string.XmppNotifyMentionsOnly : R.string.XmppNotifyAll));
        }
        otherItem.addSubItem(MENU_MUTE, c != null && c.muted ? R.drawable.msg_unmute : R.drawable.msg_mute,
                LocaleController.getString(c != null && c.muted ? R.string.XmppUnmute : R.string.XmppMute));
        otherItem.addSubItem(MENU_OLDER, R.drawable.msg_retry, LocaleController.getString(R.string.XmppLoadEarlier));
        otherItem.addSubItem(MENU_CLEAR, R.drawable.msg_clear, LocaleController.getString(R.string.XmppClearHistory));
        if (group) {
            otherItem.addSubItem(MENU_LEAVE, R.drawable.msg_leave, LocaleController.getString(R.string.XmppLeaveGroup));
        } else {
            otherItem.addSubItem(MENU_DELETE, R.drawable.msg_delete, LocaleController.getString(R.string.XmppDeleteChat));
        }
    }

    private void onMenu(int id) {
        switch (id) {
            case -1:
                finishFragment();
                break;
            case MENU_INFO:
                showInfo();
                break;
            case MENU_PARTICIPANTS:
                presentFragment(new XmppParticipantsActivity(accountId, jid));
                break;
            case MENU_INVITE:
                XmppUi.input(this, LocaleController.getString(R.string.XmppInvite), LocaleController.getString(R.string.XmppAddressHint), "", R.string.XmppInvite, text -> {
                    String user = app.retrogram.xmpp.core.Jids.parseUserInput(text);
                    if (user == null) {
                        XmppUi.toast(this, LocaleController.getString(R.string.XmppErrorInvalid));
                        return;
                    }
                    controller.session().invite(jid, user, "", XmppDialogs.reportErrors(this, () -> XmppUi.toast(this, LocaleController.getString(R.string.XmppInvitationSent))));
                });
                break;
            case MENU_SUBJECT: {
                XmppConversation c = controller.conversation(jid);
                XmppUi.input(this, LocaleController.getString(R.string.XmppChangeSubject), LocaleController.getString(R.string.XmppSubjectHint),
                        c == null ? "" : c.subject, R.string.Save,
                        text -> controller.session().setRoomSubject(jid, text, XmppDialogs.reportErrors(this, null)));
                break;
            }
            case MENU_NOTIFY_ALL: {
                XmppConversation c = controller.conversation(jid);
                controller.setNotifyAll(jid, c == null || !c.notifyAll);
                buildMenu();
                break;
            }
            case MENU_MUTE: {
                XmppConversation c = controller.conversation(jid);
                controller.setMuted(jid, c == null || !c.muted);
                buildMenu();
                break;
            }
            case MENU_OLDER:
                archiveExhausted = false;
                limit += PAGE;
                loadOlder();
                break;
            case MENU_CLEAR:
                XmppDialogs.confirm(this, LocaleController.getString(R.string.XmppClearHistoryQuestion), R.string.XmppClear, () -> {
                    List<XmppMessage> all = controller.messages(jid, 0, 100000);
                    for (XmppMessage m : all) {
                        controller.deleteMessageLocally(m.dbId);
                    }
                });
                break;
            case MENU_DELETE:
                XmppDialogs.confirm(this, LocaleController.getString(R.string.XmppDeleteChatQuestion), R.string.Delete, () -> {
                    controller.deleteConversation(jid);
                    finishFragment();
                });
                break;
            case MENU_LEAVE:
                XmppDialogs.confirm(this, LocaleController.getString(R.string.XmppLeaveGroupQuestion), R.string.XmppLeave,
                        () -> controller.leaveRoom(jid, true, XmppDialogs.reportErrors(this, this::finishFragment)));
                break;
            default:
                break;
        }
    }

    private void showInfo() {
        if (group) {
            presentFragment(new XmppParticipantsActivity(accountId, jid));
        } else {
            XmppDialogs.contactMenu(this, controller, jid, null);
        }
    }

    // ---------------------------------------------------------------------------------------------- header

    private void updateTitle() {
        if (actionBar == null) {
            return;
        }
        XmppConversation c = controller.conversation(jid);
        String name = c != null ? c.displayName() : XmppManager.get().nameOf(accountId, jid);
        actionBar.setTitle(name);
        updateSubtitle();
    }

    private void updateSubtitle() {
        if (actionBar == null) {
            return;
        }
        String sub;
        if (typingFrom != null) {
            sub = group ? LocaleController.formatString(R.string.XmppTypingNamed, typingFrom) : LocaleController.getString(R.string.XmppTyping);
        } else if (controller.state() != XmppConnectionState.ONLINE) {
            sub = LocaleController.getString(R.string.XmppStateOffline);
        } else if (group) {
            XmppConversation c = controller.conversation(jid);
            int n = controller.session().occupants(jid).size();
            if (c != null && !c.joined) {
                sub = LocaleController.getString(R.string.XmppNotInRoom);
            } else if (c != null && !c.subject.isEmpty()) {
                sub = c.subject;
            } else {
                sub = LocaleController.formatString(R.string.XmppParticipantCount, n);
            }
        } else {
            XmppContact ct = controller.contact(jid);
            if (ct == null) {
                sub = jid;
            } else if (ct.show == XmppShow.OFFLINE) {
                sub = LocaleController.getString(R.string.XmppShowOffline);
            } else {
                sub = XmppUi.showName(ct.show) + (ct.status.isEmpty() ? "" : " · " + ct.status);
            }
        }
        actionBar.setSubtitle(sub);
    }

    // ---------------------------------------------------------------------------------------------- list

    private void reload(boolean scrollToEnd) {
        boolean atEnd = layoutManager == null || layoutManager.findLastVisibleItemPosition() >= rows.size() - 2;
        messages = controller.messages(jid, 0, limit);
        rows.clear();
        int lastDay = -1;
        int lastYear = -1;
        Calendar cal = Calendar.getInstance();
        for (XmppMessage m : messages) {
            cal.setTimeInMillis(m.time);
            int day = cal.get(Calendar.DAY_OF_YEAR);
            int year = cal.get(Calendar.YEAR);
            if (day != lastDay || year != lastYear) {
                rows.add(LocaleController.formatDateChat(m.time / 1000, true));
                lastDay = day;
                lastYear = year;
            }
            rows.add(m);
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        if (emptyView != null) {
            emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if ((scrollToEnd || atEnd) && !rows.isEmpty() && layoutManager != null) {
            layoutManager.scrollToPositionWithOffset(rows.size() - 1, 0);
        }
    }

    private void loadOlder() {
        if (loadingOlder) {
            return;
        }
        loadingOlder = true;
        int before = messages.size();
        int firstVisible = layoutManager.findFirstVisibleItemPosition();
        limit = Math.max(limit, messages.size()) + PAGE;
        List<XmppMessage> now = controller.messages(jid, 0, limit);
        if (now.size() > before) {
            int added = rows.size();
            reload(false);
            // Keep the viewport where it was: the new rows are above it.
            layoutManager.scrollToPositionWithOffset(Math.max(0, firstVisible + (rows.size() - added)), 0);
            loadingOlder = false;
            return;
        }
        if (archiveExhausted || controller.state() != XmppConnectionState.ONLINE) {
            loadingOlder = false;
            return;
        }
        controller.loadOlder(jid, new Callback<Integer>() {
            @Override
            public void onResult(Integer count) {
                AndroidUtilities.runOnUIThread(() -> {
                    loadingOlder = false;
                    if (count == null || count == 0) {
                        archiveExhausted = true;
                    } else {
                        reload(false);
                    }
                });
            }

            @Override
            public void onError(XmppError error) {
                AndroidUtilities.runOnUIThread(() -> {
                    loadingOlder = false;
                    archiveExhausted = true;
                });
            }
        });
    }

    private XmppMessage findById(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (XmppMessage m : messages) {
            if (id.equals(m.stanzaId) || id.equals(m.serverId)) {
                return m;
            }
        }
        return null;
    }

    private String nameOfSender(XmppMessage m) {
        if (m.outgoing) {
            return LocaleController.getString(R.string.XmppYou);
        }
        if (group) {
            return m.sender;
        }
        return XmppManager.get().nameOf(accountId, m.conversation);
    }

    private boolean autoLoadImage(XmppMessage m) {
        if (m.outgoing || approvedImages.contains(m.attachmentUrl)) {
            return true;
        }
        XmppAccountConfig cfg = controller.config();
        try {
            String host = new java.net.URL(m.attachmentUrl).getHost();
            if (host != null && (host.equalsIgnoreCase(cfg.domain()) || host.toLowerCase().endsWith("." + cfg.domain().toLowerCase()))) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return !group && controller.contact(m.conversation) != null;
    }

    private final class Adapter extends RecyclerListView.SelectionAdapter {
        private static final int TYPE_MESSAGE = 0;
        private static final int TYPE_DATE = 1;
        private final Context context;

        Adapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == TYPE_MESSAGE;
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position) instanceof XmppMessage ? TYPE_MESSAGE : TYPE_DATE;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = viewType == TYPE_MESSAGE ? new XmppMessageRow(context) : new XmppDateRow(context);
            v.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(v);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Object row = rows.get(position);
            if (row instanceof XmppMessage) {
                XmppMessage m = (XmppMessage) row;
                String label = group && !m.outgoing ? m.sender : null;
                int color = Theme.getColor(Theme.key_chat_inReplyNameText);
                if (label != null) {
                    color = org.telegram.ui.Components.AvatarDrawable.getColorForId(Math.abs((long) label.hashCode()));
                    color = 0xFF000000 | color;
                }
                String qa = null;
                String qb = null;
                if (!m.replyToId.isEmpty()) {
                    XmppMessage original = findById(m.replyToId);
                    qa = original != null ? nameOfSender(original) : m.replyToAuthor.isEmpty() ? "" : m.replyToAuthor;
                    qb = original != null ? original.body : m.replyToBody;
                } else if (!m.replyToBody.isEmpty()) {
                    qa = "";
                    qb = m.replyToBody;
                }
                float progress = uploads.containsKey(m.dbId) ? uploads.get(m.dbId) : -1f;
                ((XmppMessageRow) holder.itemView).set(m, label, color, qa, qb, autoLoadImage(m), progress, (msg, loaded) -> {
                    if (loaded) {
                        openUrl(msg.attachmentUrl);
                    } else {
                        approvedImages.add(msg.attachmentUrl);
                        notifyDataSetChanged();
                    }
                });
            } else {
                ((XmppDateRow) holder.itemView).set((String) row);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------- actions

    private void openUrl(String url) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            context.startActivity(i);
        } catch (Exception e) {
            XmppUi.toast(this, LocaleController.getString(R.string.XmppNoAppToOpen));
        }
    }

    private static String firstUrl(String text) {
        java.util.regex.Matcher m = android.util.Patterns.WEB_URL.matcher(text);
        while (m.find()) {
            String u = m.group();
            if (u.startsWith("http://") || u.startsWith("https://")) {
                return u;
            }
        }
        return null;
    }

    private void showMessageMenu(XmppMessage m) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        List<CharSequence> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (m.status == XmppMessage.STATUS_FAILED) {
            items.add(LocaleController.getString(R.string.XmppRetry));
            actions.add(() -> controller.resend(m.dbId));
        }
        items.add(LocaleController.getString(R.string.XmppReply));
        actions.add(() -> startReply(m));
        items.add(LocaleController.getString(R.string.Copy));
        actions.add(() -> {
            XmppDialogs.copy(context, m.body);
            XmppUi.toast(this, LocaleController.getString(R.string.XmppCopied));
        });
        String link = m.hasAttachment() ? m.attachmentUrl : firstUrl(m.body);
        if (link != null) {
            items.add(LocaleController.getString(R.string.XmppOpenLink));
            actions.add(() -> openUrl(link));
        }
        boolean textOnly = !m.hasAttachment() && m.attachmentPath.isEmpty();
        if (m.outgoing && textOnly && m.status != XmppMessage.STATUS_PENDING && m.status != XmppMessage.STATUS_FAILED) {
            items.add(LocaleController.getString(R.string.XmppEditMessage));
            actions.add(() -> startEdit(m));
        }
        if (group && !m.outgoing) {
            items.add(LocaleController.formatString(R.string.XmppMention, m.sender));
            actions.add(() -> {
                String t = input.getText().toString();
                input.setText((t.isEmpty() || t.endsWith(" ") ? t : t + " ") + m.sender + ": ");
                input.setSelection(input.length());
            });
        }
        items.add(LocaleController.getString(R.string.XmppDeleteForMe));
        actions.add(() -> controller.deleteMessageLocally(m.dbId));
        AlertDialog.Builder b = new AlertDialog.Builder(context, getResourceProvider());
        b.setItems(items.toArray(new CharSequence[0]), (d, which) -> actions.get(which).run());
        showDialog(b.create());
    }

    private void startReply(XmppMessage m) {
        editing = null;
        replyingTo = m;
        bannerTitle.setText(LocaleController.formatString(R.string.XmppReplyingTo, nameOfSender(m)));
        bannerText.setText(m.body);
        banner.setVisibility(View.VISIBLE);
        input.requestFocus();
        AndroidUtilities.showKeyboard(input);
    }

    private void startEdit(XmppMessage m) {
        replyingTo = null;
        editing = m;
        bannerTitle.setText(LocaleController.getString(R.string.XmppEditing));
        bannerText.setText(m.body);
        banner.setVisibility(View.VISIBLE);
        input.setText(m.body);
        input.setSelection(input.length());
        attachButton.setVisibility(View.GONE);
        input.requestFocus();
        AndroidUtilities.showKeyboard(input);
    }

    private void clearBanner() {
        if (editing != null) {
            input.setText("");
        }
        replyingTo = null;
        editing = null;
        banner.setVisibility(View.GONE);
        attachButton.setVisibility(input.getText().length() == 0 ? View.VISIBLE : View.GONE);
    }

    private void send() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        if (editing != null) {
            XmppMessage target = editing;
            if (!text.equals(target.body)) {
                controller.editMessage(target.dbId, text, XmppDialogs.reportErrors(this, null));
            }
            editing = null;
            banner.setVisibility(View.GONE);
            input.setText("");
            return;
        }
        controller.sendText(jid, group, text, replyingTo);
        replyingTo = null;
        banner.setVisibility(View.GONE);
        input.setText("");
        stopComposing();
        layoutManager.scrollToPositionWithOffset(Math.max(0, rows.size() - 1), 0);
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, LocaleController.getString(R.string.XmppAttach)), REQUEST_PICK_FILE);
        } catch (Exception e) {
            XmppUi.toast(this, LocaleController.getString(R.string.XmppNoAppToOpen));
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_PICK_FILE || resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        Uri uri = data.getData();
        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            java.io.File f = app.retrogram.xmpp.XmppFiles.copyToOutbox(context, uri);
            AndroidUtilities.runOnUIThread(() -> {
                if (f == null) {
                    XmppUi.toast(this, LocaleController.getString(R.string.XmppCannotReadFile));
                    return;
                }
                String mime = app.retrogram.xmpp.XmppFiles.mimeOf(context, uri, f.getName());
                controller.sendFile(jid, group, f, mime);
            });
        });
    }

    // ---------------------------------------------------------------------------------------------- typing

    private void typed() {
        if (editing != null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!composing || now - lastComposingSent > 4000) {
            composing = true;
            lastComposingSent = now;
            controller.sendTyping(jid, group, ChatState.composing);
        }
        AndroidUtilities.cancelRunOnUIThread(pauseTyping);
        AndroidUtilities.runOnUIThread(pauseTyping, 5000);
    }

    private void stopComposing() {
        AndroidUtilities.cancelRunOnUIThread(pauseTyping);
        if (composing) {
            composing = false;
            controller.sendTyping(jid, group, ChatState.active);
        }
    }

    // ---------------------------------------------------------------------------------------------- lifecycle

    @Override
    public void onResume() {
        super.onResume();
        if (controller != null) {
            controller.setActiveConversation(jid);
            XmppNotifications.cancel(getParentActivity() == null ? org.telegram.messenger.ApplicationLoader.applicationContext : getParentActivity(), accountId, jid);
            updateSubtitle();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (controller != null) {
            controller.setActiveConversation("");
        }
    }

    // ---------------------------------------------------------------------------------------------- events (UI thread)

    @Override
    public void onMessagesChanged(long account, String conversation) {
        if (account == accountId && jid.equalsIgnoreCase(conversation)) {
            reload(false);
            XmppNotifications.cancel(org.telegram.messenger.ApplicationLoader.applicationContext, accountId, jid);
        }
    }

    @Override
    public void onConversationsChanged(long account) {
        if (account == accountId) {
            updateTitle();
            if (otherItem != null) {
                buildMenu();
            }
        }
    }

    @Override
    public void onConnectionState(long account, XmppConnectionState state, XmppError error) {
        if (account == accountId) {
            updateSubtitle();
        }
    }

    @Override
    public void onContactsChanged(long account) {
        if (account == accountId) {
            updateSubtitle();
        }
    }

    @Override
    public void onOccupantsChanged(long account, String roomJid) {
        if (account == accountId && jid.equalsIgnoreCase(roomJid)) {
            updateSubtitle();
        }
    }

    @Override
    public void onChatState(long account, String conversation, String from, ChatState state) {
        if (account != accountId || !jid.equalsIgnoreCase(conversation)) {
            return;
        }
        AndroidUtilities.cancelRunOnUIThread(clearTyping);
        if (state == ChatState.composing) {
            typingFrom = group ? app.retrogram.xmpp.core.Jids.resource(from) : "";
            AndroidUtilities.runOnUIThread(clearTyping, 8000);
        } else {
            typingFrom = null;
        }
        updateSubtitle();
    }

    @Override
    public void onUploadProgress(long account, long messageDbId, long sent, long total) {
        if (account != accountId) {
            return;
        }
        float p = total <= 0 ? 0 : (float) sent / total;
        Float old = uploads.get(messageDbId);
        if (old == null || p - old > 0.02f || p >= 1f) {
            if (p >= 1f) {
                uploads.remove(messageDbId);
            } else {
                uploads.put(messageDbId, p);
            }
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onRoomClosed(long account, String roomJid, XmppError reason) {
        if (account == accountId && jid.equalsIgnoreCase(roomJid)) {
            XmppUi.toast(this, LocaleController.getString(R.string.XmppRoomClosed) + (reason == null ? "" : ": " + XmppUi.errorText(reason)));
            updateSubtitle();
        }
    }
}
