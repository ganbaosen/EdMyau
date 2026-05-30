package myau.util;

import myau.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

public class CombatTargeting {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static EntityPlayer findTarget(double rangeSq, boolean ignoreTeammates) {
        EntityPlayer target = null;
        double closestDistance = rangeSq;

        if (mc.theWorld == null || mc.thePlayer == null) {
            return null;
        }

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityPlayer)) {
                continue;
            }

            EntityPlayer player = (EntityPlayer) entity;

            if (player == mc.thePlayer) {
                continue;
            }

            if (player.isDead || player.getHealth() <= 0) {
                continue;
            }

            if (ignoreTeammates && mc.thePlayer.isOnSameTeam(player)) {
                continue;
            }

            double distance = mc.thePlayer.getDistanceSqToEntity(player);
            if (distance < closestDistance) {
                closestDistance = distance;
                target = player;
            }
        }

        return target;
    }
}
