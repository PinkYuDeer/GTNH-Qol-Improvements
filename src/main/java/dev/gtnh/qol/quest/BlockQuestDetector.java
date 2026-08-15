package dev.gtnh.qol.quest;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dev.gtnh.qol.config.QolConfig;

public final class BlockQuestDetector extends BlockContainer {

    @SideOnly(Side.CLIENT)
    private IIcon inactiveIcon;
    @SideOnly(Side.CLIENT)
    private IIcon activeIcon;

    public BlockQuestDetector() {
        super(Material.iron);
        setBlockName("gtnh_qol_improvements.quest_detector");
        setHardness(1.5F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
        setCreativeTab(CreativeTabs.tabRedstone);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new TileQuestDetector();
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
        if (world.isRemote || !(placer instanceof EntityPlayer player)) return;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileQuestDetector detector) detector.bindOwner(player);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileQuestDetector detector)) return false;

        detector.requestScan();
        String state;
        if (!QolConfig.questDetector) state = "disabled";
        else if (!detector.hasOwner()) state = "unbound";
        else if (!detector.isNetworkActive()) state = "offline";
        else state = "online";
        player.addChatMessage(
            new ChatComponentTranslation(
                "gtnh_qol_improvements.quest_detector.status." + state,
                detector.getOwnerName()));
        return true;
    }

    @Override
    public int damageDropped(int metadata) {
        return 0;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        inactiveIcon = register.registerIcon("gtnh_qol_improvements:quest_detector");
        activeIcon = register.registerIcon("gtnh_qol_improvements:quest_detector_on");
        blockIcon = inactiveIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int metadata) {
        return (metadata & 1) != 0 ? activeIcon : inactiveIcon;
    }

}
