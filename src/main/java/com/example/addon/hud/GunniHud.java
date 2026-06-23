package com.example.addon.hud;

import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudElement;

public class GunniHud extends HudElement {

    public static final HudElementInfo<GunniHud> INFO = new HudElementInfo<>(
        Hud.GROUP, "gunni-hud", "Displays GunniAddon on screen.", GunniHud::new);

    public GunniHud() {
        super(GunniHud.INFO);
    }

    public void render(HudRenderer renderer) {
        renderer.text("GunniAddon", x, y, Color.WHITE, true);
        setSize(renderer.textWidth("GunniAddon"), renderer.textHeight());
    }
}
