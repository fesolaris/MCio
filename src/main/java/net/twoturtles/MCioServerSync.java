package net.twoturtles;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

class MCioServerSync {
  private final Logger LOGGER = LogUtils.getLogger();
  private final MCioSyncUtil syncUtil = MCioSyncUtil.getInstance();
  private final MCioConfig config;

  public MCioServerSync(MCioConfig config) {
    this.config = config;
    ServerLifecycleEvents.SERVER_STARTED.register(this::init);
    ServerTickEvents.START_SERVER_TICK.register(this::startTickCB);
    ServerTickEvents.END_SERVER_TICK.register(this::endTickCB);
  }

  void init(MinecraftServer server) {
    if (config.syncMultiplayer) {
      MCioMultiplayerSync.getInstance().serverInit(server);
    } else {
      syncUtil.serverInit(server);
    }
  }

  void startTickCB(MinecraftServer server) {
    if (!config.syncMultiplayer) {
      syncUtil.serverStartTick();
    }
  }

  void endTickCB(MinecraftServer server) {
    if (config.syncMultiplayer) {
      MCioMultiplayerSync.getInstance().onEndServerTick(server);
    } else {
      syncUtil.serverEndTick();
    }
  }

  void stop() {}
}
