package myau.lag.api;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;

public enum EnumLagDirection {
    INBOUND(packet -> {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.getNetHandler() != null && packet instanceof Packet) {
                ((Packet<INetHandlerPlayClient>) packet).processPacket(mc.getNetHandler());
            }
        } catch (Exception e) {
            // Ignore exceptions
        }
    }),
    OUTBOUND(packet -> {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.getNetHandler() != null) {
                mc.getNetHandler().addToSendQueue(packet);
            }
        } catch (Exception e) {
            // Ignore exceptions
        }
    });

    public static final Set<EnumLagDirection> ONLY_INBOUND = EnumSet.of(INBOUND);
    public static final Set<EnumLagDirection> ONLY_OUTBOUND = EnumSet.of(OUTBOUND);
    public static final Set<EnumLagDirection> BIDIRECTIONAL = EnumSet.allOf(EnumLagDirection.class);

    private final Consumer<Packet<?>> channel;

    EnumLagDirection(Consumer<Packet<?>> channel) {
        this.channel = channel;
    }

    public void passThroughChannel(Packet<?> packet) {
        this.channel.accept(packet);
    }
}
