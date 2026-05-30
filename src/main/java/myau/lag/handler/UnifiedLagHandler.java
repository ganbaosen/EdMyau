package myau.lag.handler;

import myau.lag.api.EnumLagDirection;
import myau.lag.api.LagRequest;
import net.minecraft.network.Packet;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class UnifiedLagHandler {
    private final List<LagRequest> activeRequests = new ArrayList<>();
    private final Queue<Packet<?>> inboundQueue = new ConcurrentLinkedQueue<>();
    private final Queue<Packet<?>> outboundQueue = new ConcurrentLinkedQueue<>();

    public void requestLag(LagRequest request) {
        synchronized (activeRequests) {
            activeRequests.add(request);
        }
    }

    public boolean shouldLagInbound() {
        synchronized (activeRequests) {
            return activeRequests.stream()
                    .anyMatch(req -> req.getDirections().contains(EnumLagDirection.INBOUND));
        }
    }

    public boolean shouldLagOutbound() {
        synchronized (activeRequests) {
            return activeRequests.stream()
                    .anyMatch(req -> req.getDirections().contains(EnumLagDirection.OUTBOUND));
        }
    }

    public void queueInbound(Packet<?> packet) {
        inboundQueue.offer(packet);
    }

    public void queueOutbound(Packet<?> packet) {
        outboundQueue.offer(packet);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        synchronized (activeRequests) {
            activeRequests.removeIf(req -> req.getTimeout().isTimedOut());
        }

        // Release queued packets if no longer lagging
        if (!shouldLagInbound()) {
            while (!inboundQueue.isEmpty()) {
                Packet<?> packet = inboundQueue.poll();
                if (packet != null) {
                    EnumLagDirection.INBOUND.passThroughChannel(packet);
                }
            }
        }

        if (!shouldLagOutbound()) {
            while (!outboundQueue.isEmpty()) {
                Packet<?> packet = outboundQueue.poll();
                if (packet != null) {
                    EnumLagDirection.OUTBOUND.passThroughChannel(packet);
                }
            }
        }
    }
}
