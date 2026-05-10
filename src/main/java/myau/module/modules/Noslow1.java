package myau.module.modules;

import io.netty.buffer.Unpooled;
import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LivingUpdateEvent;
import myau.events.PacketEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ModeProperty;
import myau.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.network.play.server.S30PacketWindowItems;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public class Noslow1 extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final BlockPos USE_ITEM_POS = new BlockPos(-1, -1, -1);

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"Vanilla", "HYT", "Grim2365", "UpdatedNCP", "Blink"});
    public final BooleanProperty sprint = new BooleanProperty("sprint", false);
    public final BooleanProperty sword = new BooleanProperty("sword", true);
    public final BooleanProperty food = new BooleanProperty("food", true);
    public final BooleanProperty potion = new BooleanProperty("potion", true);
    public final BooleanProperty bow = new BooleanProperty("bow", true);

    private boolean shouldSwap;
    private boolean usingItem;
    private int usingItemTick;

    public Noslow1() {
        super("Noslow1", false);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            this.stopBlink();
            this.usingItemTick = 0;
            return;
        }

        if (event.getType() == EventType.PRE) {
            if (mc.thePlayer.isUsingItem()) {
                ++this.usingItemTick;
            } else {
                this.usingItemTick = 0;
            }
        }

        switch (this.mode.getValue()) {
            case 1:
                this.handleHyt(event);
                break;
            case 2:
                break;
            case 3:
                this.handleUpdatedNcp(event);
                break;
            case 4:
                this.handleBlink(event);
                break;
            default:
                this.stopBlink();
                break;
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onGrimUpdate(UpdateEvent event) {
        if (!this.isEnabled() || this.mode.getValue() != 2 || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        this.handleGrim2365(event);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        if (event.getType() == EventType.SEND && this.isUpdatedNcp() && event.getPacket() instanceof C08PacketPlayerBlockPlacement) {
            this.shouldSwap = true;
            return;
        }

        if (!this.isHyt()) {
            return;
        }

        Packet<?> packet = event.getPacket();
        ItemStack heldItem = mc.thePlayer.getHeldItem();
        if (heldItem == null || !(heldItem.getItem() instanceof ItemFood) || !this.food.getValue()) {
            return;
        }

        if (event.getType() == EventType.SEND && packet instanceof C08PacketPlayerBlockPlacement) {
            C08PacketPlayerBlockPlacement placement = (C08PacketPlayerBlockPlacement) packet;
            if (placement.getPlacedBlockDirection() == 255
                    && USE_ITEM_POS.equals(placement.getPosition())
                    && heldItem.stackSize >= 2
                    && heldItem.getItemDamage() == 0) {
                PacketUtil.sendPacketNoEvent(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.DROP_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
            }
        } else if (event.getType() == EventType.SEND && packet instanceof C07PacketPlayerDigging) {
            C07PacketPlayerDigging digging = (C07PacketPlayerDigging) packet;
            if (digging.getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM && heldItem.getItemDamage() == 0) {
                event.setCancelled(true);
            }
        } else if (event.getType() == EventType.RECEIVE
                && mc.thePlayer.isUsingItem()
                && (packet instanceof S30PacketWindowItems || packet instanceof S2FPacketSetSlot)) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!this.isEnabled() || !this.isAnyActive()) {
            return;
        }

        if (!this.canSprint()) {
            mc.thePlayer.setSprinting(false);
        }
    }

    private void handleHyt(UpdateEvent event) {
        ItemStack heldItem = mc.thePlayer.getHeldItem();
        if (heldItem == null) {
            return;
        }

        if (event.getType() == EventType.PRE) {
            if (mc.thePlayer.isUsingItem() && !this.isEating(heldItem) && mc.thePlayer.getItemInUseCount() < 25) {
                mc.thePlayer.stopUsingItem();
            }

            if (this.isSwordStack(heldItem) && this.sword.getValue() && mc.thePlayer.isUsingItem() && !this.isKillAuraBlocking()) {
                PacketUtil.sendPacketNoEvent(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
            }

            if (this.isBowStack(heldItem) && this.bow.getValue() && mc.thePlayer.isUsingItem() && this.isMoving()) {
                int nextSlot = (mc.thePlayer.inventory.currentItem + 1) % 9;
                PacketUtil.sendPacketNoEvent(new C09PacketHeldItemChange(nextSlot));
                PacketUtil.sendPacketNoEvent(new C17PacketCustomPayload("test", new PacketBuffer(Unpooled.buffer(0))));
                PacketUtil.sendPacketNoEvent(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
            }
        } else if (event.getType() == EventType.POST
                && this.isSwordStack(heldItem)
                && this.sword.getValue()
                && mc.thePlayer.isUsingItem()
                && !this.isKillAuraBlocking()) {
            this.sendUseItem(heldItem);
        }
    }

    private void handleGrim2365(UpdateEvent event) {
        if (event.getType() != EventType.PRE || !mc.thePlayer.isUsingItem() || !this.isAnyActive()) {
            return;
        }

        ItemStack heldItem = mc.thePlayer.getHeldItem();
        if (heldItem == null) {
            return;
        }

        int currentSlot = mc.thePlayer.inventory.currentItem;
        for (int slot = 0; slot < 8; ++slot) {
            if (slot == currentSlot) {
                continue;
            }

            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (this.isGrimSpoofStack(stack)) {
                PacketUtil.sendPacketNoEvent(new C09PacketHeldItemChange(slot));
                PacketUtil.sendPacketNoEvent(new C08PacketPlayerBlockPlacement(heldItem));
                PacketUtil.sendPacketNoEvent(new C09PacketHeldItemChange(currentSlot));
                return;
            }
        }
    }

    private void handleUpdatedNcp(UpdateEvent event) {
        ItemStack heldItem = mc.thePlayer.getHeldItem();
        if (heldItem == null) {
            this.shouldSwap = false;
            return;
        }

        if (event.getType() == EventType.POST && this.isSwordStack(heldItem) && this.sword.getValue() && (mc.thePlayer.isUsingItem() || this.isKillAuraBlocking())) {
            this.sendUseItem(heldItem);
            return;
        }

        if (event.getType() == EventType.PRE && this.shouldSwap && mc.thePlayer.isUsingItem() && this.isConsumableActive(heldItem)) {
            this.updateSlot();
            this.sendUseItem(heldItem);
            this.shouldSwap = false;
        }
    }

    private void handleBlink(UpdateEvent event) {
        if (event.getType() != EventType.PRE) {
            return;
        }

        boolean shouldBlink = mc.thePlayer.isUsingItem() && this.isAnyActive();
        if (shouldBlink) {
            this.usingItem = true;
            Myau.blinkManager.setBlinkState(true, BlinkModules.NO_SLOW);
        } else if (this.usingItem) {
            this.usingItem = false;
            this.stopBlink();
        }
    }

    private void sendUseItem(ItemStack stack) {
        PacketUtil.sendPacketNoEvent(new C08PacketPlayerBlockPlacement(USE_ITEM_POS, 255, stack, 0.0F, 0.0F, 0.0F));
    }

    private void updateSlot() {
        int slot = mc.thePlayer.inventory.currentItem;
        int nextSlot = slot == 8 ? 7 : slot + 1;
        PacketUtil.sendPacketNoEvent(new C09PacketHeldItemChange(nextSlot));
        PacketUtil.sendPacketNoEvent(new C09PacketHeldItemChange(slot));
    }

    private void stopBlink() {
        if (Myau.blinkManager != null && Myau.blinkManager.getBlinkingModule() == BlinkModules.NO_SLOW) {
            Myau.blinkManager.setBlinkState(false, BlinkModules.NO_SLOW);
        }
        this.usingItem = false;
    }

    private boolean isAnyActive(ItemStack stack) {
        return stack != null && (this.isSwordActive(stack) || this.isFoodActive(stack) || this.isPotionActive(stack) || this.isBowActive(stack));
    }

    public boolean isAnyActive() {
        return mc.thePlayer != null && mc.thePlayer.isUsingItem() && this.isAnyActive(mc.thePlayer.getHeldItem());
    }

    public boolean canSprint() {
        return this.sprint.getValue();
    }

    private boolean isConsumableActive(ItemStack stack) {
        return this.isFoodActive(stack) || this.isPotionActive(stack) || this.isBowActive(stack);
    }

    private boolean isFoodActive(ItemStack stack) {
        return this.food.getValue() && stack != null && stack.getItem() instanceof ItemFood;
    }

    private boolean isPotionActive(ItemStack stack) {
        return this.potion.getValue() && stack != null && stack.getItem() instanceof ItemPotion;
    }

    private boolean isSwordActive(ItemStack stack) {
        return this.sword.getValue() && this.isSwordStack(stack);
    }

    private boolean isBowActive(ItemStack stack) {
        return this.bow.getValue() && this.isBowStack(stack);
    }

    private boolean isSwordStack(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemSword;
    }

    private boolean isBowStack(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemBow;
    }

    private boolean isEating(ItemStack stack) {
        return stack != null && (stack.getItem() instanceof ItemFood || stack.getItem() instanceof ItemPotion);
    }

    private boolean isGrimSpoofStack(ItemStack stack) {
        if (stack == null) {
            return false;
        }

        Item item = stack.getItem();
        return !(item instanceof ItemFood)
                && !(item instanceof ItemPotion)
                && !(item instanceof ItemSword)
                && !(item instanceof ItemEgg)
                && !(item instanceof ItemSnowball)
                && !(item instanceof ItemEnderPearl);
    }

    private boolean isMoving() {
        return mc.thePlayer.movementInput.moveForward != 0.0F || mc.thePlayer.movementInput.moveStrafe != 0.0F;
    }

    private boolean isKillAuraBlocking() {
        KillAura killAura = (KillAura) Myau.moduleManager.getModule(KillAura.class);
        return killAura != null && killAura.isEnabled() && killAura.isPlayerBlocking();
    }

    private boolean isHyt() {
        return this.mode.getValue() == 1;
    }

    private boolean isUpdatedNcp() {
        return this.mode.getValue() == 3;
    }

    @Override
    public void onDisabled() {
        this.stopBlink();
        this.shouldSwap = false;
        this.usingItemTick = 0;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}
