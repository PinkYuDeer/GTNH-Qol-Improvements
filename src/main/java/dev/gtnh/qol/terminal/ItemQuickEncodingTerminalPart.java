package dev.gtnh.qol.terminal;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import appeng.api.AEApi;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Places the early-game quick encoding terminal as an AE cable part. */
public final class ItemQuickEncodingTerminalPart extends Item implements IPartItem {

    public ItemQuickEncodingTerminalPart() {
        setMaxStackSize(64);
        AEApi.instance()
            .partHelper()
            .setItemBusRenderer(this);
    }

    @Override
    public IPart createPartFromItemStack(ItemStack stack) {
        return new PartQuickEncodingTerminal(stack);
    }

    /** AE2 cable parts are rendered from the block atlas, not the normal item atlas. */
    @Override
    @SideOnly(Side.CLIENT)
    public int getSpriteNumber() {
        return 0;
    }

    /**
     * AbstractPartDisplay uses its owning item icon for the terminal's front
     * edge while rendering the inventory model. Reuse AE2's registered pattern
     * terminal icon instead of the unregistered icon of this custom part item.
     */
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int damage) {
        ItemStack patternTerminal = AEApi.instance()
            .definitions()
            .parts()
            .patternTerminal()
            .maybeStack(1)
            .orNull();
        return patternTerminal == null ? super.getIconFromDamage(damage) : patternTerminal.getIconIndex();
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        return AEApi.instance()
            .partHelper()
            .placeBus(stack, x, y, z, side, player, world);
    }
}
