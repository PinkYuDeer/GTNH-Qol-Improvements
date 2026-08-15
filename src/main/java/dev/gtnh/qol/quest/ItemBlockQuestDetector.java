package dev.gtnh.qol.quest;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

public final class ItemBlockQuestDetector extends ItemBlock {

    public ItemBlockQuestDetector(Block block) {
        super(block);
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        tooltip.add(new ChatComponentTranslation("gtnh_qol_improvements.quest_detector.tooltip.1").getFormattedText());
        tooltip.add(new ChatComponentTranslation("gtnh_qol_improvements.quest_detector.tooltip.2").getFormattedText());
        tooltip.add(new ChatComponentTranslation("gtnh_qol_improvements.quest_detector.tooltip.3").getFormattedText());
    }
}
