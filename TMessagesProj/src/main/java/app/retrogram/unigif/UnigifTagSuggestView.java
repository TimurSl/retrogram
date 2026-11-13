package app.retrogram.unigif;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import app.retrogram.unigif.UnigifModels.Tag;

/**
 * The row of hashtag chips under the GIF search field in UniGif mode (docs/plans/08-unigif.md, WP U.5).
 * Field empty and focused: the recently used tags. While typing: tags starting with the word being typed.
 * Hidden when there is nothing to show; errors hide it silently.
 */
@SuppressLint("ViewConstructor")
public class UnigifTagSuggestView extends RecyclerListView {

    public interface Delegate {
        void onTagClick(Tag tag);

        /** The row appeared or went away; the owner makes room for {@link #HEIGHT_DP}. */
        void onShownChanged(boolean shown);
    }

    public static final int HEIGHT_DP = 36;
    private static final int DEBOUNCE_MS = 250;
    /** Cache key of the recent tags (a prefix is never empty). */
    private static final String KEY_RECENT = "";

    private final Delegate delegate;
    private final ArrayList<Tag> tags = new ArrayList<>();
    private final HashMap<String, List<Tag>> cache = new HashMap<>();
    private final ChipAdapter adapter = new ChipAdapter();

    /** What the row should show now: null = nothing, {@link #KEY_RECENT}, or a prefix. */
    private String wantedKey;
    private okhttp3.Call call;
    private Runnable pending;
    private boolean shown;

    private int chipColor;
    private int pressedColor;
    private int textColor;
    private int countColor;

    public UnigifTagSuggestView(Context context, Theme.ResourcesProvider resourcesProvider, Delegate delegate) {
        super(context, resourcesProvider);
        this.delegate = delegate;
        setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        setItemAnimator(null);
        setClipToPadding(false);
        setPadding(dp(10), 0, dp(10), 0);
        setOverScrollMode(OVER_SCROLL_NEVER);
        addItemDecoration(new ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull State state) {
                outRect.right = parent.getChildAdapterPosition(view) == tags.size() - 1 ? 0 : dp(6);
            }
        });
        setAdapter(adapter);
        setVisibility(GONE);
    }

    /** {@code chip}: chip background; {@code text}: the tag; {@code count}: the GIF count next to it. */
    @SuppressLint("NotifyDataSetChanged")
    public void setColors(int chip, int pressed, int text, int count) {
        chipColor = chip;
        pressedColor = pressed;
        textColor = text;
        countColor = count;
        adapter.notifyDataSetChanged();
    }

    /**
     * Tells the row what the search field holds. {@code active} = UniGif mode; {@code focused} = the field has
     * the keyboard (the recent tags are offered only then).
     */
    public void update(String text, boolean active, boolean focused) {
        String key = null;
        if (active) {
            if (text == null || text.isEmpty()) {
                key = focused ? KEY_RECENT : null;
            } else {
                final String word = UnigifQuery.lastWord(text);
                key = word.isEmpty() ? null : word;
            }
        }
        if (key == null ? wantedKey == null : key.equals(wantedKey)) {
            return;
        }
        cancelLoad();
        wantedKey = key;
        if (key == null) {
            show(null);
            return;
        }
        final List<Tag> cached = cache.get(key);
        if (cached != null) {
            show(cached);
            return;
        }
        if (key.equals(KEY_RECENT)) {
            load(key);
        } else {
            // The chips of the previous prefix stay until the answer for this one arrives.
            final String prefix = key;
            AndroidUtilities.runOnUIThread(pending = () -> {
                pending = null;
                load(prefix);
            }, DEBOUNCE_MS);
        }
    }

    /** Forgets everything (provider or server changed, panel reopened). */
    public void reset() {
        cancelLoad();
        cache.clear();
        wantedKey = null;
        show(null);
    }

    private void cancelLoad() {
        if (pending != null) {
            AndroidUtilities.cancelRunOnUIThread(pending);
            pending = null;
        }
        if (call != null) {
            call.cancel();
            call = null;
        }
    }

    private void load(final String key) {
        final UnigifClient.Callback<List<Tag>> callback = (result, error) -> {
            if (!key.equals(wantedKey)) {
                return; // stale: the field moved on
            }
            call = null;
            if (error != null || result == null) {
                show(null);
                return;
            }
            cache.put(key, result);
            show(result);
        };
        final okhttp3.Call started = key.equals(KEY_RECENT) ? UnigifClient.suggestTags(callback) : UnigifClient.listTags(key, callback);
        if (key.equals(wantedKey)) {
            call = started;
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void show(List<Tag> list) {
        tags.clear();
        if (list != null) {
            for (Tag tag : list) {
                if (tag != null && !UnigifQuery.tagName(tag.name).isEmpty()) {
                    tags.add(tag);
                }
            }
        }
        adapter.notifyDataSetChanged();
        if (!tags.isEmpty()) {
            scrollToPosition(0);
        }
        final boolean show = !tags.isEmpty();
        if (show != shown) {
            shown = show;
            setVisibility(show ? VISIBLE : GONE);
            delegate.onShownChanged(show);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelLoad();
        // A load dropped here would otherwise never be retried for the same key.
        if (wantedKey != null && !cache.containsKey(wantedKey)) {
            wantedKey = null;
            show(null);
        }
    }

    private class ChipAdapter extends SelectionAdapter {

        @Override
        public boolean isEnabled(ViewHolder holder) {
            return false; // chips handle their own clicks
        }

        @Override
        public int getItemCount() {
            return tags.size();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final TextView chip = new TextView(parent.getContext());
            chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            chip.setSingleLine(true);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(dp(10), 0, dp(10), 0);
            chip.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)));
            chip.setOnClickListener(v -> {
                if (v.getTag() instanceof Tag) {
                    delegate.onTagClick((Tag) v.getTag());
                }
            });
            return new Holder(chip);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final Tag tag = tags.get(position);
            final TextView chip = (TextView) holder.itemView;
            final SpannableStringBuilder text = new SpannableStringBuilder("#").append(UnigifQuery.tagName(tag.name));
            if (tag.gifCount > 0) {
                final int start = text.length();
                text.append("  ").append(String.valueOf(tag.gifCount));
                text.setSpan(new ForegroundColorSpan(countColor), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            chip.setText(text);
            chip.setTextColor(textColor);
            chip.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(14), chipColor, pressedColor));
            chip.setTag(tag);
            chip.setContentDescription(text);
        }
    }
}
