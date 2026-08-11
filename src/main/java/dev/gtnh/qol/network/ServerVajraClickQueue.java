package dev.gtnh.qol.network;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.gtnh.qol.vajra.VajraEventHandler;

/** Moves precise Vajra clicks from Netty onto the authoritative server thread. */
public final class ServerVajraClickQueue {

    private static final Queue<Request> REQUESTS = new ConcurrentLinkedQueue<Request>();

    public static void enqueue(EntityPlayerMP player, int x, int y, int z, int face, float hitX, float hitY,
        float hitZ) {
        REQUESTS.add(new Request(player, x, y, z, face, hitX, hitY, hitZ));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Request request;
        while ((request = REQUESTS.poll()) != null) {
            if (request.player.playerNetServerHandler != null && !request.player.isDead) {
                VajraEventHandler.handlePreciseToolClick(
                    request.player,
                    request.x,
                    request.y,
                    request.z,
                    request.face,
                    request.hitX,
                    request.hitY,
                    request.hitZ);
            }
        }
    }

    private static final class Request {

        private final EntityPlayerMP player;
        private final int x;
        private final int y;
        private final int z;
        private final int face;
        private final float hitX;
        private final float hitY;
        private final float hitZ;

        private Request(EntityPlayerMP player, int x, int y, int z, int face, float hitX, float hitY, float hitZ) {
            this.player = player;
            this.x = x;
            this.y = y;
            this.z = z;
            this.face = face;
            this.hitX = hitX;
            this.hitY = hitY;
            this.hitZ = hitZ;
        }
    }
}
