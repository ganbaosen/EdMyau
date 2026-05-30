package myau.module.modules;

import com.google.common.base.CaseFormat;
import io.netty.buffer.Unpooled;
import myau.Myau;
import myau.enums.BlinkModules;
import myau.enums.FloatModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LivingUpdateEvent;
import myau.events.PlayerUpdateEvent;
import myau.events.RightClickMouseEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.util.BlockUtil;
import myau.util.ItemUtil;
import myau.util.PacketUtil;
import myau.util.PlayerUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.util.BlockPos;

import java.util.Random;

public class NoSlow extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty swordMode = new ModeProperty("sword-mode", 1, new String[]{"NONE", "VANILLA", "PREDICTION-SEMI", "PREDICTION"});
    public final IntProperty swapDelay = new IntProperty("swap-delay", 0, 0, 3, () -> this.swordMode.getValue() == 3);
    public final BooleanProperty test = new BooleanProperty("test", false, () -> this.swordMode.getValue() == 3);
    public final BooleanProperty c17 = new BooleanProperty("c17-packet", false, () -> this.swordMode.getValue() == 3);
    public final IntProperty cancelTick = new IntProperty("cancel-tick", 1, 0, 2, () -> this.swordMode.getValue() == 2);
    public final IntProperty cancelTick2 = new IntProperty("cancel-tick2", 1, 0, 2, () -> this.swordMode.getValue() == 2);
    public final PercentProperty swordMotion = new PercentProperty("sword-motion", 100, () -> this.swordMode.getValue() != 0);
    public final BooleanProperty swordSprint = new BooleanProperty("sword-sprint", true, () -> this.swordMode.getValue() != 0);
    public final BooleanProperty onlyKillAuraAutoBlock = new BooleanProperty("only-killaura-autoblock", false, () -> this.swordMode.getValue() != 0);
    public final ModeProperty foodMode = new ModeProperty("food-mode", 0, new String[]{"NONE", "VANILLA", "FLOAT"});
    public final PercentProperty foodMotion = new PercentProperty("food-motion", 100, () -> this.foodMode.getValue() != 0);
    public final BooleanProperty foodSprint = new BooleanProperty("food-sprint", true, () -> this.foodMode.getValue() != 0);
    public final ModeProperty bowMode = new ModeProperty("bow-mode", 0, new String[]{"NONE", "VANILLA", "FLOAT"});
    public final PercentProperty bowMotion = new PercentProperty("bow-motion", 100, () -> this.bowMode.getValue() != 0);
    public final BooleanProperty bowSprint = new BooleanProperty("bow-sprint", true, () -> this.bowMode.getValue() != 0);
    private int lastSlot = -1;
    private int delay = 0;
    private boolean post = false;

    public NoSlow() {
        super("NoSlow", false);
    }

    public boolean isSwordActive() {
        return this.swordMode.getValue() != 0 && ItemUtil.isHoldingSword() && (!this.onlyKillAuraAutoBlock.getValue() || this.isKillAuraAutoBlocking());
    }

    public boolean isFoodActive() {
        return this.foodMode.getValue() != 0 && ItemUtil.isEating();
    }

    public boolean isBowActive() {
        return this.bowMode.getValue() != 0 && ItemUtil.isUsingBow();
    }

    public boolean isFloatMode() {
        return this.foodMode.getValue() == 2 && ItemUtil.isEating()
                || this.bowMode.getValue() == 2 && ItemUtil.isUsingBow();
    }

    private boolean isKillAuraAutoBlocking() {
        KillAura aura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (aura == null || !aura.isPlayerBlocking() || !aura.isEnabled()) {
            return false;
        }
        return aura.isBlocking();
    }

    public boolean isAnyActive() {
        if (this.swordMode.getValue() != 2 && this.swordMode.getValue() != 3) {
            return mc.thePlayer.isUsingItem() && (this.isSwordActive() || this.isFoodActive() || this.isBowActive());
        } else if (this.swordMode.getValue() == 2 && this.isSwordActive()) {
            KillAura killAura = (KillAura) Myau.moduleManager.getModule(KillAura.class);
            return killAura.isEnabled() && killAura.shouldAutoBlock()
                    && (killAura.blockTick == this.cancelTick.getValue() || killAura.blockTick == this.cancelTick2.getValue());
        } else if (this.swordMode.getValue() == 3 && this.isSwordActive()) {
            return this.delay == 0;
        }
        return false;
    }

    public boolean canSprint() {
        return this.isSwordActive() && this.swordSprint.getValue()
                || this.isFoodActive() && this.foodSprint.getValue()
                || this.isBowActive() && this.bowSprint.getValue();
    }

    public int getMotionMultiplier() {
        if (ItemUtil.isHoldingSword()) {
            return this.swordMotion.getValue();
        } else if (ItemUtil.isEating()) {
            return this.foodMotion.getValue();
        } else {
            return ItemUtil.isUsingBow() ? this.bowMotion.getValue() : 100;
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled()) return;
        if (ItemUtil.isHoldingSword() && mc.thePlayer.isUsingItem()) {
            if (this.isSwordActive()) {
                if (this.swordMode.getValue() == 3) {
                    if (event.getType() == EventType.PRE) {
                        this.delay--;
                        if (this.delay < 0) {
                            int randomSlot = new Random().nextInt(9);
                            while (randomSlot == mc.thePlayer.inventory.currentItem) {
                                randomSlot = new Random().nextInt(9);
                            }
                            if (this.test.getValue()) {
                                Myau.blinkManager.setBlinkState(true, BlinkModules.NO_SLOW);
                            }
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
                            if (this.c17.getValue()) {
                                PacketUtil.sendPacket(new C17PacketCustomPayload("woshijiejue", new PacketBuffer(Unpooled.buffer())));
                            }
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                            this.post = true;
                            this.delay = this.swapDelay.getValue();
                        }
                    } else if (event.getType() == EventType.POST) {
                        if (this.post) {
                            this.post = false;
                            if (this.test.getValue()) {
                                PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                                Myau.blinkManager.setBlinkState(false, BlinkModules.NO_SLOW);
                            }
                        }
                    }
                }
            }
        } else {
            if (this.post) {
                if (this.test.getValue()) {
                    int randomSlot = new Random().nextInt(9);
                    while (randomSlot == mc.thePlayer.inventory.currentItem) {
                        randomSlot = new Random().nextInt(9);
                    }
                    Myau.blinkManager.setBlinkState(false, BlinkModules.NO_SLOW);
                    PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
                    if (this.c17.getValue()) {
                        PacketUtil.sendPacket(new C17PacketCustomPayload("woshijiejue", new PacketBuffer(Unpooled.buffer())));
                    }
                    PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                }
                this.post = false;
            }
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled() && this.isAnyActive()) {
            float multiplier = (float) this.getMotionMultiplier() / 100.0F;
            mc.thePlayer.movementInput.moveForward *= multiplier;
            mc.thePlayer.movementInput.moveStrafe *= multiplier;
            if (!this.canSprint()) {
                mc.thePlayer.setSprinting(false);
            }
        }
    }

    @EventTarget(Priority.LOW)
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (this.isEnabled() && this.isFloatMode()) {
            int item = mc.thePlayer.inventory.currentItem;
            if (this.lastSlot != item && PlayerUtil.isUsingItem()) {
                this.lastSlot = item;
                Myau.floatManager.setFloatState(true, FloatModules.NO_SLOW);
            }
        } else {
            this.lastSlot = -1;
            Myau.floatManager.setFloatState(false, FloatModules.NO_SLOW);
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isEnabled()) {
            if (mc.objectMouseOver != null) {
                switch (mc.objectMouseOver.typeOfHit) {
                    case BLOCK:
                        BlockPos blockPos = mc.objectMouseOver.getBlockPos();
                        if (BlockUtil.isInteractable(blockPos) && !PlayerUtil.isSneaking()) {
                            return;
                        }
                        break;
                    case ENTITY:
                        Entity entityHit = mc.objectMouseOver.entityHit;
                        if (entityHit instanceof EntityVillager) {
                            return;
                        }
                        if (entityHit instanceof EntityLivingBase && TeamUtil.isShop((EntityLivingBase) entityHit)) {
                            return;
                        }
                }
            }
            if (this.isFloatMode() && !Myau.floatManager.isPredicted() && mc.thePlayer.onGround) {
                event.setCancelled(true);
                mc.thePlayer.motionY = 0.42F;
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.swordMode.getModeString())};
    }
}
