package com.example.addon;

import com.example.addon.hud.GunniHud;
import com.example.addon.modules.*;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class AddonTemplate extends MeteorAddon {

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(GunniCategory.CATEGORY);
    }

    @Override
    public void onInitialize() {
        final Modules modules = Modules.get();
        modules.add(new AutoVclip());
        modules.add(new AutoTomato());
        modules.add(new AutoCharged());
        modules.add(new AutoCuck());
        modules.add(new AutoRage());
        modules.add(new PearlCatcher());
        modules.add(new SpeedMine());
        modules.add(new AutoKami());
        Hud.get().register(GunniHud.INFO);
        System.out.println("[GunniAddon] Loaded successfully.");
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }
}
