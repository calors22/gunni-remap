package com.example.addon.modules;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import com.example.addon.GunniCategory;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public class AutoKami extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> healthLevel = sgGeneral.add(new IntSetting.Builder()
        .name("health-level")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 20)
        .build());
    private final Setting<Integer> clipDistance = sgGeneral.add(new IntSetting.Builder()
        .name("clip-distance")
        .defaultValue(100)
        .min(1)
        .sliderRange(1, 500)
        .build());
    private final Setting<Integer> disconnectDelay = sgGeneral.add(new IntSetting.Builder()
        .name("disconnect-delay")
        .defaultValue(20)
        .min(1)
        .sliderRange(1, 100)
        .build());
    private final Setting<Integer> cooldownTime = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown")
        .defaultValue(100)
        .min(0)
        .sliderRange(0, 600)
        .build());

    private boolean triggered = false;
    private int timer = 0;
    private int cooldown = 0;

    public AutoKami() {
        super(GunniCategory.CATEGORY, "auto-kami", " Loggea como Kamilonwww");
    }

    public void onActivate() {
        triggered = false;
        timer = 0;
        cooldown = 0;
    }

    public void onDeactivate() {
        triggered = false;
        timer = 0;
        cooldown = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (cooldown > 0) --cooldown;

        double totalHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (triggered) {
            ++timer;

            if (timer >= disconnectDelay.get()) {
                mc.player.networkHandler.getConnection().disconnect(Text.of("AutoKami disconnect"));
                triggered = false;
                timer = 0;
                cooldown = cooldownTime.get();
                toggle();
            }
            return;
        }

        if (cooldown == 0 && totalHealth <= healthLevel.get()) {
            mc.player.networkHandler.sendChatMessage(".vclip " + clipDistance.get());
            triggered = true;
            timer = 0;
        }
    }
}
