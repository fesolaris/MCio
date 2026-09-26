package net.twoturtles;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;

public class MCioHitTracker {
  private static final MCioHitTracker INSTANCE = new MCioHitTracker();
  private static final int CRIT_WINDOW = 2;

  public static MCioHitTracker getInstance() {
    return INSTANCE;
  }

  private int hits = 0;
  private int crits = 0;
  private long observation = 0;
  private final Map<Integer, Long> recentVictims =
      new HashMap<>(); // entity id -> observation of our last hit

  private MCioHitTracker() {}

  public synchronized void onDamageEvent(int entityId, int sourceCauseId) {
    LocalPlayer self = Minecraft.getInstance().player;
    if (self == null || entityId == self.getId() || sourceCauseId != self.getId()) {
      return;
    }
    hits++;
    recentVictims.put(entityId, observation);
  }

  public synchronized void onAnimate(int entityId, int action) {
    if (action != ClientboundAnimatePacket.CRITICAL_HIT) {
      return;
    }
    LocalPlayer self = Minecraft.getInstance().player;
    if (self == null || entityId == self.getId()) {
      return;
    }
    Long when = recentVictims.get(entityId);
    if (when != null && observation - when <= CRIT_WINDOW) {
      crits++;
    }
  }

  public synchronized int[] takeCounts() {
    int[] out = {hits, crits};
    hits = 0;
    crits = 0;
    observation++;
    recentVictims.values().removeIf(t -> observation - t > CRIT_WINDOW);
    return out;
  }

  public synchronized void reset() {
    hits = 0;
    crits = 0;
    recentVictims.clear();
  }
}
