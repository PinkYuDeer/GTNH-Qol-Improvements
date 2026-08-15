package dev.gtnh.qol.quest;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.core.features.IStackSrc;
import appeng.tile.AEBaseTile;
import cpw.mods.fml.common.registry.GameRegistry;
import drethic.questbook.item.QBItems;

public final class QolBlocks {

    public static BlockQuestDetector questDetector;

    private QolBlocks() {}

    public static void register() {
        questDetector = new BlockQuestDetector();
        GameRegistry.registerBlock(questDetector, ItemBlockQuestDetector.class, "quest_detector");
        GameRegistry.registerTileEntity(TileQuestDetector.class, "gtnh_qol_improvements.quest_detector");
        AEBaseTile.registerTileItem(TileQuestDetector.class, new IStackSrc() {

            @Override
            public ItemStack stack(int amount) {
                return new ItemStack(questDetector, amount);
            }

            @Override
            public Item getItem() {
                return Item.getItemFromBlock(questDetector);
            }

            @Override
            public int getDamage() {
                return 0;
            }

            @Override
            public boolean isEnabled() {
                return true;
            }
        });
    }

    public static void registerRecipe() {
        ItemStack fluix = AEApi.instance()
            .definitions()
            .materials()
            .fluixCrystal()
            .maybeStack(1)
            .orNull();
        if (fluix == null || QBItems.ItemQuestBook == null) return;

        GameRegistry.addRecipe(
            new ItemStack(questDetector),
            "IFI",
            "FBF",
            "IFI",
            'I',
            Items.iron_ingot,
            'F',
            fluix,
            'B',
            QBItems.ItemQuestBook);
    }
}
