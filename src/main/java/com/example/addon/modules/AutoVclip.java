package com.example.addon.modules;

import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import com.example.addon.GunniCategory;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public class AutoVclip extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> healthLevel = sgGeneral.add(new IntSetting.Builder()
        .name("health-level").description("Health level at which to trigger vclip.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 20)
        .build());
    private final Setting<Integer> clipDistance = sgGeneral.add(new IntSetting.Builder()
        .name("clip-distance").description("Distance to vclip.")
        .defaultValue(100)
        .min(1)
        .sliderRange(1, 500)
        .build());

    private int cooldownTimer = 0;
    private static final int COOLDOWN = 100; // ¿

    public AutoVclip() {
        super(GunniCategory.CATEGORY, "auto-vclip", "Runs .vclip when health is low.");
    }

    public void onActivate() {
        cooldownTimer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        if (cooldownTimer > 0) {
            --cooldownTimer;
            return;
        }

        if (mc.player.getHealth() <= healthLevel.get()) {
            mc.player.networkHandler.sendChatMessage(".vclip " + clipDistance.get());
            cooldownTimer = 100;
        }
    }
}
