package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import app.retrogram.RetroConfig;

import org.telegram.messenger.R;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UndoView;

import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.cell.AbstractConfigCell;
import tw.nekomimi.nekogram.config.cell.ConfigCellDivider;
import tw.nekomimi.nekogram.config.cell.ConfigCellHeader;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheck;

/** Settings → Retrogram. Only holds the switches from {@link RetroConfig}. */
public class RetrogramSettingsActivity extends BaseNekoXSettingsActivity {

    private ListAdapter listAdapter;

    private final CellGroup cellGroup = new CellGroup(this);

    private final AbstractConfigCell headerInterface = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.RetrogramInterface)));
    private final AbstractConfigCell retroUiRow = cellGroup.appendCell(new ConfigCellTextCheck(RetroConfig.retroUi, getString(R.string.RetroUiInfo)));
    private final AbstractConfigCell remoteMetadataRow = cellGroup.appendCell(new ConfigCellTextCheck(RetroConfig.remoteMetadata, getString(R.string.RetroRemoteMetadataInfo)));
    private final AbstractConfigCell classicSettingsRow = cellGroup.appendCell(new ConfigCellTextCheck(RetroConfig.classicSettings, getString(R.string.RetroClassicSettingsInfo)));
    private final AbstractConfigCell classicInputRow = cellGroup.appendCell(new ConfigCellTextCheck(RetroConfig.classicInput, getString(R.string.RetroClassicInputInfo)));
    private final AbstractConfigCell dividerInterface = cellGroup.appendCell(new ConfigCellDivider());

    public RetrogramSettingsActivity() {
        addRowsToMap(cellGroup);
    }

    @Override
    public View createView(Context context) {
        View superView = super.createView(context);

        listAdapter = new ListAdapter(context);
        listView.setAdapter(listAdapter);

        setupDefaultListeners();

        // The UI and metadata switches are read once per process start; the classic-settings and classic-input switches are live (input applies to chats opened afterwards).
        cellGroup.callBackSettingsChanged = (key, newValue) -> {
            if (!RetroConfig.classicSettings.getKey().equals(key) && !RetroConfig.classicInput.getKey().equals(key)) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            }
        };

        return superView;
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
    }
}
