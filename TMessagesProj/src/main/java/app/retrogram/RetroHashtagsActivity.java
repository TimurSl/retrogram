package app.retrogram;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import org.telegram.messenger.HashtagSearchController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.HashtagActivity;
import org.telegram.ui.Components.HashtagHistoryView;
import org.telegram.ui.Components.LayoutHelper;

/**
 * "Hashtags+" from the chat list menu (retro mode): type a hashtag (or cashtag) or pick a recent one, then see the
 * posts and stories that carry it, on Telegram's own hashtag results page ({@link HashtagActivity}). Recent hashtags
 * are Telegram's shared hashtag search history, so they also show up in in-chat hashtag search.
 */
public class RetroHashtagsActivity extends BaseFragment {

    private HashtagHistoryView historyView;
    private ActionBarMenuItem searchItem;
    private boolean searchOpenedOnce;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.RetroHashtags));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        searchItem = menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchPressed(EditText editText) {
                open(editText.getText() != null ? editText.getText().toString() : null);
            }
        });
        searchItem.setSearchFieldHint(getString(R.string.SearchHashtagsHint));

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        historyView = new HashtagHistoryView(context, getResourceProvider(), currentAccount);
        historyView.setOnHashtagClickListener(this::open);
        frameLayout.addView(historyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = frameLayout;
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (historyView != null) {
            historyView.update();
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);
        if (isOpen && !backward && !searchOpenedOnce && searchItem != null) {
            searchOpenedOnce = true;
            searchItem.openSearch(true);
        }
    }

    /** "#tag", "tag", " #tag " → "#tag"; "$TON" stays a cashtag. Null when nothing usable was typed. */
    static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String tag = raw.trim().replace(" ", "");
        while (tag.startsWith("##")) {
            tag = tag.substring(1);
        }
        if (tag.isEmpty() || tag.equals("#") || tag.equals("$")) {
            return null;
        }
        if (!tag.startsWith("#") && !tag.startsWith("$")) {
            tag = "#" + tag;
        }
        return tag;
    }

    private void open(String raw) {
        String tag = normalize(raw);
        if (TextUtils.isEmpty(tag)) {
            return;
        }
        HashtagSearchController.getInstance(currentAccount).putToHistory(tag);
        presentFragment(new HashtagActivity(tag, getResourceProvider()));
    }
}
