package myau.module.modules;

import myau.Myau;
import myau.event.EventManager;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.AttackEvent;
import myau.events.PacketEvent;
import myau.events.UpdateEvent;
import myau.mixin.IAccessorEntity;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.util.ChatUtil;
import myau.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class ReduceVelocity extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private boolean slot;
    private boolean attack;
    private boolean swing;
    private boolean block;
    private boolean inventory;
    private boolean dig;

    public final BooleanProperty reduce = new BooleanProperty("reduce", true);
    public final BooleanProperty tickExactEnable = new BooleanProperty("tick-exact", true);
    public final BooleanProperty debug = new BooleanProperty("debug", false);
    public final IntProperty tick500 = new IntProperty("500", 3, 0, 20);
    public final IntProperty tick1000 = new IntProperty("1000", 4, 0, 20);
    public final IntProperty tick2000 = new IntProperty("2000", 4, 0, 20);
    public final IntProperty tick3000 = new IntProperty("3000", 5, 0, 20);
    public final IntProperty tick4000 = new IntProperty("4000", 6, 0, 20);
    public final IntProperty tick5000 = new IntProperty("5000", 6, 0, 20);
    public final IntProperty tick6000 = new IntProperty("6000", 7, 0, 20);
    public final IntProperty tick7000 = new IntProperty("7000", 7, 0, 20);
    public final IntProperty tick8000 = new IntProperty("8000", 8, 0, 20);
    public final IntProperty tick9000 = new IntProperty("9000", 8, 0, 20);
    public final IntProperty tick10000 = new IntProperty("10000", 9, 0, 20);

    private int reduceTicks;
    private int reductions;

    public ReduceVelocity() {
        super("ReduceVelocity", false);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        if (event.getType() == EventType.RECEIVE && !event.isCancelled()) {
            Packet<?> packet = event.getPacket();
            if (packet instanceof S12PacketEntityVelocity) {
                S12PacketEntityVelocity velocity = (S12PacketEntityVelocity) packet;
                if (velocity.getEntityID() == mc.thePlayer.getEntityId()) {
                    this.reduceTicks = this.getReduceTicks(velocity.getMotionX(), velocity.getMotionZ());
                }
            }
            return;
        }

        if (event.getType() != EventType.SEND || event.isCancelled()) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (packet instanceof C09PacketHeldItemChange) {
            this.slot = true;
        } else if (packet instanceof C0APacketAnimation) {
            this.swing = true;
        } else if (packet instanceof C02PacketUseEntity) {
            C02PacketUseEntity useEntity = (C02PacketUseEntity) packet;
            if (useEntity.getAction() == C02PacketUseEntity.Action.ATTACK) {
                this.attack = true;
            }
        } else if (packet instanceof C08PacketPlayerBlockPlacement) {
            this.block = true;
        } else if (packet instanceof C07PacketPlayerDigging) {
            this.block = true;
            this.dig = true;
        } else if (packet instanceof C0DPacketCloseWindow
                || packet instanceof C0EPacketClickWindow
                || packet instanceof C16PacketClientStatus
                && ((C16PacketClientStatus) packet).getStatus() == C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT) {
            this.inventory = true;
        } else if (packet instanceof C03PacketPlayer) {
            this.resetBadPackets();
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE || this.reduceTicks <= 0 || !this.reduce.getValue()) {
            return;
        }

        --this.reduceTicks;

        KillAura killAura = (KillAura) Myau.moduleManager.getModule(KillAura.class);
        if (killAura == null || !killAura.isEnabled()) {
            return;
        }

        EntityLivingBase target = killAura.getTarget();
        if (target == null || target == mc.thePlayer) {
            return;
        }
        if (((IAccessorEntity) mc.thePlayer).getIsInWeb()) {
            return;
        }
        if (!mc.thePlayer.isSprinting() || !this.isMoving() || this.hasBadPackets()) {
            return;
        }

        AttackEvent attackEvent = new AttackEvent(target);
        EventManager.call(attackEvent);
        if (attackEvent.isCancelled()) {
            return;
        }

        PacketUtil.sendPacket(new C0APacketAnimation());
        PacketUtil.sendPacket(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));

        mc.thePlayer.motionX *= 0.6;
        mc.thePlayer.motionZ *= 0.6;
        mc.thePlayer.setSprinting(false);

        ++this.reductions;
        if (this.debug.getValue()) {
            ChatUtil.sendRaw("Reduce" + this.reductions);
        }
    }

    private int getReduceTicks(int motionX, int motionZ) {
        double kb = Math.hypot(motionX, motionZ);

        if (!this.tickExactEnable.getValue()) {
            int result = (int) Math.round(6.43153527E-4 * kb + 2.9419087136);
            if (result < 1) {
                return 1;
            }
            return Math.min(result, 10);
        }

        if (kb <= 500.0) {
            return this.tick500.getValue();
        }
        if (kb <= 1000.0) {
            return this.tick1000.getValue();
        }
        if (kb <= 2000.0) {
            return this.tick2000.getValue();
        }
        if (kb <= 3000.0) {
            return this.tick3000.getValue();
        }
        if (kb <= 4000.0) {
            return this.tick4000.getValue();
        }
        if (kb <= 5000.0) {
            return this.tick5000.getValue();
        }
        if (kb <= 6000.0) {
            return this.tick6000.getValue();
        }
        if (kb <= 7000.0) {
            return this.tick7000.getValue();
        }
        if (kb <= 8000.0) {
            return this.tick8000.getValue();
        }
        if (kb <= 9000.0) {
            return this.tick9000.getValue();
        }
        return this.tick10000.getValue();
    }

    private boolean hasBadPackets() {
        return this.slot || this.attack || this.swing || this.block || this.inventory || this.dig;
    }

    private void resetBadPackets() {
        this.slot = false;
        this.swing = false;
        this.attack = false;
        this.block = false;
        this.inventory = false;
        this.dig = false;
    }

    private boolean isMoving() {
        return mc.thePlayer.movementInput.moveForward != 0.0F || mc.thePlayer.movementInput.moveStrafe != 0.0F;
    }

    @Override
    public boolean onEnabled() {
        this.reduceTicks = 0;
        this.reductions = 0;
        this.resetBadPackets();
        return false;
    }

    @Override
    public void onDisabled() {
        this.reduceTicks = 0;
        this.reductions = 0;
        this.resetBadPackets();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{"NOXZ"};
    }
}
