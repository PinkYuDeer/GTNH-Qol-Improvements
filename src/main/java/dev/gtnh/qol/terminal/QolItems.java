package dev.gtnh.qol.terminal;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;

import com.glodblock.github.loader.recipe.WirelessTerminalEnergyRecipe;
import com.glodblock.github.loader.recipe.WirelessTerminalQuantumBridgeRecipe;

import appeng.api.AEApi;
import cpw.mods.fml.common.registry.GameRegistry;

public final class QolItems {

    public static ItemDualTerminal dualTerminal;
    public static ItemQuickEncodingTerminalPart panelTerminal;

    private QolItems() {}

    public static void register() {
        dualTerminal = new ItemDualTerminal();
        dualTerminal.setUnlocalizedName("gtnh_qol_improvements.dual_terminal");
        dualTerminal.setTextureName("appliedenergistics2:ToolWirelessTerminal");
        dualTerminal.setCreativeTab(CreativeTabs.tabTools);
        GameRegistry.registerItem(dualTerminal, "dual_terminal");

        panelTerminal = new ItemQuickEncodingTerminalPart();
        panelTerminal.setUnlocalizedName("gtnh_qol_improvements.panel_terminal");
        panelTerminal.setCreativeTab(CreativeTabs.tabTools);
        GameRegistry.registerItem(panelTerminal, "panel_terminal");
    }

    public static void registerRecipe() {
        ItemStack wireless = AEApi.instance()
            .definitions()
            .items()
            .wirelessTerminal()
            .maybeStack(1)
            .orNull();
        ItemStack pattern = AEApi.instance()
            .definitions()
            .parts()
            .patternTerminalEx()
            .maybeStack(1)
            .orNull();
        ItemStack terminal = AEApi.instance()
            .definitions()
            .parts()
            .interfaceTerminal()
            .maybeStack(1)
            .orNull();
        if (wireless != null && pattern != null && terminal != null) {
            GameRegistry.addShapelessRecipe(new ItemStack(dualTerminal), wireless, pattern, terminal);
        }
        ItemStack normalPattern = AEApi.instance()
            .definitions()
            .parts()
            .patternTerminal()
            .maybeStack(1)
            .orNull();
        if (normalPattern != null && terminal != null) {
            GameRegistry.addShapelessRecipe(new ItemStack(panelTerminal), normalPattern, terminal.copy());
        }
        GameRegistry.addRecipe(new WirelessTerminalEnergyRecipe(new ItemStack(dualTerminal)));
        GameRegistry.addRecipe(new WirelessTerminalQuantumBridgeRecipe(new ItemStack(dualTerminal)));
    }
}
