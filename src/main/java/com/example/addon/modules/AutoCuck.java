package com.example.addon.modules;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import com.example.addon.GunniCategory;
import meteordevelopment.meteorclient.systems.modules.Module;

public class AutoCuck extends Module {

    public AutoCuck() {
        super(GunniCategory.CATEGORY, "auto-cuck", "sends a silly message after a kill.");
    }

    @EventHandler
    private void onEntityRemoved(EntityRemovedEvent event) {
        if (mc.player == null || mc.world == null) return;

        Entity entity = event.entity;
        if (!(entity instanceof PlayerEntity)) return;

        PlayerEntity player = (PlayerEntity) entity;
        if (player == mc.player) return;

        if (player.getHealth() > 0.0f) return;

        mc.player.networkHandler.sendChatMessage("EZZ cuckeado como camila a manurpm " + player.getName().getString());
    }
}
