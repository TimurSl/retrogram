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
import tw.nekomimi.nekogram.config.cell.ConfigCellTextInput;

/** Settings → Retrogram. Only holds the switches from {@link RetroConfig}. */
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
    private final AbstractConfigCell classicRoundButtonsRow = cellGroup.appendCell(new ConfigCellTextCheck(RetroConfig.classicRoundButtons, getString(R.string.RetroClassicRoundButtonsInfo)));
    private final AbstractConfigCell classicColorsRow = cellGroup.appendCell(new ConfigCellTextCheck(RetroConfig.classicColors, getString(R.string.RetroClassicColorsInfo)));
    private final AbstractConfigCell headerGraph = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.RetroGraphFeatures)));
    private final AbstractConfigCell graphAutoReplyRow = cellGroup.appendCell(new ConfigCellTextCheck(RetroConfig.graphAutoReply, getString(R.string.RetroGraphAutoReplyInfo)));
    private final AbstractConfigCell graphAutoReplyTextRow = cellGroup.appendCell(new ConfigCellTextInput(getString(R.string.RetroGraphAutoReplyText), RetroConfig.graphAutoReplyText, getString(R.string.RetroGraphAutoReplyTextHint), null));
    private final AbstractConfigCell graphContactsRow = cellGroup.appendCell(new ConfigCellTextCheck(RetroConfig.graphSpecialContacts, getString(R.string.RetroGraphSpecialContactsInfo)));
    private final AbstractConfigCell graphContactIdsRow = cellGroup.appendCell(new ConfigCellTextInput(getString(R.string.RetroGraphSpecialContactIds), RetroConfig.graphSpecialContactIds, getString(R.string.RetroGraphSpecialContactIdsHint), null));
    private final AbstractConfigCell graphContactsLogRow = cellGroup.appendCell(new tw.nekomimi.nekogram.config.cell.ConfigCellText("RetroGraphContactsLog", this::showContactsLog));
    private final AbstractConfigCell graphChatLockRow = cellGroup.appendCell(new ConfigCellTextCheck(RetroConfig.graphChatLock, getString(R.string.RetroGraphChatLockInfo)));
    private final AbstractConfigCell graphChatLockPinRow = cellGroup.appendCell(new tw.nekomimi.nekogram.config.cell.ConfigCellText("RetroGraphChatLockSetPin", () -> app.retrogram.graph.ChatLockGate.showSetPin(getParentActivity())));
    private final AbstractConfigCell graphHiddenChatsRow = cellGroup.appendCell(new ConfigCellTextCheck(RetroConfig.graphHiddenChats, getString(R.string.RetroGraphHiddenChatsInfo)));
    private final AbstractConfigCell graphHiddenChatsRevealRow = cellGroup.appendCell(new tw.nekomimi.nekogram.config.cell.ConfigCellText("RetroGraphHiddenChatsReveal", () -> app.retrogram.graph.HiddenChats.toggleReveal(getParentActivity(), currentAccount)));
    private final AbstractConfigCell graphVoiceEffectRow = cellGroup.appendCell(new tw.nekomimi.nekogram.config.cell.ConfigCellSelectBox("RetroGraphVoiceEffect", RetroConfig.graphVoiceEffect, new String[]{
            getString(R.string.RetroGraphVoiceEffectOff),
            getString(R.string.RetroGraphVoiceEffectRobot),
            getString(R.string.RetroGraphVoiceEffectEcho),
            getString(R.string.RetroGraphVoiceEffectHigher),
            getString(R.string.RetroGraphVoiceEffectDeeper),
    }, null));
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
            if (!RetroConfig.classicSettings.getKey().equals(key) && !RetroConfig.classicInput.getKey().equals(key)
                && !RetroConfig.classicChatHeader.getKey().equals(key)
                && !RetroConfig.classicEmojiPanel.getKey().equals(key)
                && !RetroConfig.classicRoundButtons.getKey().equals(key)
                && !RetroConfig.graphAutoReply.getKey().equals(key)
                && !RetroConfig.graphAutoReplyText.getKey().equals(key)
                && !RetroConfig.graphSpecialContacts.getKey().equals(key)
                && !RetroConfig.graphSpecialContactIds.getKey().equals(key)
                && !RetroConfig.graphChatLock.getKey().equals(key)
                && !RetroConfig.graphHiddenChats.getKey().equals(key)
                && !RetroConfig.graphVoiceEffect.getKey().equals(key)) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            }
        };

        return superView;
    }

    private void showContactsLog() {
        if (getParentActivity() == null) {
            return;
        }
        final String log = app.retrogram.graph.ContactWatchHook.readableLog();
        final org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.RetroGraphContactsLog));
        builder.setMessage(log.isEmpty() ? getString(R.string.RetroGraphContactsLogEmpty) : log);
        builder.setPositiveButton(getString(R.string.OK), null);
        if (!log.isEmpty()) {
            builder.setNegativeButton(getString(R.string.RetroGraphContactsLogClear), (d, w) -> app.retrogram.graph.ContactWatchHook.clearLog());
        }
        showDialog(builder.create());
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
