package net.twoturtles.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.twoturtles.MCioHitTracker;
import net.twoturtles.MCioStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
  /**
   * On a remote server there is no local ServerStatsCounter for MCioStats to hook, so feed it from
   * the vanilla award-stats packet. TAIL, because the method first re-schedules itself onto the
   * render thread (ensureRunningOnSameThread throws on the Netty thread), so this runs exactly
   * once, on the render thread. On a LAN host MCioStats is already fed by the integrated server, so
   * skip.
   */
  @Inject(method = "handleAwardStats", at = @At("TAIL"))
  private void mcioOnAwardStats(ClientboundAwardStatsPacket packet, CallbackInfo ci) {
    if (Minecraft.getInstance().getSingleplayerServer() == null) {
      MCioStats.getInstance().updateStatsFromServer(packet.stats());
    }
  }

  @Inject(method = "handleDamageEvent", at = @At("TAIL"))
  private void mcioOnDamageEvent(ClientboundDamageEventPacket packet, CallbackInfo ci) {
    MCioHitTracker.getInstance().onDamageEvent(packet.entityId(), packet.sourceCauseId());
  }

  @Inject(method = "handleAnimate", at = @At("TAIL"))
  private void mcioOnAnimate(ClientboundAnimatePacket packet, CallbackInfo ci) {
    MCioHitTracker.getInstance().onAnimate(packet.getId(), packet.getAction());
  }
}
