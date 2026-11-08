// SPDX-License-Identifier: GPL-3.0-or-later
package app.retrogram

import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.helpers.MainTabsHelper
import xyz.nextalone.nagram.NaConfig

/**
 * Phase 1 shell switches (docs/plans/01-main-shell.md, WP 1.1).
 *
 * In retro mode the main shell is the 12.1.1 one: side drawer on, no bottom tab bar,
 * folder tabs under the action bar, search as an action-bar icon. Upstream code reads these NagramXF options in
 * many places, so instead of editing every reader we pin their *in-memory* values once at
 * cold start. Nothing is written to preferences: leaving retro mode restores the user's
 * stored choices. Call only after `NaConfig.init()` and [RetroConfig.captureColdStart].
 */
object RetroShell {

    @JvmStatic
    fun applyForcedConfig() {
        if (!RetroConfig.isRetroUi()) return
        NekoConfig.navigationDrawerEnabled.value = true
        NaConfig.mainTabsDisplayMode.value = MainTabsHelper.BOTTOM_BAR_MODE_HIDE
        NaConfig.foldersAtBottom.value = false
        // NagramXF option: the pill search field is hidden at rest and a search icon shows in the
        // action bar instead (12.1.1 behaviour). The pill only appears while searching.
        NaConfig.hideHomeSearchField.value = true
    }
}
