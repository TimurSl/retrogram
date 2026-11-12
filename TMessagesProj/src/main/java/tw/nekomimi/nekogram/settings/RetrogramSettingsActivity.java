package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import app.retrogram.RetroConfig;
import app.retrogram.unigif.UnigifClient;
import app.retrogram.unigif.UnigifConfig;
import app.retrogram.unigif.UnigifErrors;
import app.retrogram.unigif.UnigifModels;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UndoView;

import okhttp3.Call;

import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.cell.AbstractConfigCell;
import tw.nekomimi.nekogram.config.cell.ConfigCellCustom;
import tw.nekomimi.nekogram.config.cell.ConfigCellDivider;
import tw.nekomimi.nekogram.config.cell.ConfigCellHeader;
import tw.nekomimi.nekogram.config.cell.ConfigCellSelectBox;
import tw.nekomimi.nekogram.config.cell.ConfigCellText;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheck;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextDetail;

/** Settings → Retrogram. Holds the switches from {@link RetroConfig} and the GIF provider section (docs/plans/08-unigif.md, WP U.1). */
public class RetrogramSettingsActivity extends BaseNekoXSettingsActivity {

    private ListAdapter listAdapter;

    private final CellGroup cellGroup = new CellGroup(this);

    private final AbstractConfigCell headerInterface = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.RetrogramInterface)));
    private final AbstractConfigCell retroUiRow = cellGroup.appendCell(new ConfigCellTextCheck(RetroConfig.retroUi, getString(R.string.RetroUiInfo)));
    private final AbstractConfigCell remoteMetadataRow = cellGroup.appendCell(new ConfigCellTextCheck(RetroConfig.remoteMetadata, getString(R.string.RetroRemoteMetadataInfo)));
    private final AbstractConfigCell classicSettingsRow = cellGroup.appendCell(new ConfigCellTextCheck(RetroConfig.classicSettings, getString(R.string.RetroClassicSettingsInfo)));
    private final AbstractConfigCell classicInputRow = cellGroup.appendCell(new ConfigCellTextCheck(RetroConfig.classicInput, getString(R.string.RetroClassicInputInfo)));
    private final AbstractConfigCell classicChatHeaderRow = cellGroup.appendCell(new ConfigCellTextCheck(RetroConfig.classicChatHeader, getString(R.string.RetroClassicChatHeaderInfo)));
    private final AbstractConfigCell classicEmojiPanelRow = cellGroup.appendCell(new ConfigCellTextCheck(RetroConfig.classicEmojiPanel, getString(R.string.RetroClassicEmojiPanelInfo)));
    private final AbstractConfigCell classicColorsRow = cellGroup.appendCell(new ConfigCellTextCheck(RetroConfig.classicColors, getString(R.string.RetroClassicColorsInfo)));
    private final AbstractConfigCell dividerInterface = cellGroup.appendCell(new ConfigCellDivider());

    // GIF provider. The UniGif rows are only in the list while UniGif is selected (see updateGifRows).
    private final AbstractConfigCell headerGifs = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.RetrogramGifs)));
    private final AbstractConfigCell gifProviderRow = cellGroup.appendCell(new ConfigCellSelectBox(null, RetroConfig.gifProvider, new String[]{
            getString(R.string.UnigifProviderKlipy),
            getString(R.string.UnigifProviderUnigif),
    }, null));
    private final AbstractConfigCell unigifUrlRow = new ConfigCellTextDetail(RetroConfig.unigifUrl, null, getString(R.string.UnigifUrlHint),
            UnigifConfig::normalizeUrl,
            (raw, normalized) -> !normalized.isEmpty() && !UnigifConfig.isValidUrl(normalized),
            value -> TextUtils.isEmpty(value) ? getString(R.string.UnigifUrlHint) : value);
    private final AbstractConfigCell unigifKeyRow = new ConfigCellTextDetail(RetroConfig.unigifKey, null, null,
            String::trim,
            (raw, trimmed) -> false,
            RetrogramSettingsActivity::maskKey);
    private final AbstractConfigCell unigifTestRow = new ConfigCellText("UnigifTestConnection", this::testUnigifConnection);
    private final AbstractConfigCell unigifInfoRow = cellGroup.appendCell(new ConfigCellCustom("UnigifInfo", CellGroup.ITEM_TYPE_TEXT, false));

    public RetrogramSettingsActivity() {
        updateGifRows();
    }

    /** Puts the URL, key and test rows between the provider row and the info text when UniGif is selected, and removes them otherwise. */
    private void updateGifRows() {
        cellGroup.rows.remove(unigifUrlRow);
        cellGroup.rows.remove(unigifKeyRow);
        cellGroup.rows.remove(unigifTestRow);
        if (UnigifConfig.isSelected()) {
            int index = cellGroup.rows.indexOf(gifProviderRow) + 1;
            for (AbstractConfigCell row : new AbstractConfigCell[]{unigifUrlRow, unigifKeyRow, unigifTestRow}) {
                row.bindCellGroup(cellGroup);
                cellGroup.rows.add(index++, row);
            }
        }
        addRowsToMap(cellGroup);
    }

    private static String maskKey(String key) {
        String value = key == null ? "" : key.trim();
        if (value.isEmpty()) {
            return getString(R.string.None);
        }
        return value.length() > 8 ? value.substring(0, 4) + "********" + value.substring(value.length() - 4) : "********";
    }

    private void testUnigifConnection() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        AlertDialog progress = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
        Call[] call = new Call[1];
        progress.setOnCancelListener(dialog -> {
            if (call[0] != null) {
                call[0].cancel();
            }
        });
        progress.show();
        call[0] = UnigifClient.me((me, error) -> {
            try {
                progress.dismiss();
            } catch (Exception ignore) {
            }
            if (getParentActivity() == null) {
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
            builder.setTitle(getString(R.string.UnigifTestConnection));
            builder.setMessage(me != null ? describeKey(me) : describeError(error));
            builder.setPositiveButton(getString(R.string.OK), null);
            showDialog(builder.create());
        });
    }

    private static String describeKey(UnigifModels.Me me) {
        StringBuilder text = new StringBuilder();
        text.append(LocaleController.formatString(R.string.UnigifTestOk, TextUtils.isEmpty(me.name) ? "?" : me.name));
        text.append('\n').append(LocaleController.formatString(R.string.UnigifTestScopes, me.scopes.isEmpty() ? getString(R.string.None) : TextUtils.join(", ", me.scopes)));
        if (!me.hasScope("read")) {
            text.append("\n\n").append(getString(R.string.UnigifTestNoRead));
        }
        if (!me.hasScope("upload")) {
            text.append("\n\n").append(getString(R.string.UnigifTestNoUpload));
        }
        return text.toString();
    }

    private static String describeError(UnigifModels.UnigifError error) {
        String text = UnigifErrors.message(error);
        // The server's own wording is shown here only (plan: "detail" appears in the test result, nowhere else).
        return error == null || error.detail.isEmpty() ? text : text + "\n\n" + error.detail;
    }

    @Override
    public View createView(Context context) {
        View superView = super.createView(context);

        listAdapter = new ListAdapter(context);
        listView.setAdapter(listAdapter);

        setupDefaultListeners();

        // The UI and metadata switches are read once per process start; the classic-settings and classic-input switches are live (input applies to chats opened afterwards).
        cellGroup.callBackSettingsChanged = (key, newValue) -> {
            if (!RetroConfig.classicSettings.getKey().equals(key) && !RetroConfig.classicInput.getKey().equals(key)
                && !RetroConfig.classicChatHeader.getKey().equals(key)
                && !RetroConfig.classicEmojiPanel.getKey().equals(key)
                && !isGifKey(key)) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            }
            if (isGifKey(key)) {
                // Live settings: show or hide the UniGif rows and refresh the cleartext warning in the info text.
                updateGifRows();
                listAdapter.notifyDataSetChanged();
            }
        };

        return superView;
    }

    private static boolean isGifKey(String key) {
        return RetroConfig.gifProvider.getKey().equals(key) || RetroConfig.unigifUrl.getKey().equals(key) || RetroConfig.unigifKey.getKey().equals(key);
    }

    @Override
    protected boolean onItemLongClick(View view, int position, float x, float y) {
        // The default long-press menu copies a settings link that contains the row's value; never do that for the key.
        return position >= 0 && position < cellGroup.rows.size() && cellGroup.rows.get(position) == unigifKeyRow;
    }

    @Override
    protected RecyclerListView.SelectionAdapter getListAdapter() {
        return listAdapter;
    }

    @Override
    protected CellGroup getCellGroup() {
        return cellGroup;
    }

    @Override
    protected String getSettingsPrefix() {
        return "retrogram";
    }

    @Override
    public int getBaseGuid() {
        return 16000;
    }

    @Override
    public int getDrawable() {
        return R.drawable.msg_theme;
    }

    @Override
    public String getTitle() {
        return getString(R.string.Retrogram);
    }

    private class ListAdapter extends BaseListAdapter {
        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        protected void onBindCustomViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (cellGroup.rows.get(position) == unigifInfoRow) {
                String info = getString(R.string.UnigifInfo);
                if (UnigifConfig.isSelected() && UnigifConfig.isCleartext(UnigifConfig.normalizeUrl(RetroConfig.unigifUrl.String()))) {
                    info = getString(R.string.UnigifHttpWarning) + "\n\n" + info;
                }
                ((TextInfoPrivacyCell) holder.itemView).setText(info);
            }
        }
    }
}
