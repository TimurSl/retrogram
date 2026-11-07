package app.retrogram

import android.os.Bundle
import org.telegram.messenger.UserConfig
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ProfileActivity
import org.telegram.ui.SettingsActivity

/**
 * One place that decides which fragment "Settings" is (docs/plans/03-profile-settings.md, WP 3.5).
 * Default: upstream's [SettingsActivity]. With [RetroConfig.classicSettings]: the 12.1.1 page, which is
 * [ProfileActivity] for the own user without `my_profile` (its settings rows are only built in that mode).
 */
object RetroNav {

    @JvmStatic
    fun settingsFragment(): BaseFragment {
        if (!RetroConfig.isClassicSettings()) {
            return SettingsActivity()
        }
        val args = Bundle()
        args.putLong("user_id", UserConfig.getInstance(UserConfig.selectedAccount).clientUserId)
        return ProfileActivity(args)
    }
}
