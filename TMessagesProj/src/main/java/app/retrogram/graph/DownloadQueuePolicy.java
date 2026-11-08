// SPDX-License-Identifier: GPL-3.0-or-later
package app.retrogram.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scheduling rules of the download manager (WP 6.5, docs/plans/06-graph-features.md).
 *
 * Pure logic, no Android dependencies: given the queue, the network type and the clock, it says which entries
 * may run now. The actual transfers stay with `FileLoader`; the screen, the drawer entry and saving to Downloads
 * (SAF/MediaStore) are separate work and not done yet. Pause/resume is a user state held here; whether `FileLoader`
 * can resume a cancelled transfer from its partial file is to be checked before wiring (it has `cancelLoadFile`
 * and an internal `FileLoadOperation.pause()`, but no public pause).
 *
 * Designed from the plan, not from Graph Messenger; options will be reconciled with `docs/graph-spec/download-manager.md`.
 */
public final class DownloadQueuePolicy {

    public enum Network { WIFI, MOBILE, ROAMING, NONE }

    public enum State { QUEUED, RUNNING, PAUSED, DONE, CANCELLED }

    public static final class Entry {
        public final String id;
        public final long sizeBytes;
        public State state = State.QUEUED;
        /** Larger runs first; ties keep insertion order. */
        public int priority;

        Entry(String id, long sizeBytes, int priority) {
            this.id = id;
            this.sizeBytes = sizeBytes;
            this.priority = priority;
        }
    }

    public static final class Settings {
        public int maxParallel = 3;
        public boolean wifiOnly = false;
        public boolean blockRoaming = true;
        /** Allowed window in minutes of the day; start == end means "any time". May wrap midnight. */
        public int windowStartMinute = 0;
        public int windowEndMinute = 0;
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Settings settings;

    public DownloadQueuePolicy(Settings settings) {
        this.settings = settings;
    }

    public Entry add(String id, long sizeBytes, int priority) {
        final Entry e = new Entry(id, sizeBytes, priority);
        entries.put(id, e);
        return e;
    }

    public void pause(String id) {
        final Entry e = entries.get(id);
        if (e != null && (e.state == State.QUEUED || e.state == State.RUNNING)) {
            e.state = State.PAUSED;
        }
    }

    public void resume(String id) {
        final Entry e = entries.get(id);
        if (e != null && e.state == State.PAUSED) {
            e.state = State.QUEUED;
        }
    }

    public void cancel(String id) {
        final Entry e = entries.get(id);
        if (e != null && e.state != State.DONE) {
            e.state = State.CANCELLED;
        }
    }

    public void markDone(String id) {
        final Entry e = entries.get(id);
        if (e != null) {
            e.state = State.DONE;
        }
    }

    private boolean networkAllows(Network network) {
        switch (network) {
            case WIFI:
                return true;
            case MOBILE:
                return !settings.wifiOnly;
            case ROAMING:
                return !settings.wifiOnly && !settings.blockRoaming;
            default:
                return false;
        }
    }

    private boolean inWindow(int minute) {
        final int s = settings.windowStartMinute;
        final int e = settings.windowEndMinute;
        if (s == e) {
            return true;
        }
        return s < e ? (minute >= s && minute < e) : (minute >= s || minute < e);
    }

    /**
     * Applies the rules and returns what to start now (already marked RUNNING). If the network or the window
     * no longer allows downloads, running entries are put back to QUEUED (so the caller must stop them) and the
     * result is empty.
     */
    public List<Entry> next(Network network, int minuteOfDay) {
        final List<Entry> started = new ArrayList<>();
        final boolean allowed = networkAllows(network) && inWindow(minuteOfDay);
        int running = 0;
        for (Entry e : entries.values()) {
            if (e.state == State.RUNNING) {
                if (allowed) {
                    running++;
                } else {
                    e.state = State.QUEUED;
                }
            }
        }
        if (!allowed) {
            return started;
        }
        final List<Entry> candidates = new ArrayList<>();
        for (Entry e : entries.values()) {
            if (e.state == State.QUEUED) {
                candidates.add(e);
            }
        }
        // stable sort keeps insertion order for equal priorities
        candidates.sort((a, b) -> Integer.compare(b.priority, a.priority));
        for (Entry e : candidates) {
            if (running >= settings.maxParallel) {
                break;
            }
            e.state = State.RUNNING;
            running++;
            started.add(e);
        }
        return started;
    }
}
