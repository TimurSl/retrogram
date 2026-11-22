package app.retrogram.xmpp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.List;

import app.retrogram.xmpp.core.XmppContact;
import app.retrogram.xmpp.core.XmppConversation;
import app.retrogram.xmpp.core.XmppMessage;
import app.retrogram.xmpp.core.XmppShow;
import app.retrogram.xmpp.core.XmppStore;

/** SQLite implementation of {@link XmppStore}. A separate file from Telegram's own storage. */
public final class XmppDatabase extends SQLiteOpenHelper implements XmppStore {

    private static final int VERSION = 1;

    /** Statements are public so a test can run them against a real SQLite. */
    static final String[] SCHEMA = {
            "CREATE TABLE messages (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, account INTEGER NOT NULL, conv TEXT NOT NULL, grp INTEGER NOT NULL," +
                    "outgoing INTEGER NOT NULL, sender TEXT, body TEXT, time INTEGER NOT NULL, stanza_id TEXT, origin_id TEXT," +
                    "server_id TEXT, edited INTEGER NOT NULL DEFAULT 0, status INTEGER NOT NULL DEFAULT 0, read INTEGER NOT NULL DEFAULT 1," +
                    "url TEXT, path TEXT, reply_id TEXT, reply_body TEXT, reply_author TEXT, error TEXT)",
            "CREATE INDEX messages_conv ON messages (account, conv, time)",
            "CREATE INDEX messages_stanza ON messages (account, conv, stanza_id)",
            "CREATE INDEX messages_server ON messages (account, conv, server_id)",
            "CREATE INDEX messages_origin ON messages (account, conv, origin_id)",
            "CREATE TABLE conversations (" +
                    "account INTEGER NOT NULL, jid TEXT NOT NULL, grp INTEGER NOT NULL, title TEXT, last_body TEXT," +
                    "last_time INTEGER NOT NULL DEFAULT 0, last_out INTEGER NOT NULL DEFAULT 0, unread INTEGER NOT NULL DEFAULT 0," +
                    "joined INTEGER NOT NULL DEFAULT 0, subject TEXT, nick TEXT, password TEXT, muted INTEGER NOT NULL DEFAULT 0," +
                    "notify_all INTEGER NOT NULL DEFAULT 0, pinned INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (account, jid))",
            "CREATE TABLE contacts (" +
                    "account INTEGER NOT NULL, jid TEXT NOT NULL, name TEXT, groups TEXT, sub TEXT, pending INTEGER NOT NULL DEFAULT 0," +
                    "show TEXT, status TEXT, PRIMARY KEY (account, jid))",
            "CREATE TABLE meta (account INTEGER NOT NULL, k TEXT NOT NULL, v TEXT, PRIMARY KEY (account, k))"
    };

    static final String MESSAGE_COLUMNS = "id, account, conv, grp, outgoing, sender, body, time, stanza_id, origin_id, server_id," +
            " edited, status, read, url, path, reply_id, reply_body, reply_author, error";
    static final String ORDER_OLDEST_FIRST = "time / 1000 ASC, id ASC";
    static final String ORDER_NEWEST_FIRST = "time / 1000 DESC, id DESC";

    private static XmppDatabase instance;

    public static synchronized XmppDatabase get(Context context) {
        if (instance == null) {
            instance = new XmppDatabase(context.getApplicationContext());
        }
        return instance;
    }

    private XmppDatabase(Context context) {
        super(context, "retrogram_xmpp.db", null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        for (String sql : SCHEMA) {
            db.execSQL(sql);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 is the first release of the schema.
    }

    private static String s(String v) {
        return v == null ? "" : v;
    }

    // ---------------------------------------------------------------------------------------------- messages

    private static ContentValues values(XmppMessage m) {
        ContentValues v = new ContentValues();
        v.put("account", m.accountId);
        v.put("conv", m.conversation);
        v.put("grp", m.groupChat ? 1 : 0);
        v.put("outgoing", m.outgoing ? 1 : 0);
        v.put("sender", m.sender);
        v.put("body", m.body);
        v.put("time", m.time);
        v.put("stanza_id", m.stanzaId);
        v.put("origin_id", m.originId);
        v.put("server_id", m.serverId);
        v.put("edited", m.edited ? 1 : 0);
        v.put("status", m.status);
        v.put("read", m.read ? 1 : 0);
        v.put("url", m.attachmentUrl);
        v.put("path", m.attachmentPath);
        v.put("reply_id", m.replyToId);
        v.put("reply_body", m.replyToBody);
        v.put("reply_author", m.replyToAuthor);
        v.put("error", m.error);
        return v;
    }

    private static XmppMessage message(Cursor c) {
        XmppMessage m = new XmppMessage();
        m.dbId = c.getLong(0);
        m.accountId = c.getLong(1);
        m.conversation = s(c.getString(2));
        m.groupChat = c.getInt(3) != 0;
        m.outgoing = c.getInt(4) != 0;
        m.sender = s(c.getString(5));
        m.body = s(c.getString(6));
        m.time = c.getLong(7);
        m.stanzaId = s(c.getString(8));
        m.originId = s(c.getString(9));
        m.serverId = s(c.getString(10));
        m.edited = c.getInt(11) != 0;
        m.status = c.getInt(12);
        m.read = c.getInt(13) != 0;
        m.attachmentUrl = s(c.getString(14));
        m.attachmentPath = s(c.getString(15));
        m.replyToId = s(c.getString(16));
        m.replyToBody = s(c.getString(17));
        m.replyToAuthor = s(c.getString(18));
        m.error = s(c.getString(19));
        return m;
    }

    @Override
    public synchronized long insertMessage(XmppMessage m) {
        long id = getWritableDatabase().insert("messages", null, values(m));
        m.dbId = id;
        return id;
    }

    @Override
    public synchronized void updateMessage(XmppMessage m) {
        getWritableDatabase().update("messages", values(m), "id = ?", new String[]{String.valueOf(m.dbId)});
    }

    @Override
    public synchronized void deleteMessage(long dbId) {
        getWritableDatabase().delete("messages", "id = ?", new String[]{String.valueOf(dbId)});
    }

    private XmppMessage findOne(String column, long accountId, String conversation, String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + MESSAGE_COLUMNS + " FROM messages WHERE account = ? AND conv = ? AND "
                + column + " = ? ORDER BY " + ORDER_NEWEST_FIRST + " LIMIT 1", new String[]{String.valueOf(accountId), conversation, value})) {
            return c.moveToFirst() ? message(c) : null;
        }
    }

