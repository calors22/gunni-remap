package com.example.addon.modules;

import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import com.example.addon.GunniCategory;
import meteordevelopment.meteorclient.systems.modules.Module;

public class AutoTomato extends Module {

    private int timer = 0;
    private static final int DELAY = 40; // ?

    public AutoTomato() {
        super(GunniCategory.CATEGORY, "auto-tomato", "Spams Tomato Domina! every 4 seconds.");
    }

    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        if (timer >= 40) {
            mc.player.networkHandler.sendChatMessage("Tomato Domina!");
            timer = 0;
        } else {
            ++timer;
        }
    }
}
