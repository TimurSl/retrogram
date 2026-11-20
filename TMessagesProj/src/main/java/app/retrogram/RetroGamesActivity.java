package app.retrogram;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * "Games" from the chat list menu (retro mode): the HTML5 bot games that were posted in your chats, newest first, one
 * row per game. Tapping a row opens the chat at that message, where its Play button starts the game.
 *
 * Telegram has no server-side "games" filter, so this reads the messages already stored on this device: a chat that was
 * never opened (or was cleared from the cache) does not contribute.
 */
public class RetroGamesActivity extends BaseFragment {

    /** {@code messageMediaGame} constructor 0xfdb19008 as it appears in a serialized message (little-endian). */
    static final String MEDIA_GAME_BYTES = "0890b1fd";

    /** Upper bound on scanned candidate rows, to keep the query cheap on big caches. */
    private static final int SCAN_LIMIT = 2000;

    private static final int ID_FIRST_GAME = 100;

    private UniversalRecyclerView listView;
    private final ArrayList<Entry> entries = new ArrayList<>();
    private boolean loading = true;

    static final class Entry {
        final long dialogId;
        final int messageId;
        final TLRPC.TL_game game;
        final long botId;

        Entry(long dialogId, int messageId, TLRPC.TL_game game, long botId) {
            this.dialogId = dialogId;
            this.messageId = messageId;
            this.game = game;
            this.botId = botId;
        }
    }

    @Override
    public boolean onFragmentCreate() {
        load();
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.RetroGames));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = frameLayout;
        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (loading) {
            items.add(UItem.asShadow(getString(R.string.Loading)));
            return;
        }
        if (entries.isEmpty()) {
            items.add(UItem.asShadow(getString(R.string.RetroGamesEmpty)));
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            items.add(UItem.asButton(ID_FIRST_GAME + i, R.drawable.retro_menu_games, entry.game.title, botName(entry.botId)));
        }
        items.add(UItem.asShadow(getString(R.string.RetroGamesInfo)));
    }

    private CharSequence botName(long botId) {
        if (botId == 0) {
            return null;
        }
        TLRPC.User bot = getMessagesController().getUser(botId);
        if (bot == null) {
            return null;
        }
        String username = UserObject.getPublicUsername(bot);
        return !TextUtils.isEmpty(username) ? "@" + username : UserObject.getUserName(bot);
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        int index = item.id - ID_FIRST_GAME;
        if (index < 0 || index >= entries.size()) {
            return;
        }
        Entry entry = entries.get(index);
        presentFragment(ChatActivity.of(entry.dialogId, entry.messageId));
    }

    private void load() {
        final MessagesStorage storage = getMessagesStorage();
        final long selfId = getUserConfig().getClientUserId();
        storage.getStorageQueue().postRunnable(() -> {
            final ArrayList<Entry> result = new ArrayList<>();
            final ArrayList<Long> botIds = new ArrayList<>();
            SQLiteCursor cursor = null;
            try {
                // instr() on a blob compares bytes, so this only deserializes rows that can hold a game.
                cursor = storage.getDatabase().queryFinalized("SELECT data, uid, mid FROM messages_v2 WHERE instr(data, X'" + MEDIA_GAME_BYTES + "') > 0 ORDER BY date DESC LIMIT " + SCAN_LIMIT);
                final HashSet<String> seen = new HashSet<>();
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data == null) {
                        continue;
                    }
                    TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                    data.reuse();
                    if (message == null || !(message.media instanceof TLRPC.TL_messageMediaGame) || message.media.game == null) {
                        continue;
                    }
                    final long dialogId = cursor.longValue(1);
                    if (DialogObject.isEncryptedDialog(dialogId)) {
                        continue;
                    }
                    final TLRPC.TL_game game = message.media.game;
                    final long botId = message.via_bot_id != 0 ? message.via_bot_id : MessageObject.getFromChatId(message);
                    final String key = game.id != 0 ? "id" + game.id : botId + "/" + game.short_name;
                    if (!seen.add(key)) {
                        continue;
                    }
                    result.add(new Entry(dialogId, cursor.intValue(2), game, botId > 0 && botId != selfId ? botId : 0));
                    if (botId > 0 && botId != selfId) {
                        botIds.add(botId);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
            final ArrayList<TLRPC.User> users = new ArrayList<>();
            if (!botIds.isEmpty()) {
                try {
                    storage.getUsersInternal(botIds, users);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                getMessagesController().putUsers(users, true);
                entries.clear();
                entries.addAll(result);
                loading = false;
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            });
        });
    }
}
