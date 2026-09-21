package net.twoturtles;

import com.mojang.logging.LogUtils;
import java.util.Optional;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.slf4j.Logger;

public class MCioClientSync {
  private final Logger LOGGER = LogUtils.getLogger();
  private final Minecraft client;
  private MCioConfig config;

  private final MCioNetworkConnection connection;
  private final MCioActionHandler actionHandler;
  private final MCioObservationHandler observationHandler;
  private final MCioSyncUtil syncUtil = MCioSyncUtil.getInstance();

  private boolean waitingForFirstAction = true;
  private int lastActionSequence = 0;
  private int ticks = 0;
  private boolean multiplayer;
  private boolean readySent = false;
  private int tickDoneCount = 0;

  /** See MCioSyncUtil for more info. */
  MCioClientSync(MCioConfig config) {
    client = Minecraft.getInstance();
    this.config = config;

    ClientPlayConnectionEvents.JOIN.register(
        (handler, sender, mc) -> {
          boolean remote = client.getSingleplayerServer() == null;
          multiplayer = config.syncMultiplayer || remote;
          syncUtil.setMultiplayerMode(multiplayer);
          readySent = false;
          LOGGER.info("Sync-Multiplayer mode={} remote={}", multiplayer, remote);
        });
    ClientPlayConnectionEvents.DISCONNECT.register(
        (handler, mc) -> {
          readySent = false;
          syncUtil.multiplayerDisconnect();
        });

    connection = new MCioNetworkConnection();
    actionHandler = new MCioActionHandler(client);
    observationHandler = new MCioObservationHandler(client, config);

    connection.registerSocketStateCallback(this::socketStateCallback);

    ClientTickEvents.START_CLIENT_TICK.register(
        client_cb -> {
          ticks++;
          MCioClientSyncUtil.checkAndSetGameRunning();
          syncUtil.clientStartTick();
          if (syncUtil.isGameRunning()) {
            if (multiplayer && !readySent && canSend(MCioSyncPayloads.READY)) {
              readySent = true;
              ClientPlayNetworking.send(new MCioSyncPayloads.ReadyPayload());
              LOGGER.info("Sent-Ready");
            }
            processAction();
          }
        });

    MCioFrameCapture frameCapture = MCioFrameCapture.getInstance();
    frameCapture.registerCaptureCallback(
        frame -> {
          if (syncUtil.isGameRunning()) {
            // Capture happens just before swapBuffers. Client ticks happen before the render.
            // So this happens after the end of the client tick.
            generateObservation();
          }
        });

    ClientTickEvents.END_CLIENT_TICK.register(
        client_cb -> {
          syncUtil.clientEndTick();
          if (multiplayer && readySent && canSend(MCioSyncPayloads.TICK_DONE)) {
            ClientPlayNetworking.send(new MCioSyncPayloads.TickDonePayload(++tickDoneCount));
          }
        });

    // For testing
    if (config.syncSpeedTest) {
      new SpeedTest();
    }
  }

  void processAction() {
    // XXX Make window responsive while waiting for an action. At least allow it to be brought to
    // the foreground.
    // XXX Hangs if you go to the menu

    if (waitingForFirstAction) {
      LOGGER.info("Waiting for first action");
    }
    Optional<ActionPacket> optAction = connection.recvActionPacket(true);
    if (optAction.isEmpty()) {
      LOGGER.warn("Invalid action");
      return;
    }

    if (waitingForFirstAction) {
      LOGGER.info("Received first action");
      waitingForFirstAction = false;
    }
    ActionPacket action = optAction.get();
    lastActionSequence = action.sequence();
    LOGGER.debug("ACTION {}", action);
    actionHandler.processAction(action);
  }

  // XXX Ideally this would include the update from the server
  void generateObservation() {
    Optional<ObservationPacket> opt = observationHandler.collectObservation(lastActionSequence);
    if (opt.isPresent()) {
      connection.sendObservationPacket(opt.get(), false);
    } else {
      // client.player is still null
      LOGGER.info("Observation Empty");
    }
  }

  /** If the Action connection goes down, automatically clear inputs. */
  private void socketStateCallback(MCioNetworkConnection.MCioSocketType type, boolean connected) {
    if (type == MCioNetworkConnection.MCioSocketType.ACTION && !connected) {
      LOGGER.info("Clearing Input (Disconnect)");
      actionHandler.requestClearInput();
    }
  }

  /** False when disconnected or when the server doesn't have MCio's receivers registered. */
  private boolean canSend(CustomPacketPayload.Type<?> type) {
    return client.getConnection() != null && ClientPlayNetworking.canSend(type);
  }

  void stop() {
    connection.close();
  }
}
