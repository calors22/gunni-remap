package com.example.addon.modules;

import meteordevelopment.meteorclient.systems.friends.Friends;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.BlockHitResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.item.BlockItem;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import com.example.addon.GunniCategory;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public class PearlCatcher extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range").description("Maximum range at which blocks will be placed.")
        .defaultValue(5.0)
        .min(0.0)
        .sliderMax(12.0)
        .build());
    private final Setting<Double> enemyRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("enemy-range").description("Maximum distance at which the target should be.")
        .defaultValue(8.0)
        .min(0.0)
        .sliderMax(16.0)
        .build());
    private final Setting<Double> getDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("getDistance").description("Distance at which the obsidian block will be placed from the player.")
        .defaultValue(4.0)
        .min(3.0)
        .sliderMax(6.0)
        .build());
    private final Setting<Boolean> holeCheck = sgGeneral.add(new BoolSetting.Builder()
        .name("hole-check").description("Only activates when the enemy is in a hole.")
        .defaultValue(false)
        .build()); // alr
    private final Setting<Boolean> whileEating = sgGeneral.add(new BoolSetting.Builder()
        .name("while-eating").description("Places blocks while eating.")
        .defaultValue(true)
        .build());
    private final Setting<Boolean> itemDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("item-disable").description("Toggles off when you run out of blocks.")
        .defaultValue(true)
        .build());

    public PearlCatcher() {
        super(GunniCategory.CATEGORY, "pearl-catcher", "Places obsidian to intercept enemy ender pearls.");
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (mc.player == null || mc.world == null) return;

        Entity entity = event.entity;
        if (!(entity instanceof EnderPearlEntity)) return;

        EnderPearlEntity pearl = (EnderPearlEntity) entity;
        if (!whileEating.get() && mc.player.isUsingItem()) return;

        Entity owner2 = pearl.getOwner();
        if (!(owner2 instanceof PlayerEntity)) return;

        PlayerEntity owner = (PlayerEntity) owner2;
        if (!validTarget(owner)) return;

        catchPearl(owner, pearl);
    }

    private void catchPearl(PlayerEntity player, EnderPearlEntity pearl) {
        int slot = InvUtils.findInHotbar(stack -> stack.getItem() instanceof BlockItem).slot();
        if (slot < 0) {
            if (itemDisable.get()) toggle();
            return;
        }

        BlockHitResult hitResult = (BlockHitResult) player.raycast(getDistance.get(), 0.0f, false);
        if (hitResult.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = hitResult.getBlockPos();
        if (mc.player.squaredDistanceTo(Vec3d.ofCenter(pos)) > MathHelper.square(range.get())) return;
        if (!mc.world.getBlockState(pos).isAir()) return;

        int prevSlot = mc.player.getInventory().getSelectedSlot();
        InvUtils.swap(slot, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(pos), hitResult.getSide(), pos, false));
        mc.player.swingHand(Hand.MAIN_HAND);
        InvUtils.swap(prevSlot, false);
    }

    private boolean validTarget(PlayerEntity player) {
        return player != mc.player && mc.player.squaredDistanceTo(player) <= MathHelper.square(enemyRange.get()) && !Friends.get().isFriend(player);
    }
}
