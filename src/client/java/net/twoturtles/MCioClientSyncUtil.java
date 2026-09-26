package net.twoturtles;

import net.minecraft.client.Minecraft;

public class MCioClientSyncUtil {
  /**
   * Pass the game state from the client up to the main namespace. This must be done here because
   * MCioSyncUtil can't access MinecraftClient.
   */
  public static void checkAndSetGameRunning() {
    Minecraft client = Minecraft.getInstance();

    boolean remote = client.getSingleplayerServer() == null;
    boolean chunksReady =
        remote
            || !MCioConfig.getInstance().mcioPreloadChunks
            || MCioChunks.getInstance().clientInitialLoadComplete();

    // Screens (menus) and Overlays (e.g. the Mojang LoadingOverlay shown after startup or a
    // resource reload) are separate. MouseHandler ignores button presses while an overlay is up,
    // and the LoadingOverlay fades out over 1 s of wall-clock time, not ticks, so wait for both.
    if (client.screen == null && client.getOverlay() == null && chunksReady) {
      MCioSyncUtil.getInstance().setGameRunning(true);
    }
  }
}
