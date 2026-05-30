package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.MoveInputEvent;
import myau.events.PacketEvent;
import myau.events.Render2DEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.util.ChatUtil;
import myau.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.ItemAppleGold;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;

import java.awt.Color;

public class AutoGapple extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final IntProperty c03Packets = new IntProperty("c03-packets", 32, 32, 40);
    public final IntProperty health = new IntProperty("health", 12, 1, 40);
    public final BooleanProperty autoEat = new BooleanProperty("auto-eat", true);
    public final BooleanProperty progressBar = new BooleanProperty("progress-bar", true);
    public final BooleanProperty alwaysAttack = new BooleanProperty("always-attack", false);

    private double savedMotionX;
    private double savedMotionY;
    private double savedMotionZ;
    private boolean motionSaved;
    private boolean cancelMove;
    private int ticks;
    private int pauseTicks;
    private float yaw;
    private float pitch;
    private boolean shouldEat;
    private boolean eating;
    private float lastHealth = 40.0F;
    private int gappleSlot = -1;

    public AutoGapple() {
        super("Gapple", false, false);
    }

    @Override
    public boolean onEnabled() {
        this.resetState();
        return super.onEnabled();
    }

    @Override
    public void onDisabled() {
        this.resetState();
        super.onDisabled();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || event.getType() != EventType.SEND) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (!(packet instanceof C03PacketPlayer) || !this.cancelMove || this.ticks >= this.c03Packets.getValue()) {
            return;
        }

        this.yaw = mc.thePlayer.rotationYaw;
        this.pitch = mc.thePlayer.rotationPitch;
        ++this.ticks;
        event.setCancelled(true);
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
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null || !mc.thePlayer.isEntityAlive()) {
            this.resetState();
            return;
        }
        if (!mc.playerController.getCurrentGameType().isSurvivalOrAdventure()) {
            this.resetState();
            return;
        }

        this.yaw = event.getNewYaw();
        this.pitch = event.getNewPitch();
        this.gappleSlot = this.findGappleSlot();
        boolean wantsEat = this.checkHealthCondition();

        if (wantsEat && this.gappleSlot >= 0) {
            if (this.pauseTicks == 0) {
                this.startStuck();
            } else {
                this.stopStuck();
                --this.pauseTicks;
            }

            if (this.ticks >= this.c03Packets.getValue()) {
                this.consumeGappleBurst();
            }
            return;
        }

        this.stopStuck();
        this.ticks = 0;
        this.pauseTicks = 0;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || !this.eating || !this.progressBar.getValue() || mc.currentScreen != null) {
            return;
        }

        ScaledResolution sr = new ScaledResolution(mc);
        int width = 140;
        int barHeight = 7;
        int x = sr.getScaledWidth() / 2 - width / 2;
        int y = sr.getScaledHeight() * 3 / 4;
        float progress = Math.min(1.0F, (float) this.ticks / (float) this.c03Packets.getValue());
        int filled = x + Math.round(width * progress);

        Gui.drawRect(x - 2, y - 2, x + width + 2, y + barHeight + 2, new Color(0, 0, 0, 140).getRGB());
        Gui.drawRect(x, y, x + width, y + barHeight, new Color(20, 20, 20, 180).getRGB());
        if (filled > x) {
            Gui.drawRect(x, y, filled, y + barHeight, new Color(76, 157, 240, 220).getRGB());
        }

        String text = "Gapple " + (int) (progress * 100.0F) + "%";
        mc.fontRendererObj.drawStringWithShadow(
                text,
                sr.getScaledWidth() / 2.0F - mc.fontRendererObj.getStringWidth(text) / 2.0F,
                y - 10.0F,
                -1
        );
    }

    public boolean isEatingGapple() {
        return this.eating;
    }

    public boolean isSilentEating() {
        return this.eating;
    }

    public int getGappleSlot() {
        return this.gappleSlot;
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

    private void releaseBufferedPackets() {
        PacketUtil.sendPacketNoEvent(new C03PacketPlayer.C05PacketPlayerLook(this.yaw, this.pitch, mc.thePlayer.onGround));
        for (int i = 1; i < this.ticks; ++i) {
            PacketUtil.sendPacketNoEvent(new C03PacketPlayer(mc.thePlayer.onGround));
        }
    }

    private void consumeGappleBurst() {
        int currentSlot = mc.thePlayer.inventory.currentItem;
        ItemStack gappleStack = mc.thePlayer.inventory.getStackInSlot(this.gappleSlot);
        if (gappleStack == null || !(gappleStack.getItem() instanceof ItemAppleGold)) {
            this.gappleSlot = -1;
            return;
        }

        PacketUtil.sendPacketNoEvent(new C09PacketHeldItemChange(this.gappleSlot));
        PacketUtil.sendPacketNoEvent(new C08PacketPlayerBlockPlacement(gappleStack));
        this.releaseBufferedPackets();
        PacketUtil.sendPacketNoEvent(new C09PacketHeldItemChange(currentSlot));

        ItemStack heldStack = mc.thePlayer.inventory.getStackInSlot(currentSlot);
        if (heldStack != null) {
            PacketUtil.sendPacketNoEvent(new C08PacketPlayerBlockPlacement(heldStack));
        }

        ChatUtil.sendRaw("&7[&9袁&d子&f晨&7]&r 袁子晨先生帮你吃了一个苹果");
        ++this.pauseTicks;
        this.ticks = 0;
    }

    private int findGappleSlot() {
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemAppleGold) {
                return i;
            }
        }
        return -1;
    }

    private boolean checkHealthCondition() {
        if (!this.autoEat.getValue()) {
            this.shouldEat = false;
            this.eating = false;
            return false;
        }

        float currentHealth = mc.thePlayer.getHealth();
        float maxHealth = mc.thePlayer.getMaxHealth();
        if (currentHealth <= this.health.getValue()) {
            this.shouldEat = true;
            this.eating = true;
        }

        this.lastHealth = currentHealth;
        return this.shouldEat;
    }

    private void resetState() {
        this.stopStuck();
        this.ticks = 0;
        this.pauseTicks = 0;
        this.shouldEat = false;
        this.eating = false;
        this.gappleSlot = -1;
        this.lastHealth = 40.0F;
    }

    @Override
    public String[] getSuffix() {
        return this.eating ? new String[]{String.valueOf(this.ticks)} : new String[0];
    }
}
