package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.LoadWorldEvent;
import myau.events.PacketEvent;
import myau.mixin.IAccessorS27PacketExplosion;
import myau.module.Module;
import myau.property.properties.PercentProperty;
import myau.util.RandomUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;

public class Velocity1 extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final PercentProperty horizontal = new PercentProperty("horizontal", 0);
    public final PercentProperty vertical = new PercentProperty("vertical", 0);
    public final PercentProperty chance = new PercentProperty("chance", 100);

    public Velocity1() {
        super("Velocity1", false);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || event.getType() != EventType.RECEIVE || event.isCancelled()) {
            return;
        }

        if (RandomUtil.nextDouble(0.0D, 100.0D) >= this.chance.getValue()) {
            return;
        }

        if (event.getPacket() instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
            if (packet.getEntityID() != mc.thePlayer.getEntityId()) {
                return;
            }

            event.setCancelled(true);
            if (this.horizontal.getValue() == 0 && this.vertical.getValue() == 0) {
                return;
            }

            double motionX = packet.getMotionX() / 8000.0D;
            double motionY = packet.getMotionY() / 8000.0D;
            double motionZ = packet.getMotionZ() / 8000.0D;

            if (this.horizontal.getValue() != 0) {
                double horizontalScale = this.horizontal.getValue() / 100.0D;
                mc.thePlayer.motionX = motionX * horizontalScale;
                mc.thePlayer.motionZ = motionZ * horizontalScale;
            }
            if (this.vertical.getValue() != 0) {
                mc.thePlayer.motionY = motionY * (this.vertical.getValue() / 100.0D);
            }
            return;
        }

        if (event.getPacket() instanceof S27PacketExplosion) {
            S27PacketExplosion packet = (S27PacketExplosion) event.getPacket();
            IAccessorS27PacketExplosion accessor = (IAccessorS27PacketExplosion) packet;
            if (this.horizontal.getValue() == 0 && this.vertical.getValue() == 0) {
                accessor.setMotionX(0.0F);
                accessor.setMotionY(0.0F);
                accessor.setMotionZ(0.0F);
                return;
            }

            accessor.setMotionX(packet.func_149149_c() * (this.horizontal.getValue() / 100.0F));
            accessor.setMotionY(packet.func_149144_d() * (this.vertical.getValue() / 100.0F));
            accessor.setMotionZ(packet.func_149147_e() * (this.horizontal.getValue() / 100.0F));
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.onDisabled();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.horizontal.getValue() + "%", this.vertical.getValue() + "%"};
    }
}
