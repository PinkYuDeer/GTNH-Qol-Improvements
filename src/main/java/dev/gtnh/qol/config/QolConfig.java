package dev.gtnh.qol.config;

import java.io.File;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

public final class QolConfig {

    public static final String CATEGORY_FEATURES = "features";

    public static boolean vajraOffhandReplacement = true;
    public static boolean vajraToolFunctions = true;
    public static boolean dualTerminal = true;
    public static boolean terminalGtRecipeSearchSuffix = true;

    private static Configuration configuration;

    private QolConfig() {}

    public static void load(File file) {
        configuration = new Configuration(file, "1");
        sync();
    }

    public static void sync() {
        if (configuration == null) {
            return;
        }

        Property replace = configuration.get(
            CATEGORY_FEATURES,
            "vajraOffhandReplacement",
            true,
            "When a Vajra breaks a block, place the block held in the Backhand offhand into the vacated position.\n"
                + "金刚杵破坏方块后，将副手中的方块放入原位置。");
        replace.setLanguageKey("gtnh_qol_improvements.config.vajraOffhandReplacement");
        vajraOffhandReplacement = replace.getBoolean(true);

        Property tools = configuration.get(
            CATEGORY_FEATURES,
            "vajraToolFunctions",
            true,
            "Give the Vajra wrench and wire-cutter right-click behavior as one feature.\n" + "为金刚杵添加扳手与剪线钳右键功能（同一开关）。");
        tools.setLanguageKey("gtnh_qol_improvements.config.vajraToolFunctions");
        vajraToolFunctions = tools.getBoolean(true);

        Property terminal = configuration.get(
            CATEGORY_FEATURES,
            "dualTerminal",
            true,
            "Enable the wireless dual terminal, Alt+NEI encoding, searching, and pattern transfer as one feature.\n"
                + "启用二合一终端、Alt+NEI 编码、搜索与样板转移（同一开关）。");
        terminal.setLanguageKey("gtnh_qol_improvements.config.dualTerminal");
        dualTerminal = terminal.getBoolean(true);

        Property gtSearchSuffix = configuration.get(
            CATEGORY_FEATURES,
            "terminalGtRecipeSearchSuffix",
            true,
            "Append GT ghost-circuit numbers and non-consumed items (molds, shapes, lenses, etc.) to automatic interface searches.\n"
                + "在自动搜索接口时追加GT虚拟电路编号及不消耗物品（模具、模头、透镜等）后缀。");
        gtSearchSuffix.setLanguageKey("gtnh_qol_improvements.config.terminalGtRecipeSearchSuffix");
        terminalGtRecipeSearchSuffix = gtSearchSuffix.getBoolean(true);

        ConfigCategory category = configuration.getCategory(CATEGORY_FEATURES);
        category.setLanguageKey("gtnh_qol_improvements.config.features");
        category.setComment("GTNH QoL Improvements feature switches / 功能开关");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    public static ConfigCategory getFeaturesCategory() {
        return configuration.getCategory(CATEGORY_FEATURES);
    }
}
