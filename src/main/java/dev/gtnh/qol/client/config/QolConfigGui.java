package dev.gtnh.qol.client.config;

import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;

import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;
import dev.gtnh.qol.GTNHQolImprovements;
import dev.gtnh.qol.config.QolConfig;

public final class QolConfigGui extends GuiConfig {

    public QolConfigGui(GuiScreen parent) {
        super(parent, getElements(), GTNHQolImprovements.MOD_ID, false, false, GTNHQolImprovements.MOD_NAME);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static List<IConfigElement> getElements() {
        return new ConfigElement(QolConfig.getFeaturesCategory()).getChildElements();
    }
}
