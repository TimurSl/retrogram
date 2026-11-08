// SPDX-License-Identifier: GPL-3.0-or-later
package app.retrogram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class RetroThemeColorsTest {

    // Off the device the preference store is unavailable, which must read as "classic colors off" (fail safe).
    @Test
    public void themeAssetPathIsUnchangedWhenClassicColorsAreOff() {
        assertEquals("day.attheme", RetroThemeColors.themeAssetPath("day.attheme"));
        assertEquals("amoled.attheme", RetroThemeColors.themeAssetPath("amoled.attheme"));
        assertNull(RetroThemeColors.themeAssetPath(null));
    }
}
