// SPDX-License-Identifier: GPL-3.0-or-later
package app.retrogram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import app.retrogram.graph.DownloadQueuePolicy;
import app.retrogram.graph.DownloadQueuePolicy.Network;
import app.retrogram.graph.DownloadQueuePolicy.Settings;
import app.retrogram.graph.DownloadQueuePolicy.State;

import org.junit.Test;

public class DownloadQueuePolicyTest {

    @Test
    public void respectsParallelLimitAndPriorityThenInsertionOrder() {
        Settings s = new Settings();
        s.maxParallel = 2;
        DownloadQueuePolicy q = new DownloadQueuePolicy(s);
        q.add("a", 1, 0);
        q.add("b", 1, 5);
        q.add("c", 1, 5);
        assertEquals("[b, c]", q.next(Network.WIFI, 0).stream().map(e -> e.id).collect(java.util.stream.Collectors.toList()).toString());
        assertTrue(q.next(Network.WIFI, 0).isEmpty());
        q.markDone("b");
        assertEquals("a", q.next(Network.WIFI, 0).get(0).id);
    }

    @Test
    public void wifiOnlyAndRoamingBlockAndRequeueRunningEntries() {
        Settings s = new Settings();
        s.wifiOnly = true;
        DownloadQueuePolicy q = new DownloadQueuePolicy(s);
        DownloadQueuePolicy.Entry e = q.add("a", 1, 0);
        assertTrue(q.next(Network.MOBILE, 0).isEmpty());
        assertEquals(1, q.next(Network.WIFI, 0).size());
        assertEquals(State.RUNNING, e.state);
        assertTrue(q.next(Network.MOBILE, 0).isEmpty());
        assertEquals("running entry goes back to the queue when the network stops allowing it", State.QUEUED, e.state);
        assertTrue(q.next(Network.NONE, 0).isEmpty());
        s.wifiOnly = false;
        assertTrue("roaming stays blocked by default", q.next(Network.ROAMING, 0).isEmpty());
        s.blockRoaming = false;
        assertEquals(1, q.next(Network.ROAMING, 0).size());
    }

    @Test
    public void scheduledWindowWrapsMidnight() {
        Settings s = new Settings();
        s.windowStartMinute = 23 * 60;
        s.windowEndMinute = 6 * 60;
        DownloadQueuePolicy q = new DownloadQueuePolicy(s);
        q.add("a", 1, 0);
        assertTrue(q.next(Network.WIFI, 12 * 60).isEmpty());
        assertEquals(1, q.next(Network.WIFI, 2 * 60).size());
    }

    @Test
    public void pausedAndCancelledEntriesDoNotRunAndResumeRequeues() {
        DownloadQueuePolicy q = new DownloadQueuePolicy(new Settings());
        q.add("a", 1, 0);
        q.add("b", 1, 0);
        q.pause("a");
        q.cancel("b");
        assertTrue(q.next(Network.WIFI, 0).isEmpty());
        q.resume("a");
        q.resume("b");
        assertEquals("cancelled entries cannot be resumed", "a", q.next(Network.WIFI, 0).get(0).id);
    }
}
