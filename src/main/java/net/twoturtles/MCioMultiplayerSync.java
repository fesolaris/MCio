package net.twoturtles;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.protocol.game.ClientboundTickingStatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerPlayer;
import net.twoturtles.mixin.ServerTickRateManagerAccessor;
import net.twoturtles.mixin.TickRateManagerAccessor;
import org.slf4j.Logger;

public class MCioMultiplayerSync {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final MCioMultiplayerSync INSTANCE = new MCioMultiplayerSync();
  private static final long LOG_EVERY = 200;

  public static MCioMultiplayerSync getInstance() {
    return INSTANCE;
  }

  private MCioMultiplayerSync() {}

  private static final class ClientState {
    int tickDone;
    int consumed;
  }

  private final Map<UUID, ClientState> clients = new HashMap<>();
  private MinecraftServer server;
  private boolean sprintStarted = false;
  private long grantCount = 0;

  public boolean isPacingActive() {
    return sprintStarted && !clients.isEmpty();
  }

  public void serverInit(MinecraftServer server) {
    this.server = server;
    ((TickRateManagerAccessor) server.tickRateManager()).mcioSetFrozen(true);
    sprintStarted = false;
    clients.clear();
    LOGGER.info("MCio-MultiplayerSync-Frozen");
  }

  public void onReady(ServerPlayer player) {
    if (server == null) {
      LOGGER.warn("MCio-Ready ignored, multiplayer sync not active");
      return;
    }
    clients.putIfAbsent(player.getUUID(), new ClientState());
    LOGGER.info("MCio-Ready player={} registered={}", player.getName().getString(), clients.size());
    if (!sprintStarted) {
      startSprint();
    }
  }

  /**
   * Records that a client finished a tick. Server-side pacing: the server advances one tick once
   * every registered client has finished at least one new tick. Clients never wait for the server,
   * so if a client runs ahead, its extra ticks are merged into a single server tick (the server's
   * tick count is <= each client's).
   *
   * <p>{@code clientTick} is the client's own counter. Using max() means lost or reordered packets
   * don't matter, and a reconnecting client (fresh ClientState, consumed = 0) is counted correctly.
   */
  public void onTickDone(ServerPlayer player, int clientTick) {
    ClientState state = clients.get(player.getUUID());
    if (state != null) {
      state.tickDone = Math.max(state.tickDone, clientTick);
    }
  }

  public void onDisconnect(ServerPlayer player) {
    if (clients.remove(player.getUUID()) != null) {
      LOGGER.info(
          "MCio-Unregister player={} remaining={}", player.getName().getString(), clients.size());
    }
  }

  public void onEndServerTick(MinecraftServer server) {
    if (clients.isEmpty()) {
      return;
    }
    for (ClientState state : clients.values()) {
      if (state.tickDone == state.consumed) {
        return;
      }
    }
    for (ClientState state : clients.values()) {
      state.consumed = state.tickDone;
    }
    server.tickRateManager().stepGameIfPaused(1);
    grantCount++;
    if (grantCount % LOG_EVERY == 0) {
      LOGGER.info("MCio-Tick-Grant count={} clients={}", grantCount, clients.size());
    }
  }

  private void startSprint() {
    ServerTickRateManager trm = server.tickRateManager();
    trm.requestGameToSprint(1);
    ServerTickRateManagerAccessor accessor = (ServerTickRateManagerAccessor) trm;
    accessor.setRemainingSprintTicks(Long.MAX_VALUE);
    accessor.setScheduledCurrentSprintTicks(Long.MAX_VALUE);
    ((TickRateManagerAccessor) trm).mcioSetFrozen(true);
    server.getPlayerList().broadcastAll(ClientboundTickingStatePacket.from(trm));
    sprintStarted = true;
    LOGGER.info("MCio-Pacer-Active");
  }
}
