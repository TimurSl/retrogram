package app.retrogram.xmpp.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.InputType;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.ArrayList;
import java.util.List;

import app.retrogram.xmpp.XmppAccounts;
import app.retrogram.xmpp.XmppManager;
import app.retrogram.xmpp.core.Callback;
import app.retrogram.xmpp.core.Jids;
import app.retrogram.xmpp.core.XmppContact;
import app.retrogram.xmpp.core.XmppController;
import app.retrogram.xmpp.core.XmppError;
import app.retrogram.xmpp.core.XmppShow;

/** Dialogs that more than one XMPP screen opens. */
final class XmppDialogs {
    private XmppDialogs() {
    }

    interface Done {
        void run();
    }

    static Callback<Void> reportErrors(BaseFragment f, Done ok) {
        return new Callback<Void>() {
            @Override
            public void onResult(Void v) {
                if (ok != null) {
                    org.telegram.messenger.AndroidUtilities.runOnUIThread(ok::run);
                }
            }

            @Override
            public void onError(XmppError error) {
                org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> XmppUi.error(f, error));
            }
        };
    }

    /** Picks one of the configured accounts, or returns it at once when there is only one. */
    static void pickAccount(BaseFragment f, long preferred, java.util.function.Consumer<XmppController> then) {
        List<XmppController> all = new ArrayList<>();
        for (XmppController c : XmppManager.get().controllers()) {
            if (c.config().enabled) {
                all.add(c);
            }
        }
        if (all.isEmpty()) {
            XmppUi.toast(f, LocaleController.getString(R.string.XmppNoAccountEnabled));
            return;
        }
        for (XmppController c : all) {
            if (c.accountId() == preferred) {
                then.accept(c);
                return;
            }
        }
        if (all.size() == 1) {
            then.accept(all.get(0));
            return;
        }
        Context context = f.getParentActivity();
        if (context == null) {
            return;
        }
        CharSequence[] names = new CharSequence[all.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = all.get(i).config().displayName();
        }
        AlertDialog.Builder b = new AlertDialog.Builder(context, f.getResourceProvider());
        b.setTitle(LocaleController.getString(R.string.XmppChooseAccount));
        b.setItems(names, (d, which) -> then.accept(all.get(which)));
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        f.showDialog(b.create());
    }

    static void addContact(BaseFragment f, XmppController c, String presetJid) {
        XmppUi.form(f, LocaleController.getString(R.string.XmppAddContact),
                new String[]{LocaleController.getString(R.string.XmppAddressHint), LocaleController.getString(R.string.XmppNameOptionalHint)},
                new String[]{presetJid, ""},
                new int[]{InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS},
                R.string.XmppAdd, values -> {
                    String jid = Jids.parseUserInput(values[0]);
                    if (jid == null) {
                        XmppUi.toast(f, LocaleController.getString(R.string.XmppErrorInvalid));
                        return false;
                    }
                    c.addContact(jid, values[1], null, reportErrors(f, () -> XmppUi.toast(f, LocaleController.formatString(R.string.XmppRequestSent, jid))));
                    return true;
                });
    }

    static void joinRoom(BaseFragment f, XmppController c, String presetRoom, boolean create, java.util.function.Consumer<String> opened) {
        XmppUi.form(f, LocaleController.getString(create ? R.string.XmppCreateGroup : R.string.XmppJoinGroup),
                new String[]{LocaleController.getString(R.string.XmppRoomHint), LocaleController.getString(R.string.XmppNicknameHint), LocaleController.getString(R.string.XmppRoomPasswordHint)},
                new String[]{presetRoom, c.config().effectiveNickname(), ""},
                new int[]{InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_CLASS_TEXT, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD},
                create ? R.string.XmppCreate : R.string.XmppJoin, values -> {
                    String room = Jids.parseUserInput(values[0]);
                    if (room == null) {
                        XmppUi.toast(f, LocaleController.getString(R.string.XmppErrorInvalid));
                        return false;
                    }
                    String nick = values[1].isEmpty() ? c.config().effectiveNickname() : values[1];
                    AlertDialog progress = new AlertDialog(f.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
                    progress.setCanCancel(false);
                    progress.show();
                    c.joinRoom(room, nick, values[2], create, true, new Callback<Void>() {
                        @Override
                        public void onResult(Void v) {
                            org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                                progress.dismiss();
                                if (opened != null) {
                                    opened.accept(room);
                                }
                            });
                        }

                        @Override
                        public void onError(XmppError error) {
                            org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                                progress.dismiss();
                                XmppUi.error(f, error);
                            });
                        }
                    });
                    return true;
                });
    }

    static void newChat(BaseFragment f, XmppController c, java.util.function.Consumer<String> opened) {
        XmppUi.input(f, LocaleController.getString(R.string.XmppNewChat), LocaleController.getString(R.string.XmppAddressHint), "", R.string.XmppOpen, text -> {
            String jid = Jids.parseUserInput(text);
            if (jid == null) {
                XmppUi.toast(f, LocaleController.getString(R.string.XmppErrorInvalid));
                return;
            }
            opened.accept(jid);
        });
    }

    static void setStatus(BaseFragment f, XmppController c) {
        Context context = f.getParentActivity();
        if (context == null) {
            return;
        }
        XmppShow[] shows = {XmppShow.ONLINE, XmppShow.CHAT, XmppShow.AWAY, XmppShow.XA, XmppShow.DND};
        CharSequence[] items = new CharSequence[shows.length];
        for (int i = 0; i < shows.length; i++) {
            items[i] = XmppUi.showName(shows[i]);
        }
        AlertDialog.Builder b = new AlertDialog.Builder(context, f.getResourceProvider());
        b.setTitle(LocaleController.getString(R.string.XmppSetStatus));
        b.setItems(items, (d, which) -> {
            XmppShow show = shows[which];
            XmppUi.input(f, LocaleController.getString(R.string.XmppStatusMessage), LocaleController.getString(R.string.XmppStatusMessageHint),
                    XmppAccounts.status(c.accountId()), R.string.Save, text -> XmppManager.get().setPresence(c.accountId(), show, text));
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        f.showDialog(b.create());
    }

    static void copy(Context context, String text) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("xmpp", text));
        }
    }

    /** What can be done with a person: open, rename, remove, block, copy. */
    static void contactMenu(BaseFragment f, XmppController c, String jid, Runnable openChat) {
        Context context = f.getParentActivity();
        if (context == null) {
            return;
        }
        XmppContact contact = c.contact(jid);
        boolean blocked = c.blocked().contains(jid);
        List<CharSequence> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (openChat != null) {
            items.add(LocaleController.getString(R.string.XmppOpenChat));
            actions.add(openChat);
        }
        if (contact != null) {
            items.add(LocaleController.getString(R.string.XmppRename));
            actions.add(() -> XmppUi.input(f, LocaleController.getString(R.string.XmppRename), LocaleController.getString(R.string.XmppNameOptionalHint),
                    contact.name, R.string.Save, text -> c.renameContact(jid, text, reportErrors(f, null))));
            items.add(LocaleController.getString(R.string.XmppGroups));
            actions.add(() -> XmppUi.input(f, LocaleController.getString(R.string.XmppGroups), LocaleController.getString(R.string.XmppGroupsHint),
                    android.text.TextUtils.join(", ", contact.groups), R.string.Save, text -> {
                        List<String> groups = new ArrayList<>();
                        for (String g : text.split(",")) {
                            if (!g.trim().isEmpty() && !groups.contains(g.trim())) {
                                groups.add(g.trim());
                            }
                        }
                        c.session().setContactGroups(jid, groups, reportErrors(f, null));
                    }));
            items.add(LocaleController.getString(R.string.XmppRemoveContact));
            actions.add(() -> confirm(f, LocaleController.formatString(R.string.XmppRemoveContactQuestion, contact.displayName()), R.string.XmppRemove,
                    () -> c.removeContact(jid, reportErrors(f, null))));
        } else {
            items.add(LocaleController.getString(R.string.XmppAddContact));
            actions.add(() -> addContact(f, c, jid));
        }
        items.add(LocaleController.getString(blocked ? R.string.XmppUnblock : R.string.XmppBlock));
        actions.add(() -> c.session().block(jid, !blocked, reportErrors(f, null)));
        items.add(LocaleController.getString(R.string.XmppCopyAddress));
        actions.add(() -> {
            copy(context, jid);
            XmppUi.toast(f, LocaleController.getString(R.string.XmppCopied));
        });
        AlertDialog.Builder b = new AlertDialog.Builder(context, f.getResourceProvider());
        b.setTitle(contact != null ? contact.displayName() : jid);
        b.setItems(items.toArray(new CharSequence[0]), (d, which) -> actions.get(which).run());
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        f.showDialog(b.create());
    }

    static void confirm(BaseFragment f, String question, int positive, Runnable onYes) {
        Context context = f.getParentActivity();
        if (context == null) {
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(context, f.getResourceProvider());
        b.setMessage(question);
        b.setPositiveButton(LocaleController.getString(positive), (d, w) -> onYes.run());
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        f.showDialog(b.create());
    }
}
