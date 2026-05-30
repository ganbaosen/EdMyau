package myau.util;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

public class Utils {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static boolean nullCheck() {
        return mc.theWorld != null && mc.thePlayer != null;
    }

    public static boolean holdingSword() {
        if (!nullCheck()) {
            return false;
        }
        ItemStack heldItem = mc.thePlayer.getHeldItem();
        return heldItem != null && heldItem.getItem() instanceof ItemSword;
    }
}
