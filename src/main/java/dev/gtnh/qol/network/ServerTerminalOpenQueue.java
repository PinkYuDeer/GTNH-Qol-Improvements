package dev.gtnh.qol.network;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.gtnh.qol.terminal.ItemDualTerminal;

public final class ServerTerminalOpenQueue {

    private static final Queue<Request> REQUESTS = new ConcurrentLinkedQueue<Request>();

    public static void enqueue(EntityPlayerMP player, int slot, int page) {
        REQUESTS.add(new Request(player, slot, page));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Request request;
        while ((request = REQUESTS.poll()) != null) {
            if (request.player.playerNetServerHandler != null) {
                ItemDualTerminal.openChecked(request.player, request.slot, true);
            }
        }
    }

    private static final class Request {

        private final EntityPlayerMP player;
        private final int slot;
        private final int page;

        private Request(EntityPlayerMP player, int slot, int page) {
            this.player = player;
            this.slot = slot;
            this.page = page;
        }
    }
}
