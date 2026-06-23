package com.example.addon.modules;

import net.minecraft.block.Blocks;
import meteordevelopment.meteorclient.systems.friends.Friends;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.entity.player.PlayerEntity;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.BlockHitResult;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import com.example.addon.GunniCategory;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public class SpeedMine extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed").description("The speed at which the module will mine blocks.")
        .defaultValue(1.0)
        .min(0.7)
        .sliderRange(0.7, 1.0)
        .build());
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range").description("The maximum distance at which blocks will be mined.")
        .defaultValue(6.0)
        .min(0.0)
        .sliderMax(8.0)
        .build());
    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch").description("Automatically switches to the fastest tool.")
        .defaultValue(true)
        .build());
    private final Setting<Boolean> doubleMine = sgGeneral.add(new BoolSetting.Builder()
        .name("double-mine").description("Allows mining of 2 blocks at the same time.")
        .defaultValue(false)
        .build());
    private final Setting<Boolean> instant = sgGeneral.add(new BoolSetting.Builder()
        .name("instant").description("Instantly mines blocks once they have been replaced.")
        .defaultValue(false)
        .build());
    private final Setting<Boolean> auto = sgGeneral.add(new BoolSetting.Builder()
        .name("auto").description("Automatically mines blocks near enemy players.")
        .defaultValue(false)
        .build());
    private final Setting<Boolean> holeCheck = sgGeneral.add(new BoolSetting.Builder()
        .name("hole-check").description("Only mine the player when they are in a hole.")
        .defaultValue(false)
        .build());
    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate").description("Sends a packet rotation when mining a block.")
        .defaultValue(true)
        .build());
    private final Setting<Boolean> whileEating = sgGeneral.add(new BoolSetting.Builder()
        .name("while-eating").description("Mines blocks while eating.")
        .defaultValue(true)
        .build());
    private final Setting<Boolean> strict = sgGeneral.add(new BoolSetting.Builder()
        .name("strict").description("Waits for the server to tick before switching back.")
        .defaultValue(false)
        .build());
    private final Setting<Boolean> grim = sgGeneral.add(new BoolSetting.Builder()
        .name("grim").description("Adds a bypass for the Grim anticheat.")
        .defaultValue(false)
        .build());

    private BlockPos primaryPos = null;
    private BlockPos secondaryPos = null;
    private Direction primaryDir = null;
    private Direction secondaryDir = null;
    private float primaryProgress = 0;
    private float secondaryProgress = 0;
    private int prevSlot = -1;

    public SpeedMine() {
        super(GunniCategory.CATEGORY, "gunni-speedmine", "Mines blocks faster using packets.");
    }

    public void onActivate() {
        primaryPos = null;
        secondaryPos = null;
        primaryDir = null;
        secondaryDir = null;
        primaryProgress = 0.0f;
        secondaryProgress = 0.0f;
        prevSlot = -1;
    }

    public void onDeactivate() {
        if (primaryPos != null && primaryDir != null) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, primaryPos, primaryDir));
        }

        if (secondaryPos != null && secondaryDir != null) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, secondaryPos, secondaryDir));
        }

        if (prevSlot != -1) {
            InvUtils.swap(prevSlot, false);
            prevSlot = -1;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (!whileEating.get() && mc.player.isUsingItem()) return;

        if (auto.get()) {
            handleAuto();
            return;
        }

        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof BlockHitResult)) return;

        BlockHitResult blockHit = (BlockHitResult) hit;
        if (blockHit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = blockHit.getBlockPos();
        Direction dir = blockHit.getSide();

        if (!isValidBlock(pos)) return;
        if (mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) > MathHelper.square(range.get())) return;

        handleMining(pos, dir);
    }

    private void handleAuto() {
        PlayerEntity target = getTarget();
        if (target == null) return;

        BlockPos targetPos = getTargetPos(target);
        if (targetPos == null) return;

        Direction dir = getDirection(targetPos);
        handleMining(targetPos, dir);
    }

    private void handleMining(BlockPos pos, Direction dir) {
        if (!pos.equals(primaryPos)) {
            if (doubleMine.get() && primaryPos != null) {
                secondaryPos = primaryPos;
                secondaryDir = primaryDir;
                secondaryProgress = primaryProgress;
            } else {
                cancelMining(primaryPos, primaryDir);
            }

            primaryPos = pos;
            primaryDir = dir;
            primaryProgress = 0.0f;
            startMining(pos, dir);
        }

        if (autoSwitch.get()) {
            int bestSlot = getBestSlot(pos);
            if (bestSlot != -1) {
                prevSlot = mc.player.getInventory().getSelectedSlot();
                InvUtils.swap(bestSlot, false);
            }
        }

        if (rotate.get()) Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), 50, null);

        float delta = mc.world.getBlockState(pos).calcBlockBreakingDelta(mc.player, mc.world, pos);
        primaryProgress += delta * speed.get().floatValue();

        if (primaryProgress >= 1.0f) {
            finishMining(pos, dir);
            primaryPos = null;
            primaryProgress = 0.0f;

            if (prevSlot != -1 && !strict.get()) {
                InvUtils.swap(prevSlot, false);
                prevSlot = -1;
            }
        }
        if (doubleMine.get() && secondaryPos != null) {
            float secondaryDelta = mc.world.getBlockState(secondaryPos).calcBlockBreakingDelta(mc.player, mc.world, secondaryPos);
            secondaryProgress += secondaryDelta * speed.get().floatValue();

            if (secondaryProgress >= 1.0f) {
                finishMining(secondaryPos, secondaryDir);
                secondaryPos = null;
                secondaryProgress = 0.0f;
            }
        }
    }

    private void startMining(BlockPos pos, Direction dir) {
        if (doubleMine.get()) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, dir));
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, dir));
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, dir));
        } else {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, dir));
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, pos, dir));
        }
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
    }

    private void finishMining(BlockPos pos, Direction dir) {
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, dir));
        if (grim.get()) mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, pos.up(500), dir));

        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        if (instant.get()) startMining(pos, dir);
    }

    private void cancelMining(BlockPos pos, Direction dir) {
        if (pos == null || dir == null) return;

        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, pos, dir));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
    }

    private int getBestSlot(BlockPos pos) {
        int bestSlot = -1;
        float bestSpeed = -1.0f;

        for (int i = 0; i < 9; ++i) {
            float s = mc.player.getInventory().getStack(i).getMiningSpeedMultiplier(mc.world.getBlockState(pos));

            if (s > bestSpeed) {
                bestSpeed = s;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private PlayerEntity getTarget() {
        PlayerEntity target = null;
        double nearestDist = Double.MAX_VALUE;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (!player.isAlive()) continue;
            if (player.getHealth() <= 0.0f) continue;
            if (Friends.get().isFriend(player)) continue;
            if (mc.player.squaredDistanceTo(player) > MathHelper.square( range.get() + 2.0)) continue;

            double dist = mc.player.squaredDistanceTo(player);
            if (dist >= nearestDist) continue;

            nearestDist = dist;
            target = player;
        }
        return target;
    }

    private BlockPos getTargetPos(PlayerEntity target) {
        BlockPos[] surroundOffsets = {new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0), new BlockPos(0, 0, 1), new BlockPos(0, 0, -1)};
        BlockPos feet = target.getBlockPos();

        for (BlockPos offset : surroundOffsets) {
            BlockPos pos = feet.add(offset);
            if (isValidBlock(pos)) {
                if (mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) <= MathHelper.square(range.get())) return pos;
            }
        }
        return null;
    }

    private boolean isValidBlock(BlockPos pos) {
        return !mc.world.getBlockState(pos).isAir() && mc.world.getBlockState(pos).getBlock().getHardness() != -1.0f && !mc.world.getBlockState(pos).getBlock().equals(Blocks.COBWEB);
    }

    private Direction getDirection(BlockPos pos) {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d center = Vec3d.ofCenter(pos);
        Direction best = Direction.UP;
        double bestDist = Double.MAX_VALUE;

        for (Direction dir : Direction.values()) {
            Vec3d face = center.add(dir.getOffsetX() * 0.5, dir.getOffsetY() * 0.5, dir.getOffsetZ() * 0.5);
            double dist = eyePos.squaredDistanceTo(face);

            if (dist < bestDist) {
                bestDist = dist;
                best = dir;
            }
        }
        return best;
    }
}
