package com.example.addon.modules;

import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import com.example.addon.GunniCategory;
import meteordevelopment.meteorclient.systems.modules.Module;

public class AutoRage extends Module {

    private int messageIndex = 0;
    private boolean died = false;
    private int timer = 0;

    public AutoRage() {
        super(GunniCategory.CATEGORY, "auto-rage", "Makes you rage like manurpm.");
    }

    public void onActivate() {
        messageIndex = 0;
        died = false;
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (mc.player.isDead() && !died) {
            died = true;
            timer = 0;
            messageIndex = 0;
        }

        if (!died) return;

        ++timer;
        if (timer % 20 == 0 && messageIndex < 3) {
            switch (messageIndex) {
                case 0: {
                    mc.player.networkHandler.sendChatMessage("AAOKAJSUIOHK:LAD");
                    break;
                }
                case 1: {
                    mc.player.networkHandler.sendChatMessage("PYUHGKKASLI';'");
                    break;
                }
                case 2: {
                    mc.player.networkHandler.sendChatMessage("HIJOS DE PUTA EN DEBATE LES GANO");
                    mc.world.disconnect();
                    break;
                }
            }
            ++messageIndex;
        }
    }
}
