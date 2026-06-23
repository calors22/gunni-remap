package com.example.addon.modules;

import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import net.minecraft.util.hit.HitResult;
import meteordevelopment.meteorclient.mixininterface.IRaycastContext;
import net.minecraft.util.math.Direction;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.mixininterface.IBox;
import net.minecraft.block.Blocks;
import java.util.concurrent.atomic.AtomicReference;
import com.google.common.util.concurrent.AtomicDouble;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import net.minecraft.entity.decoration.EndCrystalEntity;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.orbit.EventHandler;
import it.unimi.dsi.fastutil.ints.IntIterator;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.entity.Entity;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import com.example.addon.GunniCategory;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.item.Item;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public class AutoCharged extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup(); // -
    private final SettingGroup sgPlace = settings.createGroup("Place");
    private final SettingGroup sgBreak = settings.createGroup("Break");

    private final Setting<Boolean> doPlace = sgPlace.add(new BoolSetting.Builder()
        .name("place").description("Places crystals.")
        .defaultValue(true)
        .build());
    public final Setting<Integer> placeDelay = sgPlace.add(new IntSetting.Builder()
        .name("place-delay").description("Delay in ticks between placements.")
        .defaultValue(0)
        .min(0)
        .sliderMax(20)
        .build());
    private final Setting<Double> placeRange = sgPlace.add(new DoubleSetting.Builder()
        .name("place-range").description("Range in which to place crystals.")
        .defaultValue(4.5)
        .min(0.0)
        .sliderMax(6.0)
        .build());
    private final Setting<Boolean> doBreak = sgBreak.add(new BoolSetting.Builder()
        .name("break").description("Breaks crystals.")
        .defaultValue(true)
        .build());
    private final Setting<Integer> breakDelay = sgBreak.add(new IntSetting.Builder()
        .name("break-delay").description("Delay in ticks between breaks.")
        .defaultValue(0)
        .min(0)
        .sliderMax(20)
        .build());
    private final Setting<Integer> attackFrequency = sgBreak.add(new IntSetting.Builder()
        .name("attack-frequency").description("Maximum hits per second.")
        .defaultValue(25)
        .min(1)
        .sliderRange(1, 30)
        .build());
    private final Setting<Boolean> fastBreak = sgBreak.add(new BoolSetting.Builder()
        .name("fast-break").description("Breaks crystals as soon as they spawn.")
        .defaultValue(true)
        .build());

    private Item mainItem;
    private Item offItem;
    private int breakTimer;
    private int placeTimer;
    private int ticksPassed;
    private final Vec3d vec3d = new Vec3d(0.0, 0.0, 0.0);
    private final Vec3d playerEyePos = new Vec3d(0.0, 0.0, 0.0);
    private final BlockPos.Mutable blockPos = new BlockPos.Mutable();
    private final Box box = new Box(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    private final Vec3d vec3dRayTraceEnd = new Vec3d(0.0, 0.0, 0.0);
    private RaycastContext raycastContext;
    private IntSet placedCrystals = new IntOpenHashSet();
    private boolean placing;
    private int placingTimer;
    public int kaTimer;
    private final BlockPos.Mutable placingCrystalBlockPos = new BlockPos.Mutable();
    private final IntSet removed = new IntOpenHashSet();
    private final Int2IntMap attemptedBreaks = new Int2IntOpenHashMap();
    private final Int2IntMap waitingToExplode = new Int2IntOpenHashMap();
    private int attacks;

    public AutoCharged() {
        super(GunniCategory.CATEGORY, "auto-charged", "Places and explodes crystals on yourself until you die.");
    }

    public void onActivate() {
        breakTimer = 0;
        placeTimer = 0;
        ticksPassed = 0;
        raycastContext = new RaycastContext(new Vec3d(0.0, 0.0, 0.0), new Vec3d(0.0, 0.0, 0.0), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
        placing = false;
        placingTimer = 0;
        kaTimer = 0;
        attacks = 0;
    }

    public void onDeactivate() {
        placedCrystals.clear();
        attemptedBreaks.clear();
        waitingToExplode.clear();
        removed.clear();
    }

    @EventHandler(priority = 100)
    private void onPreTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (placing) {
            if (placingTimer > 0) --placingTimer;
            else placing = false;
        }

        if (kaTimer > 0) --kaTimer;

        if (ticksPassed < 20) {
            ++ticksPassed;
        } else {
            ticksPassed = 0;
            attacks = 0;
        }

        if (breakTimer > 0) --breakTimer;
        if (placeTimer > 0) --placeTimer;

        mainItem = mc.player.getMainHandStack().getItem();
        offItem = mc.player.getOffHandStack().getItem();

        IntIterator it = waitingToExplode.keySet().iterator();
        while (it.hasNext()) {
            int id = it.nextInt();
            int ticks = waitingToExplode.get(id);
            if (ticks > 3) {
                it.remove();
                removed.remove(id);
            } else {
                waitingToExplode.put(id, ticks + 1);
            }
        }

        ((IVec3d) playerEyePos).meteor$set(mc.player.getPos().x, mc.player.getPos().y + mc.player.getEyeHeight(mc.player.getPose()), mc.player.getPos().z);
        doBreak();
        doPlace();
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (!(event.entity instanceof EndCrystalEntity)) return;
        if (placing && event.entity.getBlockPos().equals(placingCrystalBlockPos)) {
            placing = false;
            placingTimer = 0;
            placedCrystals.add(event.entity.getId());
        }

        if (fastBreak.get() && attacks < attackFrequency.get()) {
            float damage = getBreakDamage(event.entity, true);
            if (damage > 0.0f) doBreak(event.entity);
        }
    }

    @EventHandler
    private void onEntityRemoved(EntityRemovedEvent event) {
        if (event.entity instanceof EndCrystalEntity) {
            placedCrystals.remove(event.entity.getId());
            removed.remove(event.entity.getId());
            waitingToExplode.remove(event.entity.getId());
        }
    }

    private void doBreak() {
        if (!doBreak.get() || breakTimer > 0 || attacks >= attackFrequency.get()) return;

        float bestDamage = 0.0f;
        Entity crystal = null;
        for (Entity entity : mc.world.getEntities()) {
            float damage = getBreakDamage(entity, true);
            if (damage > bestDamage) {
                bestDamage = damage;
                crystal = entity;
            }
        }

        if (crystal != null) doBreak(crystal);
    }

    private float getBreakDamage(Entity entity, boolean checkCrystalAge) {

        if (!(entity instanceof EndCrystalEntity)) return 0.0f;
        if (removed.contains(entity.getId())) return 0.0f;
        if (attemptedBreaks.get(entity.getId()) > 2) return 0.0f;
        if (checkCrystalAge && entity.age < 0) return 0.0f;
        if (isOutOfRange(entity.getPos(), entity.getBlockPos(), false)) return 0.0f;

        blockPos.set(entity.getBlockPos()).move(0, -1, 0);

        return DamageUtils.crystalDamage(mc.player, entity.getPos(), false, blockPos);
    }

    private void doBreak(Entity crystal) {
        attackCrystal(crystal);
        breakTimer = breakDelay.get();
        removed.add(crystal.getId());
        attemptedBreaks.put(crystal.getId(), attemptedBreaks.get(crystal.getId()) + 1);
        waitingToExplode.put(crystal.getId(), 0);
    }

    private void attackCrystal(Entity entity) {
        mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(entity, mc.player.isSneaking()));
        Hand hand = InvUtils.findInHotbar(Items.END_CRYSTAL).getHand();
        if (hand == null) hand = Hand.MAIN_HAND;

        mc.player.swingHand(hand);
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
        ++attacks;
    }

    private void doPlace() { // vf
        if (doPlace.get() && placeTimer <= 0) {
            if (InvUtils.testInHotbar(Items.END_CRYSTAL)) {
                if (mainItem != Items.END_CRYSTAL && offItem != Items.END_CRYSTAL) {
                    int slot = InvUtils.findInHotbar(Items.END_CRYSTAL).slot();
                    if (slot < 0) return;

                    InvUtils.swap(slot, false);
                }

                AtomicDouble bestDamage = new AtomicDouble(0.0);
                AtomicReference<BlockPos.Mutable> bestBlockPos = new AtomicReference<>(new BlockPos.Mutable());
                BlockIterator.register((int) Math.ceil(placeRange.get()), (int) Math.ceil(placeRange.get()), (bp, blockState) -> {
                    boolean hasBlock = blockState.isOf(Blocks.BEDROCK) || blockState.isOf(Blocks.OBSIDIAN);
                    if (hasBlock) {
                        blockPos.set(bp.getX(), bp.getY() + 1, bp.getZ());

                        if (mc.world.getBlockState(blockPos).isAir()) {
                            ((IVec3d)vec3d).meteor$set(bp.getX() + 0.5, (bp.getY() + 1), bp.getZ() + 0.5);
                            blockPos.set(bp).move(0, 1, 0);

                            if (!isOutOfRange(vec3d, blockPos, true)) {
                                float selfDamage = DamageUtils.crystalDamage(mc.player, vec3d, false, bp);

                                if (!(selfDamage <= 0.0F)) {
                                    double x = bp.getX();
                                    double y = (bp.getY() + 1);
                                    double z = bp.getZ();
                                    ((IBox) box).meteor$set(x, y, z, x + 1.0, y + 2.0, z + 1.0);

                                    if (selfDamage > bestDamage.get()) {
                                        bestDamage.set(selfDamage);
                                        bestBlockPos.get().set(bp);
                                    }
                                }
                            }
                        }
                    }
                });
                BlockIterator.after(() -> {
                    if (bestDamage.get() != 0.0) {
                        BlockHitResult result = getPlaceInfo(bestBlockPos.get());
                        FindItemResult item = InvUtils.findInHotbar(Items.END_CRYSTAL);
                        if (item.found()) {
                            int prevSlot = mc.player.getInventory().getSelectedSlot(); // ...
                            if (!item.isOffhand()) InvUtils.swap(item.slot(), false);

                            Hand hand = item.getHand();
                            if (hand != null) {
                                mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(hand, result, 0));
                                mc.player.swingHand(hand);
                                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
                                placing = true;
                                placingTimer = 4;
                                kaTimer = 8;
                                placingCrystalBlockPos.set(result.getBlockPos()).move(0, 1, 0);
                                placeTimer = placeTimer + placeDelay.get();
                            }
                        }
                    }
                });
            }
        }
    }

    private BlockHitResult getPlaceInfo(BlockPos blockPos) {
        ((IVec3d) vec3d).meteor$set(mc.player.getX(), mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()), mc.player.getZ());
        for (Direction side : Direction.values()) {
            ((IVec3d) vec3dRayTraceEnd).meteor$set(blockPos.getX() + 0.5 + side.getVector().getX() * 0.5, blockPos.getY() + 0.5 + side.getVector().getY() * 0.5, blockPos.getZ() + 0.5 + side.getVector().getZ() * 0.5);
            ((IRaycastContext) raycastContext).meteor$set(vec3d, vec3dRayTraceEnd, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
            BlockHitResult result = mc.world.raycast(raycastContext);

            if (result != null && result.getType() == HitResult.Type.BLOCK && result.getBlockPos().equals(blockPos)) return result;
        }
        Direction side2 = (blockPos.getY() > vec3d.y) ? Direction.DOWN : Direction.UP;
        return new BlockHitResult(vec3d, side2, blockPos, false);
    }

    private boolean isOutOfRange(Vec3d vec3d, BlockPos blockPos, boolean place) {
        ((IRaycastContext) raycastContext).meteor$set(playerEyePos, vec3d, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
        BlockHitResult result = mc.world.raycast(raycastContext);
        if (result == null || !result.getBlockPos().equals(blockPos)) { // ¿
            return !PlayerUtils.isWithin(vec3d, placeRange.get());
        }
        return !PlayerUtils.isWithin(vec3d, placeRange.get());
    }
}
