package dev.gtnh.qol.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class MiddleClickRequestMessage implements IMessage {

    private boolean worldBlock;
    private int hotbarSlot;
    private long amount;
    private IAEStack<?> stack;

    public MiddleClickRequestMessage() {}

    private MiddleClickRequestMessage(boolean worldBlock, int hotbarSlot, long amount, IAEStack<?> stack) {
        this.worldBlock = worldBlock;
        this.hotbarSlot = hotbarSlot;
        this.amount = Math.max(1, amount);
        this.stack = stack == null ? null : stack.copy();
    }

    public static MiddleClickRequestMessage bookmark(IAEStack<?> stack, long amount) {
        return new MiddleClickRequestMessage(false, -1, amount, stack);
    }

    public static MiddleClickRequestMessage worldBlock(int hotbarSlot, ItemStack stack) {
        return new MiddleClickRequestMessage(true, hotbarSlot, 1, AEItemStack.create(stack));
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        worldBlock = buffer.readBoolean();
        hotbarSlot = buffer.readByte();
        amount = buffer.readLong();
        NBTTagCompound tag = ByteBufUtils.readTag(buffer);
        stack = tag == null ? null : IAEStack.fromNBTGeneric(tag);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeBoolean(worldBlock);
        buffer.writeByte(hotbarSlot);
        buffer.writeLong(amount);
        ByteBufUtils.writeTag(buffer, stack == null ? null : stack.toNBTGeneric());
    }

    public static final class Handler implements IMessageHandler<MiddleClickRequestMessage, IMessage> {

        @Override
        public IMessage onMessage(MiddleClickRequestMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            ServerMiddleClickQueue
                .enqueue(player, message.worldBlock, message.hotbarSlot, message.amount, message.stack);
            return null;
        }
    }
}
