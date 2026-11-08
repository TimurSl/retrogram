// SPDX-License-Identifier: GPL-3.0-or-later
package app.retrogram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import app.retrogram.graph.ChatLockPolicy;
import app.retrogram.graph.ContactChangeDetector;
import app.retrogram.graph.ContactChangeDetector.Change;
import app.retrogram.graph.ContactChangeDetector.Kind;
import app.retrogram.graph.ContactChangeDetector.Snapshot;

import org.junit.Test;

import java.util.List;

public class ChatLockAndContactsTest {

    @Test
    public void unlockedChatRelocksAfterTimeout() {
        ChatLockPolicy p = new ChatLockPolicy(60_000, true);
        assertFalse(p.needsGate(1, 0));
        p.lock(1);
        assertTrue(p.needsGate(1, 0));
        p.onUnlocked(1, 1000);
        assertFalse(p.needsGate(1, 30_000));
        assertTrue(p.needsGate(1, 61_000));
        assertTrue("stays locked until unlocked again", p.needsGate(1, 62_000));
    }

    @Test
    public void backgroundRelocksOnlyWhenEnabled() {
        ChatLockPolicy on = new ChatLockPolicy(600_000, true);
        on.lock(1);
        on.onUnlocked(1, 0);
        on.onAppBackgrounded();
        assertTrue(on.needsGate(1, 10));
        ChatLockPolicy off = new ChatLockPolicy(600_000, false);
        off.lock(1);
        off.onUnlocked(1, 0);
        off.onAppBackgrounded();
        assertFalse(off.needsGate(1, 10));
    }

    @Test
    public void zeroTimeoutRelocksWhenChatClosedAndUnlockingUnlockedChatIsNoop() {
        ChatLockPolicy p = new ChatLockPolicy(0, false);
        p.onUnlocked(7, 0);
        assertFalse(p.needsGate(7, 1));
        p.lock(2);
        p.onUnlocked(2, 0);
        p.onChatClosed(2);
        assertTrue(p.needsGate(2, 1));
    }

    @Test
    public void previewsAreRedactedForLockedChatsEvenWhenUnlocked() {
        ChatLockPolicy p = new ChatLockPolicy(60_000, true);
        p.lock(1);
        p.onUnlocked(1, 0);
        assertTrue(p.shouldRedactPreview(1));
        assertFalse(p.shouldRedactPreview(2));
        p.removeLock(1);
        assertFalse(p.shouldRedactPreview(1));
    }

    private static Snapshot snap(boolean online, String name, String user, long photo, String phone) {
        return new Snapshot(online, name, user, photo, phone);
    }

    @Test
    public void firstSnapshotIsBaselineThenChangesAreReported() {
        ContactChangeDetector d = new ContactChangeDetector();
        d.watch(5);
        assertTrue(d.update(5, snap(false, "Ann", "ann", 1, "+1"), 0).isEmpty());
        List<Change> c = d.update(5, snap(true, "Anna", "ann", 2, "+1"), 10);
        assertEquals(3, c.size());
        assertEquals(Kind.ONLINE, c.get(0).kind);
        assertEquals(Kind.NAME, c.get(1).kind);
        assertEquals("Ann", c.get(1).oldValue);
        assertEquals("Anna", c.get(1).newValue);
        assertEquals(Kind.PHOTO, c.get(2).kind);
        assertTrue(d.update(5, snap(true, "Anna", "ann", 2, "+1"), 20).isEmpty());
    }

    @Test
    public void unwatchedUsersAreIgnoredAndRewatchStartsNewBaseline() {
        ContactChangeDetector d = new ContactChangeDetector();
        assertTrue(d.update(9, snap(true, "X", null, 0, null), 0).isEmpty());
        d.watch(9);
        d.update(9, snap(true, "X", null, 0, null), 0);
        d.unwatch(9);
        d.watch(9);
        assertTrue(d.update(9, snap(false, "Y", "y", 0, null), 5).isEmpty());
        List<Change> c = d.update(9, snap(false, "Y", "z", 0, "+2"), 6);
        assertEquals(2, c.size());
        assertEquals(Kind.USERNAME, c.get(0).kind);
        assertEquals(Kind.PHONE, c.get(1).kind);
    }
}
