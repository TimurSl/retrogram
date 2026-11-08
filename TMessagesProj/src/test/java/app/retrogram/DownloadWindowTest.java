// SPDX-License-Identifier: GPL-3.0-or-later
package app.retrogram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import app.retrogram.graph.DownloadWindow;

import org.junit.Test;

public class DownloadWindowTest {

    @Test
    public void simpleWindow() {
        assertTrue(DownloadWindow.contains(2 * 60, 1, 6));
        assertFalse(DownloadWindow.contains(6 * 60, 1, 6));
        assertFalse(DownloadWindow.contains(30, 1, 6));
        assertTrue(DownloadWindow.contains(60, 1, 6));
    }

    @Test
    public void windowWrappingMidnightAndAllDay() {
        assertTrue(DownloadWindow.contains(23 * 60 + 30, 22, 7));
        assertTrue(DownloadWindow.contains(3 * 60, 22, 7));
        assertFalse(DownloadWindow.contains(12 * 60, 22, 7));
        assertTrue(DownloadWindow.contains(12 * 60, 5, 5));
    }

    @Test
    public void millisUntilOpen() {
        assertEquals(0, DownloadWindow.millisUntilOpen(2 * 60, 0, 1, 6));
        assertEquals(60 * 60_000L, DownloadWindow.millisUntilOpen(0, 0, 1, 6));
        assertEquals(60 * 60_000L - 15_000L, DownloadWindow.millisUntilOpen(0, 15, 1, 6));
        assertEquals(750 * 60_000L, DownloadWindow.millisUntilOpen(12 * 60 + 30, 0, 1, 6)); // 12:30 -> 01:00 next day
        assertEquals(30 * 60_000L, DownloadWindow.millisUntilOpen(21 * 60 + 30, 0, 22, 7));
    }
}
