package dev.gtnh.qol.quest;

import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import betterquesting.api.utils.BigItemStack;
import betterquesting.api.utils.ItemComparison;
import bq_standard.tasks.TaskRetrieval;

/** Dedicated-server-safe BetterQuesting item and ore-dictionary matching. */
final class QuestItemMatcher {

    private QuestItemMatcher() {}

    static List<ItemStack> getOreStacks(BigItemStack required) {
        String oreName = getOreName(required);
        return oreName == null ? Collections.emptyList() : OreDictionary.getOres(oreName);
    }

    static boolean matches(TaskRetrieval task, BigItemStack required, ItemStack stack) {
        if (ItemComparison.StackMatch(required.getBaseStack(), stack, !task.ignoreNBT, task.partialMatch)) return true;

        String oreName = getOreName(required);
        if (oreName == null || stack == null || !hasOreName(stack, oreName)) return false;
        return task.ignoreNBT
            || ItemComparison.CompareNBTTag(stack.getTagCompound(), required.GetTagCompound(), task.partialMatch);
    }

    private static String getOreName(BigItemStack required) {
        if (required == null) return null;
        String oreName = required.getOreDict();
        return oreName == null || oreName.isEmpty() ? null : oreName;
    }

    private static boolean hasOreName(ItemStack stack, String oreName) {
        for (int oreId : OreDictionary.getOreIDs(stack)) {
            if (oreName.equals(OreDictionary.getOreName(oreId))) return true;
        }
        return false;
    }
}
