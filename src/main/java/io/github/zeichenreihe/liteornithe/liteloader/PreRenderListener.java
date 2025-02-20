package io.github.zeichenreihe.liteornithe.liteloader;

import net.minecraft.client.render.world.WorldRenderer;

public interface PreRenderListener {
    void onRenderWorld(float partialTicks);
    void onSetupCameraTransform(float partialTicks, int pass, long timeSlice);
    void onRenderSky(float partialTicks, int pass);
    void onRenderClouds(float partialTicks, int pass, WorldRenderer renderGlobal);
    void onRenderTerrain(float partialTicks, int pass);
}
