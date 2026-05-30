package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.LoadWorldEvent;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.util.ChatUtil;
import myau.util.TeamUtil;
import myau.util.TimerUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S0EPacketSpawnObject;
import net.minecraft.network.play.server.S24PacketBlockAction;
import net.minecraft.network.play.server.S45PacketTitle;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.Set;

public class ChestAura extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final String[] REFILL_SUBSTRINGS = new String[]{"refill", "reabastecidos"};
    private static final long OPEN_TRACK_MS = 2000L;
    private static final long TARGET_LOCK_MS = 350L;
    private static final DecimalFormat DISTANCE_FORMAT = new DecimalFormat("##0.00", new DecimalFormatSymbols(Locale.ENGLISH));

    private final TimerUtil timer = new TimerUtil();
    private final Set<BlockPos> clickedBlocks = new HashSet<>();
    private final Map<BlockPos, OpenRecord> chestOpenMap = new HashMap<>();

    public final BooleanProperty chest = new BooleanProperty("chest", true);
    public final BooleanProperty enderChest = new BooleanProperty("ender-chest", true);
    public final BooleanProperty antiScaffold = new BooleanProperty("anti-scaffold", true);
    public final FloatProperty range = new FloatProperty("range", 4.0F, 1.0F, 6.0F);
    public final IntProperty delay = new IntProperty("delay", 200, 50, 500);
    public final BooleanProperty throughWalls = new BooleanProperty("through-walls", true);
    public final FloatProperty wallsRange = new FloatProperty("walls-range", 3.0F, 1.0F, 5.0F, this.throughWalls::getValue);
    public final FloatProperty minDistanceFromOpponent = new FloatProperty("min-distance-from-opponent", 10.0F, 0.0F, 30.0F);
    public final BooleanProperty visualSwing = new BooleanProperty("visual-swing", true);
    public final BooleanProperty ignoreLooted = new BooleanProperty("ignore-looted", true);
    public final BooleanProperty detectRefill = new BooleanProperty("detect-refill", true);
    public final ModeProperty openInfo = new ModeProperty("open-info", 0, new String[]{"OFF", "SELF", "OTHER", "EVERYONE"});

    private TileTarget target;
    private TileTarget pendingOpenTarget;
    private float pendingOpenYaw;
    private float pendingOpenPitch;
    private BlockPos lockedTargetPos;
    private long lockedTargetUntil;

    public ChestAura() {
        super("ChestAura", false);
    }

    @Override
    public boolean onEnabled() {
        this.resetState();
        this.timer.setTime();
        return super.onEnabled();
    }

    @Override
    public void onDisabled() {
        this.resetState();
        super.onDisabled();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen instanceof GuiChest) {
            this.target = null;
            return;
        }
        if (this.antiScaffold.getValue() && Myau.moduleManager.getModule(Scaffold.class).isEnabled()) {
            this.target = null;
            return;
        }

        if (event.getType() != EventType.PRE) {
            return;
        }

        this.cleanupOpenMap();

        this.target = this.findTarget();
        if (this.target == null) {
            this.pendingOpenTarget = null;
            return;
        }

        float[] rotations = this.getRotations(this.target.clickVec);
        event.setRotation(rotations[0], rotations[1], 1);
        if (this.timer.hasTimeElapsed(this.delay.getValue())) {
            this.pendingOpenTarget = this.target;
            this.pendingOpenYaw = rotations[0];
            this.pendingOpenPitch = rotations[1];
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.POST) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen instanceof GuiChest) {
            this.pendingOpenTarget = null;
            return;
        }
        if (this.pendingOpenTarget == null) {
            return;
        }

        TileTarget openTarget = this.pendingOpenTarget;
        float yaw = this.pendingOpenYaw;
        float pitch = this.pendingOpenPitch;
        this.pendingOpenTarget = null;
        this.tryOpenTarget(openTarget, yaw, pitch);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE || mc.theWorld == null) {
            return;
        }

        if (this.detectRefill.getValue() && event.getPacket() instanceof S45PacketTitle) {
            S45PacketTitle packet = (S45PacketTitle) event.getPacket();
            if (this.isRefillTitle(packet)) {
                this.clickedBlocks.clear();
                this.chestOpenMap.clear();
            }
            return;
        }

        if (event.getPacket() instanceof S24PacketBlockAction) {
            this.handleBlockAction((S24PacketBlockAction) event.getPacket());
            return;
        }

        if (this.ignoreLooted.getValue() && event.getPacket() instanceof S0EPacketSpawnObject) {
            S0EPacketSpawnObject packet = (S0EPacketSpawnObject) event.getPacket();
            if (packet.getType() != 78) {
                return;
            }

            BlockPos pos = new BlockPos(packet.getX() / 32.0D, packet.getY() / 32.0D, packet.getZ() / 32.0D);
            TileEntity tile = mc.theWorld.getTileEntity(pos);
            if (tile instanceof TileEntityChest || tile instanceof TileEntityEnderChest) {
                this.markOpenedBlock(tile.getPos());
            }
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.resetState();
    }

    private void resetState() {
        this.target = null;
        this.pendingOpenTarget = null;
        this.clickedBlocks.clear();
        this.chestOpenMap.clear();
        this.lockedTargetPos = null;
        this.lockedTargetUntil = 0L;
    }

    private TileTarget findTarget() {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        BlockPos playerPos = mc.thePlayer.getPosition();
        double rangeSq = this.range.getValue() * this.range.getValue();
        double searchRadiusSq = (this.range.getValue() + 1.0F) * (this.range.getValue() + 1.0F);
        double wallsRangeSq = this.wallsRange.getValue() * this.wallsRange.getValue();
        long now = System.currentTimeMillis();
        if (this.lockedTargetPos != null && now <= this.lockedTargetUntil) {
            TileEntity lockedTile = mc.theWorld.getTileEntity(this.lockedTargetPos);
            TileTarget lockedTarget = lockedTile == null || !this.shouldClickTile(lockedTile) ? null : this.findBestClickTarget(lockedTile, eyes, rangeSq, wallsRangeSq);
            if (lockedTarget != null && !this.isOpponentNearby(lockedTarget.blockPos, this.minDistanceFromOpponent.getValue())) {
                return lockedTarget;
            }
        }
        if (this.target != null) {
            TileEntity currentTile = mc.theWorld.getTileEntity(this.target.blockPos);
            TileTarget currentTarget = currentTile == null || !this.shouldClickTile(currentTile) ? null : this.findBestClickTarget(currentTile, eyes, rangeSq, wallsRangeSq);
            if (currentTarget != null && !this.isOpponentNearby(currentTarget.blockPos, this.minDistanceFromOpponent.getValue())) {
                return currentTarget;
            }
        }

        TileTarget best = null;
        for (TileEntity tile : mc.theWorld.loadedTileEntityList) {
            if (!this.shouldClickTile(tile)) {
                continue;
            }
            if (tile.getPos().distanceSq(playerPos) > searchRadiusSq) {
                continue;
            }

            TileTarget candidate = this.findBestClickTarget(tile, eyes, rangeSq, wallsRangeSq);
            if (candidate == null) {
                continue;
            }

            if (this.minDistanceFromOpponent.getValue() > 0.0F && this.isOpponentNearby(tile.getPos(), this.minDistanceFromOpponent.getValue())) {
                continue;
            }

            if (best == null
                    || candidate.distanceSq < best.distanceSq
                    || (Math.abs(candidate.distanceSq - best.distanceSq) < 1.0E-4D
                    && candidate.centerDistanceSq < best.centerDistanceSq)) {
                best = candidate;
            }
        }
        return best;
    }

    private TileTarget findBestClickTarget(TileEntity tile, Vec3 eyes, double rangeSq, double wallsRangeSq) {
        AxisAlignedBB box = tile.getBlockType().getSelectedBoundingBox(mc.theWorld, tile.getPos());
        if (box == null) {
            box = new AxisAlignedBB(tile.getPos(), tile.getPos().add(1, 1, 1));
        }

        Vec3 center = new Vec3(tile.getPos().getX() + 0.5D, tile.getPos().getY() + 0.5D, tile.getPos().getZ() + 0.5D);
        double centerDistanceSq = eyes.squareDistanceTo(center);
        List<Vec3> candidatePoints = this.getCandidatePoints(box, eyes);
        candidatePoints.sort((first, second) -> Double.compare(eyes.squareDistanceTo(first), eyes.squareDistanceTo(second)));

        for (Vec3 point : candidatePoints) {
            double distanceSq = eyes.squareDistanceTo(point);
            if (distanceSq > rangeSq) {
                continue;
            }

            MovingObjectPosition trace = mc.theWorld.rayTraceBlocks(eyes, point, false, true, false);
            boolean visible = trace == null || trace.getBlockPos() == null || trace.getBlockPos().equals(tile.getPos());
            if (visible) {
                return new TileTarget(tile.getPos(), point, distanceSq, centerDistanceSq, true);
            }

            if (!this.throughWalls.getValue()) {
                continue;
            }

            double tracedDistanceSq = trace != null && trace.hitVec != null ? eyes.squareDistanceTo(trace.hitVec) : distanceSq;
            if (tracedDistanceSq <= wallsRangeSq) {
                return new TileTarget(tile.getPos(), point, distanceSq, centerDistanceSq, false);
            }
        }
        return null;
    }

    private boolean shouldClickTile(TileEntity tile) {
        if (tile == null || this.clickedBlocks.contains(tile.getPos())) {
            return false;
        }

        if (tile instanceof TileEntityChest) {
            if (!this.chest.getValue()) {
                return false;
            }
            Block block = mc.theWorld.getBlockState(tile.getPos()).getBlock();
            return block instanceof BlockChest && ((BlockChest) block).getLockableContainer(mc.theWorld, tile.getPos()) != null;
        }

        if (tile instanceof TileEntityEnderChest) {
            if (!this.enderChest.getValue()) {
                return false;
            }
            Block blockAbove = mc.theWorld.getBlockState(tile.getPos().up()).getBlock();
            return !blockAbove.isOpaqueCube();
        }

        return false;
    }

    private boolean isOpponentNearby(BlockPos pos, float minDistance) {
        double minDistanceSq = minDistance * minDistance;
        for (Object entityObj : mc.theWorld.playerEntities) {
            if (!(entityObj instanceof EntityPlayer)) {
                continue;
            }
            EntityPlayer player = (EntityPlayer) entityObj;
            if (player == mc.thePlayer || player.isDead || TeamUtil.isBot(player) || TeamUtil.isFriend(player) || TeamUtil.isSameTeam(player)) {
                continue;
            }
            if (player.getDistanceSq(pos) < minDistanceSq) {
                return true;
            }
        }
        return false;
    }

    private List<Vec3> getCandidatePoints(AxisAlignedBB box, Vec3 eyes) {
        List<Vec3> points = new ArrayList<>();
        points.add(myau.util.RotationUtil.clampVecToBox(eyes, box));

        for (double x = box.minX; x <= box.maxX + 1.0E-4D; x += 0.1D) {
            for (double y = box.minY; y <= box.maxY + 1.0E-4D; y += 0.1D) {
                for (double z = box.minZ; z <= box.maxZ + 1.0E-4D; z += 0.1D) {
                    points.add(new Vec3(
                            Math.min(x, box.maxX),
                            Math.min(y, box.maxY),
                            Math.min(z, box.maxZ)
                    ));
                }
            }
        }
        return points;
    }

    private float[] getRotations(Vec3 targetVec) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        double deltaX = targetVec.xCoord - eyes.xCoord;
        double deltaY = targetVec.yCoord - eyes.yCoord;
        double deltaZ = targetVec.zCoord - eyes.zCoord;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float yaw = (float) (Math.atan2(deltaZ, deltaX) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(deltaY, horizontalDistance) * 180.0D / Math.PI));
        return new float[]{yaw, pitch};
    }

    private MovingObjectPosition rayTraceTarget(float yaw, float pitch, BlockPos pos) {
        MovingObjectPosition trace = myau.util.RotationUtil.rayTrace(yaw, pitch, mc.playerController.getBlockReachDistance(), 1.0F);
        if (trace != null && trace.getBlockPos() != null && trace.getBlockPos().equals(pos)) {
            return trace;
        }
        return null;
    }

    private void tryOpenTarget(TileTarget openTarget, float yaw, float pitch) {
        TileEntity tile = mc.theWorld.getTileEntity(openTarget.blockPos);
        if (tile == null || !this.shouldClickTile(tile)) {
            return;
        }

        ItemStack heldItem = mc.thePlayer.getHeldItem();
        MovingObjectPosition hitResult = this.rayTraceTarget(yaw, pitch, openTarget.blockPos);
        if (hitResult == null || hitResult.getBlockPos() == null || !hitResult.getBlockPos().equals(openTarget.blockPos)) {
            return;
        }
        EnumFacing sideHit = hitResult.sideHit;
        Vec3 hitVec = hitResult.hitVec;
        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, heldItem, openTarget.blockPos, sideHit, hitVec)) {
            if (this.visualSwing.getValue()) {
                mc.thePlayer.swingItem();
            } else {
                mc.thePlayer.sendQueue.addToSendQueue(new net.minecraft.network.play.client.C0APacketAnimation());
            }
            this.markOpenedBlock(openTarget.blockPos);
            this.lockedTargetPos = openTarget.blockPos;
            this.lockedTargetUntil = System.currentTimeMillis() + TARGET_LOCK_MS;
            this.timer.reset();
        }
    }

    private void handleBlockAction(S24PacketBlockAction packet) {
        BlockPos pos = packet.getBlockPosition();
        TileEntity tile = mc.theWorld.getTileEntity(pos);
        if (!(tile instanceof TileEntityChest) && !(tile instanceof TileEntityEnderChest)) {
            return;
        }

        boolean opened = packet.getData1() > 0;
        long now = System.currentTimeMillis();
        OpenRecord previous = this.chestOpenMap.get(pos);
        boolean selfOpened = this.clickedBlocks.contains(pos)
                && previous != null
                && now - previous.time <= OPEN_TRACK_MS;
        this.chestOpenMap.put(pos, new OpenRecord(opened, now));

        String mode = this.openInfo.getModeString();
        if ("OFF".equalsIgnoreCase(mode)) {
            return;
        }
        if ("SELF".equalsIgnoreCase(mode) && !selfOpened) {
            return;
        }
        if ("OTHER".equalsIgnoreCase(mode) && selfOpened) {
            return;
        }

        String action = packet.getData1() > 0 ? "&a&lOpened&r" : "&c&lClosed&r";
        String owner = selfOpened ? "&bself&r" : "&eother&r";
        String chestType = tile instanceof TileEntityEnderChest ? "&5EnderChest&r" : "&6Chest&r";
        double distance = Math.sqrt(mc.thePlayer.getDistanceSq(pos));
        ChatUtil.sendFormatted(
                String.format(
                        "%s%s %s &7[%s | %d, %d, %d | %sm]&r",
                        Myau.clientName,
                        chestType,
                        action,
                        owner,
                        pos.getX(),
                        pos.getY(),
                        pos.getZ(),
                        DISTANCE_FORMAT.format(distance)
                )
        );
    }

    private void markOpenedBlock(BlockPos pos) {
        long now = System.currentTimeMillis();
        this.clickedBlocks.add(pos);
        this.chestOpenMap.put(pos, new OpenRecord(true, now));

        TileEntity tile = mc.theWorld == null ? null : mc.theWorld.getTileEntity(pos);
        if (!(tile instanceof TileEntityChest)) {
            return;
        }

        for (EnumFacing facing : new EnumFacing[]{EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST}) {
            BlockPos adjacentPos = pos.offset(facing);
            TileEntity adjacentTile = mc.theWorld.getTileEntity(adjacentPos);
            if (adjacentTile instanceof TileEntityChest) {
                this.clickedBlocks.add(adjacentPos);
                this.chestOpenMap.put(adjacentPos, new OpenRecord(true, now));
            }
        }
    }

    private void cleanupOpenMap() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<BlockPos, OpenRecord>> iterator = this.chestOpenMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, OpenRecord> entry = iterator.next();
            if (now - entry.getValue().time > OPEN_TRACK_MS) {
                iterator.remove();
            }
        }
        if (this.lockedTargetPos != null && now > this.lockedTargetUntil) {
            this.lockedTargetPos = null;
            this.lockedTargetUntil = 0L;
        }
    }

    private boolean isRefillTitle(S45PacketTitle packet) {
        if (packet.getMessage() == null) {
            return false;
        }

        String text = packet.getMessage().getUnformattedText();
        if (text == null) {
            return false;
        }

        text = text.toLowerCase(Locale.ROOT);
        for (String substring : REFILL_SUBSTRINGS) {
            if (text.contains(substring)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.format("%.1f", this.range.getValue())};
    }

    private static class TileTarget {
        private final BlockPos blockPos;
        private final Vec3 clickVec;
        private final double distanceSq;
        private final double centerDistanceSq;
        private final boolean visible;

        private TileTarget(BlockPos blockPos, Vec3 clickVec, double distanceSq, double centerDistanceSq, boolean visible) {
            this.blockPos = blockPos;
            this.clickVec = clickVec;
            this.distanceSq = distanceSq;
            this.centerDistanceSq = centerDistanceSq;
            this.visible = visible;
        }
    }

    private static class OpenRecord {
        private final boolean opened;
        private final long time;

        private OpenRecord(boolean opened, long time) {
            this.opened = opened;
            this.time = time;
        }
    }
}
