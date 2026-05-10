package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.LoadWorldEvent;
import myau.events.MoveInputEvent;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.MathHelper;

public class Stuck extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty disableOnHurt = new BooleanProperty("disable-on-hurt", true);

    private boolean stuck;
    private boolean lastOnGround;
    private double savedMotionX;
    private double savedMotionY;
    private double savedMotionZ;
    private boolean motionSaved;
    private boolean cancelMove;
    private int pearlPauseTicks;
    private float cachedYaw;
    private float cachedPitch;
    private boolean hasCachedRotation;

    public Stuck() {
        super("Stuck", false);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        this.stuck = true;
        this.lastOnGround = mc.thePlayer.onGround;
        if (this.pearlPauseTicks > 0) {
            this.stopStuck();
            --this.pearlPauseTicks;
            return;
        }
        this.startStuck();
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (!this.isEnabled() || !this.cancelMove || mc.thePlayer == null) {
            return;
        }

        mc.thePlayer.movementInput.moveForward = 0.0F;
        mc.thePlayer.movementInput.moveStrafe = 0.0F;
        mc.thePlayer.movementInput.jump = false;
        mc.thePlayer.setSprinting(false);
        mc.thePlayer.motionX = 0.0;
        mc.thePlayer.motionY = 0.0;
        mc.thePlayer.motionZ = 0.0;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        if (mc.thePlayer.isDead) {
            this.setEnabled(false);
            return;
        }

        if (this.disableOnHurt.getValue() && mc.thePlayer.hurtTime == 1) {
            this.setEnabled(false);
            return;
        }
        this.stuck = true;
        this.lastOnGround = mc.thePlayer.onGround;
    }

    private void startStuck() {
        if (!this.motionSaved) {
            this.savedMotionX = mc.thePlayer.motionX;
            this.savedMotionY = mc.thePlayer.motionY;
            this.savedMotionZ = mc.thePlayer.motionZ;
            this.motionSaved = true;
        }
        this.cancelMove = true;
    }

    private void stopStuck() {
        this.cancelMove = false;
        if (this.motionSaved && mc.thePlayer != null) {
            mc.thePlayer.motionX = this.savedMotionX;
            mc.thePlayer.motionY = this.savedMotionY;
            mc.thePlayer.motionZ = this.savedMotionZ;
            this.motionSaved = false;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        ItemStack pearlStack = this.getPearlStack(event.getPacket());
        if (event.getType() == EventType.SEND && pearlStack != null) {
            event.setCancelled(true);
            this.sendPearlUse(pearlStack, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
            this.pauseForPearlThrow();
            return;
        }

        if (event.getType() == EventType.SEND && event.getPacket() instanceof C03PacketPlayer) {
            C03PacketPlayer packet = (C03PacketPlayer) event.getPacket();
            this.lastOnGround = packet.isOnGround();
            event.setCancelled(true);
            return;
        }

        if (event.getType() != EventType.RECEIVE) {
            return;
        }

        if (!this.stuck) {
            this.stuck = true;
        }

        if (event.getPacket() instanceof S08PacketPlayerPosLook) {
            this.setEnabled(false);
            return;
        }

    }

    private ItemStack getPearlStack(Object packet) {
        if (!(packet instanceof C08PacketPlayerBlockPlacement)) {
            return null;
        }

        ItemStack stack = ((C08PacketPlayerBlockPlacement) packet).getStack();
        if (stack == null) {
            stack = mc.thePlayer.getHeldItem();
        }
        return stack != null && stack.getItem() instanceof ItemEnderPearl ? stack : null;
    }

    private void pauseForPearlThrow() {
        this.pearlPauseTicks = 1;
        this.stopStuck();
    }

    private void sendPearlUse(ItemStack heldItem, float yaw, float pitch) {
        float snappedYaw = snapRotation(mc.thePlayer.rotationYaw, yaw);
        float snappedPitch = snapRotation(mc.thePlayer.rotationPitch, MathHelper.clamp_float(pitch, -90.0F, 90.0F));
        mc.thePlayer.rotationYaw = snappedYaw;
        mc.thePlayer.rotationPitch = snappedPitch;

        PacketUtil.sendPacketNoEvent(new C03PacketPlayer.C05PacketPlayerLook(snappedYaw, snappedPitch, this.lastOnGround));
        this.cachedYaw = snappedYaw;
        this.cachedPitch = snappedPitch;
        this.hasCachedRotation = true;

        PacketUtil.sendPacketNoEvent(new C08PacketPlayerBlockPlacement(heldItem));
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.setEnabled(false);
    }

    @Override
    public boolean onEnabled() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            this.setEnabled(false);
            return false;
        }

        this.stuck = true;
        this.lastOnGround = mc.thePlayer.onGround;
        this.pearlPauseTicks = 0;
        this.cachedYaw = mc.thePlayer.rotationYaw;
        this.cachedPitch = mc.thePlayer.rotationPitch;
        this.hasCachedRotation = true;
        this.startStuck();
        return false;
    }

    @Override
    public void onDisabled() {
        this.stuck = false;
        this.pearlPauseTicks = 0;
        this.hasCachedRotation = false;
        this.stopStuck();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.disableOnHurt.getValue() ? "Safe" : "Raw"};
    }

    public static void throwPearl(float yaw, float pitch) {
        Stuck stuck = (Stuck) Myau.moduleManager.getModule(Stuck.class);
        if (stuck == null || !stuck.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        ItemStack heldItem = mc.thePlayer.getHeldItem();
        if (heldItem == null || !(heldItem.getItem() instanceof ItemEnderPearl)) {
            return;
        }

        stuck.sendPearlUse(heldItem, yaw, pitch);
        stuck.pauseForPearlThrow();
    }

    private static float snapRotation(float current, float target) {
        float sensitivity = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        float gcd = sensitivity * sensitivity * sensitivity * 1.2F;
        float delta = MathHelper.wrapAngleTo180_float(target - current);
        return current + delta - delta % gcd;
    }
}
