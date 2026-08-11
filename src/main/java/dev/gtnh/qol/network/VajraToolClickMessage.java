package dev.gtnh.qol.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Carries the client ray hit that Forge 1.7.10 discards from PlayerInteractEvent. */
public final class VajraToolClickMessage implements IMessage {

    private int x;
    private int y;
    private int z;
    private int face;
    private float hitX;
    private float hitY;
    private float hitZ;

    public VajraToolClickMessage() {}

    public VajraToolClickMessage(int x, int y, int z, int face, float hitX, float hitY, float hitZ) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.face = face;
        this.hitX = hitX;
        this.hitY = hitY;
        this.hitZ = hitZ;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        x = buffer.readInt();
        y = buffer.readInt();
        z = buffer.readInt();
        face = buffer.readUnsignedByte();
        hitX = buffer.readFloat();
        hitY = buffer.readFloat();
        hitZ = buffer.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
        buffer.writeByte(face);
        buffer.writeFloat(hitX);
        buffer.writeFloat(hitY);
        buffer.writeFloat(hitZ);
    }

    public static final class Handler implements IMessageHandler<VajraToolClickMessage, IMessage> {

        @Override
        public IMessage onMessage(VajraToolClickMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            ServerVajraClickQueue.enqueue(
                player,
                message.x,
                message.y,
                message.z,
                message.face,
                message.hitX,
                message.hitY,
                message.hitZ);
            return null;
        }
    }
}
