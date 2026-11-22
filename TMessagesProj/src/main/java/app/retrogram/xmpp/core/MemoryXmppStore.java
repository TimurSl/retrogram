package app.retrogram.xmpp.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** In-memory {@link XmppStore}. Used by tests and as a fallback when the database cannot be opened. */
public class MemoryXmppStore implements XmppStore {

    private long nextId = 1;
    private final List<XmppMessage> messages = new ArrayList<>();
    private final Map<String, XmppConversation> conversations = new HashMap<>();
    private final Map<Long, List<XmppContact>> contacts = new HashMap<>();
    private final Map<String, String> meta = new HashMap<>();

    private static String key(long accountId, String jid) {
        return accountId + "/" + jid;
    }

    @Override
    public synchronized long insertMessage(XmppMessage m) {
        m.dbId = nextId++;
        messages.add(m.copy());
        return m.dbId;
    }

    @Override
    public synchronized void updateMessage(XmppMessage m) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).dbId == m.dbId) {
                messages.set(i, m.copy());
                return;
            }
        }
    }

    @Override
    public synchronized void deleteMessage(long dbId) {
        for (Iterator<XmppMessage> it = messages.iterator(); it.hasNext(); ) {
            if (it.next().dbId == dbId) {
                it.remove();
                return;
            }
        }
    }

    private XmppMessage find(long accountId, String conversation, String value, int field) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        XmppMessage best = null;
        for (XmppMessage m : messages) {
            if (m.accountId != accountId || !m.conversation.equals(conversation)) {
                continue;
            }
            String v = field == 0 ? m.stanzaId : field == 1 ? m.serverId : m.originId;
            if (value.equals(v) && (best == null || m.time >= best.time)) {
                best = m;
            }
        }
        return best == null ? null : best.copy();
    }

    @Override
    public synchronized XmppMessage findByStanzaId(long accountId, String conversation, String stanzaId) {
        return find(accountId, conversation, stanzaId, 0);
    }

    @Override
    public synchronized XmppMessage findByServerId(long accountId, String conversation, String serverId) {
        return find(accountId, conversation, serverId, 1);
    }

    @Override
    public synchronized XmppMessage findByOriginId(long accountId, String conversation, String originId) {
        return find(accountId, conversation, originId, 2);
    }

    private List<XmppMessage> of(long accountId, String conversation) {
        List<XmppMessage> out = new ArrayList<>();
        for (XmppMessage m : messages) {
            if (m.accountId == accountId && m.conversation.equals(conversation)) {
                out.add(m);
            }
        }
        Collections.sort(out, Comparator.<XmppMessage>comparingLong(m -> m.time / 1000).thenComparingLong(m -> m.dbId));
        return out;
    }

    @Override
    public synchronized List<XmppMessage> messages(long accountId, String conversation, long beforeTime, int limit) {
        List<XmppMessage> all = of(accountId, conversation);
        List<XmppMessage> out = new ArrayList<>();
        for (XmppMessage m : all) {
            if (beforeTime == 0 || m.time < beforeTime) {
                out.add(m.copy());
            }
        }
        if (out.size() > limit) {
            out = new ArrayList<>(out.subList(out.size() - limit, out.size()));
        }
        return out;
    }

    @Override
    public synchronized long oldestTime(long accountId, String conversation) {
        List<XmppMessage> all = of(accountId, conversation);
        return all.isEmpty() ? 0 : all.get(0).time;
    }

    @Override
    public synchronized long newestTime(long accountId, String conversation) {
        List<XmppMessage> all = of(accountId, conversation);
        return all.isEmpty() ? 0 : all.get(all.size() - 1).time;
    }

    @Override
    public synchronized List<XmppMessage> pendingOutgoing(long accountId) {
        List<XmppMessage> out = new ArrayList<>();
        for (XmppMessage m : messages) {
            if (m.accountId == accountId && m.outgoing && m.status == XmppMessage.STATUS_PENDING) {
                out.add(m.copy());
            }
        }
        Collections.sort(out, Comparator.<XmppMessage>comparingLong(m -> m.time).thenComparingLong(m -> m.dbId));
        return out;
    }

    @Override
    public synchronized void deleteConversation(long accountId, String jid) {
        for (Iterator<XmppMessage> it = messages.iterator(); it.hasNext(); ) {
            XmppMessage m = it.next();
            if (m.accountId == accountId && m.conversation.equals(jid)) {
                it.remove();
            }
        }
        conversations.remove(key(accountId, jid));
    }

    @Override
    public synchronized XmppConversation conversation(long accountId, String jid) {
        XmppConversation c = conversations.get(key(accountId, jid));
        return c == null ? null : c.copy();
    }

    @Override
    public synchronized void saveConversation(XmppConversation c) {
        conversations.put(key(c.accountId, c.jid), c.copy());
    }

    @Override
    public synchronized List<XmppConversation> conversations(long accountId) {
        List<XmppConversation> out = new ArrayList<>();
        for (XmppConversation c : conversations.values()) {
            if (c.accountId == accountId) {
                out.add(c.copy());
            }
        }
        Collections.sort(out, (a, b) -> {
            if (a.pinned != b.pinned) {
                return a.pinned ? -1 : 1;
            }
            return Long.compare(b.lastTime, a.lastTime);
        });
        return out;
    }

    @Override
    public synchronized void markRead(long accountId, String jid) {
        for (XmppMessage m : messages) {
            if (m.accountId == accountId && m.conversation.equals(jid)) {
                m.read = true;
            }
        }
        XmppConversation c = conversations.get(key(accountId, jid));
        if (c != null) {
            c.unread = 0;
        }
    }

    @Override
    public synchronized List<XmppMessage> unreadIncoming(long accountId, String jid) {
        List<XmppMessage> out = new ArrayList<>();
        for (XmppMessage m : of(accountId, jid)) {
            if (!m.outgoing && !m.read) {
                out.add(m.copy());
            }
        }
        return out;
    }

    @Override
    public synchronized void saveContacts(long accountId, List<XmppContact> list) {
        contacts.put(accountId, new ArrayList<>(list));
    }

    @Override
    public synchronized List<XmppContact> contacts(long accountId) {
        List<XmppContact> l = contacts.get(accountId);
        return l == null ? new ArrayList<>() : new ArrayList<>(l);
    }

    @Override
    public synchronized String getMeta(long accountId, String key) {
        return meta.get(key(accountId, key));
    }

    @Override
    public synchronized void setMeta(long accountId, String key, String value) {
        meta.put(key(accountId, key), value);
    }

    @Override
    public synchronized void deleteAccount(long accountId) {
        for (Iterator<XmppMessage> it = messages.iterator(); it.hasNext(); ) {
            if (it.next().accountId == accountId) {
                it.remove();
            }
        }
        conversations.values().removeIf(c -> c.accountId == accountId);
        contacts.remove(accountId);
        meta.keySet().removeIf(k -> k.startsWith(accountId + "/"));
    }
}
