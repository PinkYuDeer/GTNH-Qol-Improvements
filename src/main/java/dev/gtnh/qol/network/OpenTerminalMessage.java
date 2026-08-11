package dev.gtnh.qol.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class OpenTerminalMessage implements IMessage {

    private int slot;
    private int page;

    public OpenTerminalMessage() {}

    public OpenTerminalMessage(int slot, int page) {
        this.slot = slot;
        this.page = page;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        slot = buffer.readInt();
        page = buffer.readByte();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(slot);
        buffer.writeByte(page);
    }

    public static final class Handler implements IMessageHandler<OpenTerminalMessage, IMessage> {

        @Override
        public IMessage onMessage(OpenTerminalMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            ServerTerminalOpenQueue.enqueue(player, message.slot, message.page);
            return null;
        }
    }
}