    @Override
    public synchronized XmppMessage findByStanzaId(long accountId, String conversation, String stanzaId) {
        return findOne("stanza_id", accountId, conversation, stanzaId);
    }

    @Override
    public synchronized XmppMessage findByServerId(long accountId, String conversation, String serverId) {
        return findOne("server_id", accountId, conversation, serverId);
    }

    @Override
    public synchronized XmppMessage findByOriginId(long accountId, String conversation, String originId) {
        return findOne("origin_id", accountId, conversation, originId);
    }

    @Override
    public synchronized List<XmppMessage> messages(long accountId, String conversation, long beforeTime, int limit) {
        List<XmppMessage> out = new ArrayList<>();
        String where = "account = ? AND conv = ?" + (beforeTime > 0 ? " AND time < ?" : "");
        String[] args = beforeTime > 0
                ? new String[]{String.valueOf(accountId), conversation, String.valueOf(beforeTime)}
                : new String[]{String.valueOf(accountId), conversation};
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + MESSAGE_COLUMNS + " FROM messages WHERE " + where
                + " ORDER BY " + ORDER_NEWEST_FIRST + " LIMIT " + Math.max(1, limit), args)) {
            while (c.moveToNext()) {
                out.add(message(c));
            }
        }
        java.util.Collections.reverse(out);
        return out;
    }

    private long extremum(String fn, long accountId, String conversation) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + fn + "(time) FROM messages WHERE account = ? AND conv = ?",
                new String[]{String.valueOf(accountId), conversation})) {
            return c.moveToFirst() && !c.isNull(0) ? c.getLong(0) : 0;
        }
    }

    @Override
    public synchronized long oldestTime(long accountId, String conversation) {
        return extremum("MIN", accountId, conversation);
    }

    @Override
    public synchronized long newestTime(long accountId, String conversation) {
        return extremum("MAX", accountId, conversation);
    }

    @Override
    public synchronized List<XmppMessage> pendingOutgoing(long accountId) {
        List<XmppMessage> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + MESSAGE_COLUMNS + " FROM messages WHERE account = ? AND outgoing = 1 AND status = ? ORDER BY "
                + ORDER_OLDEST_FIRST, new String[]{String.valueOf(accountId), String.valueOf(XmppMessage.STATUS_PENDING)})) {
            while (c.moveToNext()) {
                out.add(message(c));
            }
        }
        return out;
    }

    @Override
    public synchronized void deleteConversation(long accountId, String jid) {
        SQLiteDatabase db = getWritableDatabase();
        String[] args = {String.valueOf(accountId), jid};
        db.delete("messages", "account = ? AND conv = ?", args);
        db.delete("conversations", "account = ? AND jid = ?", args);
    }

    // ---------------------------------------------------------------------------------------------- conversations

    private static final String CONVERSATION_COLUMNS = "account, jid, grp, title, last_body, last_time, last_out, unread, joined, subject, nick, password, muted, notify_all, pinned";

    private static XmppConversation conversation(Cursor c) {
        XmppConversation x = new XmppConversation();
        x.accountId = c.getLong(0);
        x.jid = s(c.getString(1));
        x.groupChat = c.getInt(2) != 0;
        x.title = s(c.getString(3));
        x.lastBody = s(c.getString(4));
        x.lastTime = c.getLong(5);
        x.lastOutgoing = c.getInt(6) != 0;
        x.unread = c.getInt(7);
        x.joined = c.getInt(8) != 0;
        x.subject = s(c.getString(9));
        x.nickname = s(c.getString(10));
        x.password = s(c.getString(11));
        x.muted = c.getInt(12) != 0;
        x.notifyAll = c.getInt(13) != 0;
        x.pinned = c.getInt(14) != 0;
        return x;
    }

    @Override
    public synchronized XmppConversation conversation(long accountId, String jid) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + CONVERSATION_COLUMNS + " FROM conversations WHERE account = ? AND jid = ?",
                new String[]{String.valueOf(accountId), jid})) {
            return c.moveToFirst() ? conversation(c) : null;
        }
    }

    @Override
    public synchronized void saveConversation(XmppConversation x) {
        ContentValues v = new ContentValues();
        v.put("account", x.accountId);
        v.put("jid", x.jid);
        v.put("grp", x.groupChat ? 1 : 0);
        v.put("title", x.title);
        v.put("last_body", x.lastBody);
        v.put("last_time", x.lastTime);
        v.put("last_out", x.lastOutgoing ? 1 : 0);
        v.put("unread", x.unread);
        v.put("joined", x.joined ? 1 : 0);
        v.put("subject", x.subject);
        v.put("nick", x.nickname);
        v.put("password", x.password);
        v.put("muted", x.muted ? 1 : 0);
        v.put("notify_all", x.notifyAll ? 1 : 0);
        v.put("pinned", x.pinned ? 1 : 0);
        getWritableDatabase().insertWithOnConflict("conversations", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    @Override
    public synchronized List<XmppConversation> conversations(long accountId) {
        List<XmppConversation> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + CONVERSATION_COLUMNS + " FROM conversations WHERE account = ? ORDER BY pinned DESC, last_time DESC",
                new String[]{String.valueOf(accountId)})) {
            while (c.moveToNext()) {
                out.add(conversation(c));
            }
        }
        return out;
    }

    @Override
    public synchronized void markRead(long accountId, String jid) {
        SQLiteDatabase db = getWritableDatabase();
        String[] args = {String.valueOf(accountId), jid};
        db.execSQL("UPDATE messages SET read = 1 WHERE account = ? AND conv = ? AND read = 0", args);
        db.execSQL("UPDATE conversations SET unread = 0 WHERE account = ? AND jid = ?", args);
    }

    @Override
    public synchronized List<XmppMessage> unreadIncoming(long accountId, String jid) {
        List<XmppMessage> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + MESSAGE_COLUMNS + " FROM messages WHERE account = ? AND conv = ? AND outgoing = 0 AND read = 0 ORDER BY "
                + ORDER_OLDEST_FIRST, new String[]{String.valueOf(accountId), jid})) {
            while (c.moveToNext()) {
                out.add(message(c));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------------------------------------- contacts

    @Override
    public synchronized void saveContacts(long accountId, List<XmppContact> contacts) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("contacts", "account = ?", new String[]{String.valueOf(accountId)});
            for (XmppContact x : contacts) {
                ContentValues v = new ContentValues();
                v.put("account", accountId);
                v.put("jid", x.jid);
                v.put("name", x.name);
                v.put("groups", new JSONArray(x.groups).toString());
                v.put("sub", x.subscription.name());
                v.put("pending", x.pendingOut ? 1 : 0);
                v.put("show", x.show.name());
                v.put("status", x.status);
                db.insertWithOnConflict("contacts", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public synchronized List<XmppContact> contacts(long accountId) {
        List<XmppContact> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT jid, name, groups, sub, pending, show, status FROM contacts WHERE account = ?",
                new String[]{String.valueOf(accountId)})) {
            while (c.moveToNext()) {
                XmppContact x = new XmppContact();
                x.accountId = accountId;
                x.jid = s(c.getString(0));
                x.name = s(c.getString(1));
                try {
                    JSONArray g = new JSONArray(s(c.getString(2)).isEmpty() ? "[]" : c.getString(2));
                    for (int i = 0; i < g.length(); i++) {
                        x.groups.add(g.getString(i));
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                x.subscription = XmppContact.Subscription.fromName(c.getString(3));
                x.pendingOut = c.getInt(4) != 0;
                // The cache is from the last session: nobody is known to be online until presence arrives.
                x.show = XmppShow.OFFLINE;
                x.status = "";
                out.add(x);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------------------------------------- meta

    @Override
    public synchronized String getMeta(long accountId, String key) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT v FROM meta WHERE account = ? AND k = ?",
                new String[]{String.valueOf(accountId), key})) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    @Override
    public synchronized void setMeta(long accountId, String key, String value) {
        ContentValues v = new ContentValues();
        v.put("account", accountId);
        v.put("k", key);
        v.put("v", value);
        getWritableDatabase().insertWithOnConflict("meta", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    @Override
    public synchronized void deleteAccount(long accountId) {
        SQLiteDatabase db = getWritableDatabase();
        String[] args = {String.valueOf(accountId)};
        db.delete("messages", "account = ?", args);
        db.delete("conversations", "account = ?", args);
        db.delete("contacts", "account = ?", args);
        db.delete("meta", "account = ?", args);
    }
}
