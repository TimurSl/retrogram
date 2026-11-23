package app.retrogram.xmpp;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.List;

import app.retrogram.xmpp.core.XmppAccountConfig;
import app.retrogram.xmpp.core.XmppShow;

/** Where XMPP accounts are kept: a JSON list in private preferences, passwords encrypted by {@link XmppSecrets}. */
public final class XmppAccounts {
    private static final String PREFS = "retrogram_xmpp";
    private static final String KEY_ACCOUNTS = "accounts";
    private static final String KEY_NEXT_ID = "next_id";
    private static final String KEY_BACKGROUND = "background_service";
    private static final String KEY_SELECTED = "selected_account";

    private XmppAccounts() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized List<XmppAccountConfig> list() {
        List<XmppAccountConfig> out = new ArrayList<>();
        String raw = prefs().getString(KEY_ACCOUNTS, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                out.add(fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            FileLog.e(e);
        }
        return out;
    }

    public static synchronized XmppAccountConfig get(long id) {
        for (XmppAccountConfig c : list()) {
            if (c.id == id) {
                return c;
            }
        }
        return null;
    }

    /** Stores the account; assigns an id to a new one. */
    public static synchronized XmppAccountConfig save(XmppAccountConfig config) {
        List<XmppAccountConfig> all = list();
        XmppAccountConfig copy = config.copy();
        if (copy.id == 0) {
            copy.id = prefs().getLong(KEY_NEXT_ID, 1);
            prefs().edit().putLong(KEY_NEXT_ID, copy.id + 1).apply();
            all.add(copy);
        } else {
            boolean found = false;
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).id == copy.id) {
                    all.set(i, copy);
                    found = true;
                }
            }
            if (!found) {
                all.add(copy);
            }
        }
        write(all);
        return copy;
    }

    public static synchronized void delete(long id) {
        List<XmppAccountConfig> all = list();
        all.removeIf(c -> c.id == id);
        write(all);
        SharedPreferences.Editor e = prefs().edit().remove("show_" + id).remove("status_" + id);
        if (prefs().getLong(KEY_SELECTED, 0) == id) {
            e.remove(KEY_SELECTED);
        }
        e.apply();
    }

    public static synchronized boolean hasAccount(String jid) {
        for (XmppAccountConfig c : list()) {
            if (c.jid.equalsIgnoreCase(jid)) {
                return true;
            }
        }
        return false;
    }

    private static void write(List<XmppAccountConfig> all) {
        JSONArray arr = new JSONArray();
        try {
            for (XmppAccountConfig c : all) {
                arr.put(toJson(c));
            }
        } catch (JSONException e) {
            FileLog.e(e);
            return;
        }
        prefs().edit().putString(KEY_ACCOUNTS, arr.toString()).apply();
    }

    private static JSONObject toJson(XmppAccountConfig c) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", c.id);
        o.put("jid", c.jid);
        o.put("pw", XmppSecrets.encrypt(c.password));
        o.put("host", c.host);
        o.put("port", c.port);
        o.put("security", c.security.name());
        o.put("resource", c.resource);
        o.put("nick", c.nickname);
        o.put("enabled", c.enabled);
        o.put("label", c.label);
        o.put("pin", c.pinnedCertificate);
        return o;
    }

    private static XmppAccountConfig fromJson(JSONObject o) {
        XmppAccountConfig c = new XmppAccountConfig();
        c.id = o.optLong("id");
        c.jid = o.optString("jid");
        c.password = XmppSecrets.decrypt(o.optString("pw"));
        c.host = o.optString("host");
        c.port = o.optInt("port");
        c.security = XmppAccountConfig.Security.fromName(o.optString("security"));
        c.resource = o.optString("resource");
        c.nickname = o.optString("nick");
        c.enabled = o.optBoolean("enabled", true);
        c.label = o.optString("label");
        c.pinnedCertificate = o.optString("pin");
        return c;
    }

    // --- per-account presence the user picked

    public static XmppShow show(long id) {
        return XmppShow.fromName(prefs().getString("show_" + id, XmppShow.ONLINE.name()));
    }

    public static String status(long id) {
        return prefs().getString("status_" + id, "");
    }

    public static void setPresence(long id, XmppShow show, String status) {
        prefs().edit().putString("show_" + id, show.name()).putString("status_" + id, status == null ? "" : status).apply();
    }

    // --- the XMPP account shown as the app's current account (drawer account switcher)

    /** The XMPP account the main screen shows instead of the Telegram chat list, or 0 when a Telegram account is shown. */
    public static long selected() {
        return prefs().getLong(KEY_SELECTED, 0);
    }

    public static void setSelected(long id) {
        // commit(), not apply(): the choice must survive the process being killed right after the switch.
        prefs().edit().putLong(KEY_SELECTED, id).commit();
    }

    // --- settings

    /** Keep the connections alive in the background with a foreground service (default on). */
    public static boolean backgroundService() {
        return prefs().getBoolean(KEY_BACKGROUND, true);
    }

    public static void setBackgroundService(boolean on) {
        prefs().edit().putBoolean(KEY_BACKGROUND, on).apply();
    }
}
