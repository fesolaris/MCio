package net.twoturtles;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.Util;
import net.minecraft.network.protocol.game.ClientboundTickingStatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.stats.ServerStatsCounter;
import net.twoturtles.MCioSyncPayloads.ServerTickPayload;
import net.twoturtles.mixin.ServerCommonPacketListenerImplInvoker;
import net.twoturtles.mixin.ServerStatsCounterMixin;
import net.twoturtles.mixin.ServerTickRateManagerAccessor;
import net.twoturtles.mixin.TickRateManagerAccessor;
import org.slf4j.Logger;

public class MCioMultiplayerSync {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final MCioMultiplayerSync INSTANCE = new MCioMultiplayerSync();
  private static final long IDLE_PARK_NANOS = 50_000;
  private static final long KEEPALIVE_INTERVAL_MS = 1_000;

  public static MCioMultiplayerSync getInstance() {
    return INSTANCE;
  }

  private MCioMultiplayerSync() {}

  private static final class ClientState {
    final ServerGamePacketListenerImpl connection;
    int tickDone;
    int consumed;

    ClientState(ServerGamePacketListenerImpl connection) {
      this.connection = connection;
    }
  }

  private final Map<UUID, ClientState> clients = new HashMap<>();
  private MinecraftServer server;
  private boolean sprintStarted = false;
  private long grantCount = 0;
  private long lastKeepAliveMs = 0;
  private boolean warnedNotFrozen = false;

  public boolean isPacingActive() {
    if (!sprintStarted || clients.isEmpty() || server == null) {
      return false;
    }

    int connections = server.getConnection().getConnections().size();
    int players = server.getPlayerList().getPlayers().size();
    return connections <= players;
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
    ClientState prev = clients.get(player.getUUID());
    if (prev == null || prev.connection != player.connection) {
      clients.put(player.getUUID(), new ClientState(player.connection));
    }
    LOGGER.info("MCio-Ready player={} registered={}", player.getName().getString(), clients.size());
    if (!sprintStarted) {
      startSprint();
    }
  }

  public void onTickDone(ServerPlayer player, int clientTick) {
    ClientState state = clients.get(player.getUUID());
    if (state != null) {
      state.tickDone = Math.max(state.tickDone, clientTick);
    }
  }

  public void onDisconnect(MinecraftServer server, ServerPlayer player) {
    server.execute(() -> unregister(player));
  }

  private void unregister(ServerPlayer player) {
    ClientState state = clients.get(player.getUUID());
    if (state != null && state.connection == player.connection) {
      clients.remove(player.getUUID());
      LOGGER.info(
          "MCio-Unregister player={} remaining={}", player.getName().getString(), clients.size());
    }
  }

  public boolean beforeServerTick() {
    if (!isPacingActive()) {
      return false;
    }
    ServerTickRateManager trm = server.tickRateManager();
    if (!trm.isFrozen()) {
      if (!warnedNotFrozen) {
        LOGGER.warn("MCio-Pacing-Suspended, server not frozen");
        warnedNotFrozen = true;
      }
      return false;
    }
    warnedNotFrozen = false;
    if (!trm.isSteppingForward()) {
      tryGrant(trm);
    }
    if (trm.isSteppingForward()) {
      return false; // Run it. tickServer's trm.tick() consumes one step.
    }
    if (hasPendingDisconnect()) {
      return false;
    }
    idle();
    return true;
  }

  public void afterServerTick() {
    if (server == null) {
      return;
    }
    ServerTickPayload payload = new ServerTickPayload(server.getTickCount());
    for (ClientState state : clients.values()) {
      ServerPlayer player = state.connection.player;
      // Remote clients have no local ServerStatsCounter, so push this tick's stat changes.
      // sendStats() sends only the dirty (changed) stats; the first send after join has all of
      // them.
      ServerStatsCounter stats = player.getStats();
      if (!((ServerStatsCounterMixin.DirtyAccessor) stats).mcioGetDirty().isEmpty()) {
        stats.sendStats(player);
      }
      if (ServerPlayNetworking.canSend(state.connection, MCioSyncPayloads.SERVER_TICK)) {
        ServerPlayNetworking.send(state.connection.player, payload);
      }
    }
  }

  private void tryGrant(ServerTickRateManager trm) {
    int pending = Integer.MAX_VALUE;
    for (ClientState state : clients.values()) {
      pending = Math.min(pending, state.tickDone - state.consumed);
    }
    if (pending <= 0) {
      return; // At least one client has not finished a new tick.
    }
    if (!trm.stepGameIfPaused(pending)) {
      LOGGER.warn("MCio-Tick-Grant failed, server not frozen");
      return;
    }
    for (ClientState state : clients.values()) {
      state.consumed += pending;
    }
    long before = grantCount;
    grantCount += pending;
    int every = MCioConfig.getInstance().tickGrantLogEvery;
    if (every > 0 && before / every != grantCount / every) {
      LOGGER.info("MCio-Tick-Grant count={} clients={}", grantCount, clients.size());
    }
  }

  private boolean hasPendingDisconnect() {
    for (UUID id : clients.keySet()) {
      ServerPlayer player = server.getPlayerList().getPlayer(id);
      if (player == null || !player.connection.isAcceptingMessages()) {
        return true;
      }
    }
    return false;
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

  private void idle() {
    long now = Util.getMillis();
    if (now - lastKeepAliveMs >= KEEPALIVE_INTERVAL_MS) {
      lastKeepAliveMs = now;
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
        ((ServerCommonPacketListenerImplInvoker) player.connection).mcioKeepConnectionAlive();
      }
    }
    LockSupport.parkNanos(IDLE_PARK_NANOS);
  }

  public boolean isSprintStarted() {
    return sprintStarted;
  }
}
