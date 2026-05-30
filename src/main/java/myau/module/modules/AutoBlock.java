package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.*;
import myau.lag.api.EnumLagDirection;
import myau.lag.api.LagRequest;
import myau.lag.timeout.ModuleBackedTimeout;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.util.CombatTargeting;
import myau.util.PacketUtil;
import myau.util.ReflectionUtils;
import myau.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

public class AutoBlock extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final FloatProperty range;
    public final FloatProperty maxHurtTimeMs;
    public final FloatProperty maxHoldMs;
    public final BooleanProperty requireLmb;
    public final BooleanProperty requireRmb;
    public final BooleanProperty activeDefense;
    public final BooleanProperty onlyWhenDamaged;
    public final BooleanProperty ignoreTeammates;
    public final FloatProperty lagChance;
    public final FloatProperty lagMaxDuration;
    public final BooleanProperty preventDelayAttacks;
    public final BooleanProperty blockAgainImmediately;
    public final BooleanProperty forceBlockAnimation;

    private boolean isBlocking;
    private boolean manualBlock;
    private int blockStartTick = -1;
    private EntityPlayer currentTarget;
    private int lastSelfHurtTime;
    private boolean isLagging;
    private int lagStartTick = -1;
    private LagRequest outboundLag;
    private int tickCounter;

    public AutoBlock() {
        super("AutoBlock", false);

        this.range = new FloatProperty("Range", 4.0f, 2.0f, 6.0f);
        this.maxHurtTimeMs = new FloatProperty("Max Hurt Time", 200.0f, 50.0f, 500.0f);
        this.maxHoldMs = new FloatProperty("Max Hold Time", 150.0f, 50.0f, 500.0f);
        this.lagChance = new FloatProperty("Lag Chance", 100.0f, 0.0f, 100.0f);
        this.lagMaxDuration = new FloatProperty("Lag Max Duration", 200.0f, 50.0f, 500.0f);
        this.preventDelayAttacks = new BooleanProperty("Prevent Delay Attacks", true);
        this.blockAgainImmediately = new BooleanProperty("Block Again Immediately", true);
        this.forceBlockAnimation = new BooleanProperty("Force Block Animation", true);
        this.requireLmb = new BooleanProperty("Require LMB", false);
        this.requireRmb = new BooleanProperty("Require RMB", false);
        this.activeDefense = new BooleanProperty("Active Defense", true);
        this.onlyWhenDamaged = new BooleanProperty("Only When Damaged", false);
        this.ignoreTeammates = new BooleanProperty("Ignore Teammates", true);
    }

    @Override
    public boolean onEnabled() {
        this.tickCounter = 0;
        this.resetState(false);
        return super.onEnabled();
    }

    @Override
    public void onDisabled() {
        this.resetState(true);
        super.onDisabled();
    }

    private static int msToTicks(double ms) {
        if (ms <= 0.0) {
            return 0;
        }
        return (int) Math.ceil(ms / 50.0);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouse(MouseEvent e) {
        if (!Utils.nullCheck() || !Utils.holdingSword()) {
            return;
        }

        // Check if BedAura is mining
        Module bedAura = Myau.moduleManager.getModule("BedAura");
        if (bedAura != null && bedAura.isEnabled()) {
            // Assume it's mining if enabled - you may need to add a method to check
            return;
        }

        if (e.button == 1) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickMouse(RightClickMouseEvent e) {
        if (this.shouldBlockVanillaUse()) {
            e.setCancelled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onUseItem(UseItemEvent e) {
        if (this.shouldBlockVanillaUse()) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) {
            return;
        }
        if (!Utils.nullCheck()) {
            return;
        }

        Module bedAura = Myau.moduleManager.getModule("BedAura");
        if (bedAura != null && bedAura.isEnabled()) {
            return;
        }

        if (mc.currentScreen != null && (this.isBlocking || this.isLagging)) {
            this.resetState(true);
            return;
        }

        if (!this.forceBlockAnimation.getValue() || !Utils.holdingSword()) {
            return;
        }

        ReflectionUtils.setItemInUse(this.isBlocking || this.isLagging);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onSendPacket(SendPacketEvent e) {
        Module bedAura = Myau.moduleManager.getModule("BedAura");
        if (bedAura != null && bedAura.isEnabled()) {
            this.releaseLag();
            return;
        }

        if (!this.isLagging || !this.preventDelayAttacks.getValue()) {
            return;
        }

        if (!(e.getPacket() instanceof C02PacketUseEntity)) {
            return;
        }

        if (((C02PacketUseEntity) e.getPacket()).getAction() != C02PacketUseEntity.Action.ATTACK) {
            return;
        }

        this.releaseLag();
        if (this.blockAgainImmediately.getValue() && Utils.holdingSword()) {
            this.startBlocking(this.tickCounter);
        }
    }

    @EventTarget
    public void onPrePlayerInteract(PrePlayerInteractEvent e) {
        if (!Utils.nullCheck() || mc.thePlayer.isDead || mc.currentScreen != null) {
            this.resetState(true);
            return;
        }

        Module bedAura = Myau.moduleManager.getModule("BedAura");
        if (bedAura != null && bedAura.isEnabled()) {
            this.resetState(true);
            return;
        }

        int selfHurtTime = mc.thePlayer.hurtTime;
        boolean hurtAgain = selfHurtTime > this.lastSelfHurtTime;
        this.lastSelfHurtTime = selfHurtTime;

        if (!Utils.holdingSword()) {
            this.resetState(false);
            return;
        }

        ++this.tickCounter;
        int currentTick = this.tickCounter;

        this.currentTarget = CombatTargeting.findTarget(this.range.getValue() * this.range.getValue(),
                                                        this.ignoreTeammates.getValue());

        Module killAura = Myau.moduleManager.getModule("KillAura");
        boolean killAuraAttacking = killAura != null && killAura.isEnabled() && this.currentTarget != null;

        boolean rmbDown = Mouse.isButtonDown(1);
        boolean lmbDown = Mouse.isButtonDown(0) || killAuraAttacking;

        if (this.requireRmb.getValue() && !rmbDown) {
            this.resetState(true);
            return;
        }

        if (rmbDown && !lmbDown) {
            if (this.isLagging) {
                this.releaseLag();
            }
            if (!this.isBlocking) {
                this.startBlocking(currentTick);
                this.manualBlock = true;
            }
            return;
        }

        if (this.manualBlock) {
            this.stopBlocking(true);
            this.manualBlock = false;
        }

        boolean hasTarget = this.currentTarget != null;
        boolean activeDefenseThreat = hasTarget && this.activeDefense.getValue();
        boolean conditionsMet = hasTarget && this.checkConditions(lmbDown, rmbDown, activeDefenseThreat);

        if (this.isLagging) {
            int lagMaxTicks = msToTicks(this.lagMaxDuration.getValue());
            boolean lagExpired = lagMaxTicks > 0 && this.lagStartTick >= 0
                               && currentTick - this.lagStartTick >= lagMaxTicks;

            if (lagExpired || !conditionsMet) {
                this.releaseLag();
                if (lagExpired && this.blockAgainImmediately.getValue() && conditionsMet) {
                    this.startBlocking(currentTick);
                }
            }
        }

        if (!conditionsMet) {
            this.stopBlocking(true);
            return;
        }

        boolean shouldStart = this.onlyWhenDamaged.getValue() ? this.shouldPredictiveBlock() : true;

        if (!this.isBlocking && !this.isLagging && shouldStart) {
            this.startBlocking(currentTick);
        }

        if (this.isBlocking) {
            int maxHoldTicks = msToTicks(this.maxHoldMs.getValue());
            boolean timeExpired = maxHoldTicks > 0 && this.blockStartTick >= 0
                                && currentTick - this.blockStartTick >= maxHoldTicks;

            boolean shouldStop = timeExpired;
            if (this.onlyWhenDamaged.getValue() && hurtAgain) {
                shouldStop = true;
            }

            if (shouldStop) {
                if (this.shouldStartLag()) {
                    this.startLag(currentTick);
                }
                this.stopBlocking(true);
            }
        }
    }

    private boolean checkConditions(boolean lmbDown, boolean rmbDown, boolean activeDefenseThreat) {
        if (this.requireLmb.getValue() && !lmbDown && !activeDefenseThreat) {
            return false;
        }
        return !this.requireRmb.getValue() || rmbDown;
    }

    private boolean shouldPredictiveBlock() {
        int ourHurtTime = mc.thePlayer.hurtTime;
        int triggerTick = (int) Math.round(this.maxHurtTimeMs.getValue() / 50.0);
        triggerTick = Math.max(1, Math.min(10, triggerTick));
        return ourHurtTime == triggerTick;
    }

    private boolean shouldBlockVanillaUse() {
        return this.isEnabled() && this.isLagging && Utils.nullCheck()
               && Utils.holdingSword() && mc.currentScreen == null;
    }

    private void startBlocking(int currentTick) {
        if (this.isBlocking || !Utils.holdingSword()) {
            return;
        }

        ItemStack heldItem = mc.thePlayer.getHeldItem();
        if (heldItem == null) {
            return;
        }

        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(heldItem));
        mc.thePlayer.setItemInUse(heldItem, heldItem.getMaxItemUseDuration());

        int keyCode = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(keyCode, true);
        KeyBinding.onTick(keyCode);

        this.isBlocking = true;
        this.blockStartTick = currentTick;
    }

    private void stopBlocking(boolean forceRelease) {
        if (!this.isBlocking && !forceRelease) {
            return;
        }

        boolean wasBlocking = this.isBlocking || mc.thePlayer != null && mc.thePlayer.isBlocking();

        int keyCode = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(keyCode, false);

        if (wasBlocking && Utils.nullCheck()) {
            PacketUtil.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                                                            BlockPos.ORIGIN,
                                                            EnumFacing.DOWN));
            mc.thePlayer.stopUsingItem();
        }

        this.isBlocking = false;
        this.blockStartTick = -1;
    }

    private boolean shouldStartLag() {
        double chance = this.lagChance.getValue();
        if (chance <= 0.0) {
            return false;
        }
        if (chance >= 100.0) {
            return true;
        }
        return Math.random() * 100.0 < chance;
    }

    private void startLag(int currentTick) {
        if (this.isLagging) {
            return;
        }

        int lagReferenceTick = this.blockStartTick >= 0 ? this.blockStartTick : currentTick;
        int lagMaxTicks = msToTicks(this.lagMaxDuration.getValue());

        if (lagMaxTicks > 0 && currentTick - lagReferenceTick >= lagMaxTicks) {
            return;
        }

        this.outboundLag = new LagRequest(EnumLagDirection.ONLY_OUTBOUND,
                                         new ModuleBackedTimeout(this));
        Myau.lagHandler.requestLag(this.outboundLag);

        this.isLagging = true;
        this.lagStartTick = lagReferenceTick;
    }

    private void releaseLag() {
        if (!this.isLagging) {
            return;
        }

        if (this.outboundLag != null) {
            this.outboundLag.getTimeout().forceTimeOut();
            this.outboundLag = null;
        }

        this.isLagging = false;
        this.lagStartTick = -1;
    }

    public boolean isActive() {
        return this.isEnabled() && (this.isBlocking || this.isLagging);
    }

    private void resetState(boolean releaseUseKey) {
        boolean wasActive = this.isBlocking || this.isLagging;

        this.releaseLag();
        this.stopBlocking(releaseUseKey);
        this.manualBlock = false;

        if (this.forceBlockAnimation.getValue() && wasActive) {
            ReflectionUtils.setItemInUse(false);
        }

        if (Mouse.isButtonDown(1) && mc.currentScreen == null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
        }

        this.currentTarget = null;
        this.lastSelfHurtTime = 0;
    }
}
