// SPDX-License-Identifier: GPL-3.0-or-later
package app.retrogram.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Change detection of the special-contacts log (WP 6.6, docs/plans/06-graph-features.md).
 *
 * Pure logic, no Android dependencies. The app feeds it the user fields it already receives (from
 * `NotificationCenter.updateInterfaces` with the status/name/avatar/phone masks, no polling); it keeps the last
 * seen snapshot per watched user and returns what changed. SQLite log, notifications and the screen are separate work.
 * Only users the account already receives updates for can be seen; nothing here contacts any server.
 *
 * Designed from the plan, not from Graph Messenger; options will be reconciled with `docs/graph-spec/special-contacts.md`.
 */
public final class ContactChangeDetector {

    public enum Kind { ONLINE, OFFLINE, NAME, USERNAME, PHOTO, PHONE }

    public static final class Snapshot {
        public final boolean online;
        public final String name;
        public final String username;
        /** Any stable id of the current profile photo; 0 means none. */
        public final long photoId;
        public final String phone;

        public Snapshot(boolean online, String name, String username, long photoId, String phone) {
            this.online = online;
            this.name = name;
            this.username = username;
            this.photoId = photoId;
            this.phone = phone;
        }
    }

    public static final class Change {
        public final long userId;
        public final Kind kind;
        public final String oldValue;
        public final String newValue;
        public final long atMs;

        Change(long userId, Kind kind, String oldValue, String newValue, long atMs) {
            this.userId = userId;
            this.kind = kind;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.atMs = atMs;
        }
    }

    private final Set<Long> watched = new HashSet<>();
    private final Map<Long, Snapshot> last = new HashMap<>();

    public void watch(long userId) {
        watched.add(userId);
    }

    public void unwatch(long userId) {
        watched.remove(userId);
        last.remove(userId);
    }

    /**
     * Compares with the previous snapshot. The first snapshot of a user is a baseline and yields no changes,
     * so enabling the watch does not log everything as "changed".
     */
    public List<Change> update(long userId, Snapshot now, long atMs) {
        final List<Change> out = new ArrayList<>();
        if (!watched.contains(userId)) {
            return out;
        }
        final Snapshot before = last.put(userId, now);
        if (before == null) {
            return out;
        }
        if (before.online != now.online) {
            out.add(new Change(userId, now.online ? Kind.ONLINE : Kind.OFFLINE, null, null, atMs));
        }
        if (!Objects.equals(before.name, now.name)) {
            out.add(new Change(userId, Kind.NAME, before.name, now.name, atMs));
        }
        if (!Objects.equals(before.username, now.username)) {
            out.add(new Change(userId, Kind.USERNAME, before.username, now.username, atMs));
        }
        if (before.photoId != now.photoId) {
            out.add(new Change(userId, Kind.PHOTO, String.valueOf(before.photoId), String.valueOf(now.photoId), atMs));
        }
        if (!Objects.equals(before.phone, now.phone)) {
            out.add(new Change(userId, Kind.PHONE, before.phone, now.phone, atMs));
        }
        return out;
    }
}
