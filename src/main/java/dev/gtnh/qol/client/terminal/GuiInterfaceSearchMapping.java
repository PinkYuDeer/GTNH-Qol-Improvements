package dev.gtnh.qol.client.terminal;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.StatCollector;

public final class GuiInterfaceSearchMapping extends GuiScreen {

    private static final int SAVE = 0;
    private static final int RESET = 1;
    private static final int CANCEL = 2;

    private final GuiQuickEncodingTerminal parent;
    private final String mappingKey;
    private final String defaultValue;
    private GuiTextField valueField;

    public GuiInterfaceSearchMapping(GuiQuickEncodingTerminal parent, String mappingKey, String defaultValue) {
        this.parent = parent;
        this.mappingKey = mappingKey;
        this.defaultValue = defaultValue == null ? "" : defaultValue;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int centerX = width / 2;
        int centerY = height / 2;
        valueField = new GuiTextField(fontRendererObj, centerX - 120, centerY - 18, 240, 20);
        valueField.setMaxStringLength(256);
        String custom = InterfaceSearchMappings.getCustom(mappingKey);
        valueField.setText(custom == null ? defaultValue : custom);
        valueField.setFocused(true);
        valueField.setCursorPositionEnd();

        buttonList.add(
            new GuiButton(
                SAVE,
                centerX - 120,
                centerY + 14,
                76,
                20,
                StatCollector.translateToLocal("gtnh_qol_improvements.terminal.edit_search_mapping.save")));
        buttonList.add(
            new GuiButton(
                RESET,
                centerX - 39,
                centerY + 14,
                98,
                20,
                StatCollector.translateToLocal("gtnh_qol_improvements.terminal.edit_search_mapping.reset")));
        buttonList.add(
            new GuiButton(
                CANCEL,
                centerX + 64,
                centerY + 14,
                56,
                20,
                StatCollector.translateToLocal("gtnh_qol_improvements.terminal.edit_search_mapping.cancel")));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        String title = StatCollector.translateToLocal("gtnh_qol_improvements.terminal.edit_search_mapping.title");
        drawCenteredString(fontRendererObj, title, width / 2, height / 2 - 52, 0xFFFFFF);
        String defaultText = StatCollector
            .translateToLocalFormatted("gtnh_qol_improvements.terminal.edit_search_mapping.default", defaultValue);
        drawCenteredString(fontRendererObj, defaultText, width / 2, height / 2 - 36, 0xA0A0A0);
        valueField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void updateScreen() {
        valueField.updateCursorCounter();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        valueField.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (key == 1) {
            returnToParent(false);
            return;
        }
        if (key == 28 || key == 156) {
            save();
            return;
        }
        valueField.textboxKeyTyped(character, key);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == SAVE) {
            save();
        } else if (button.id == RESET) {
            InterfaceSearchMappings.reset(mappingKey);
            returnToParent(true);
        } else if (button.id == CANCEL) {
            returnToParent(false);
        }
    }

    private void save() {
        InterfaceSearchMappings.setCustom(mappingKey, defaultValue, valueField.getText());
        returnToParent(true);
    }

    private void returnToParent(boolean refreshSearch) {
        mc.displayGuiScreen(parent);
        if (refreshSearch) parent.refreshInterfaceSearchMapping(mappingKey, defaultValue);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
