package net.twoturtles.mixin;

import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.twoturtles.MCioMultiplayerSync;
import net.twoturtles.MCioProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
  /**
   * While multiplayer pacing is active, only run a full server tick when a granted step is waiting.
   * Idle spins return here, so connection/player ticks and teleport timers advance once per granted
   * tick. Incoming packets are still handled by runServer's waitUntilNextTick(), which runs outside
   * tickServer.
   */
  @Inject(method = "tickServer", at = @At("HEAD"), cancellable = true)
  private void mcioGateTick(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
    long tGate = MCioProfile.t();
    boolean cancel = MCioMultiplayerSync.getInstance().beforeServerTick();
    MCioProfile.add(MCioProfile.Phase.SERVER_GATE, tGate);
    if (cancel) {
      ci.cancel();
    }
  }

  /**
   * Vanilla defers a queued task until tickCount is 3 past its creation tick, or until the loop has
   * spare time. While pacing, idle spins don't increase tickCount and sprinting leaves no spare
   * time, so queued packets (e.g. TICK_DONE) would never run and no tick would be granted. Run
   * queued tasks on every spin instead.
   */
  @Inject(
      method = "shouldRun(Lnet/minecraft/server/TickTask;)Z",
      at = @At("HEAD"),
      cancellable = true)
  private void mcioRunTasksWhilePacing(TickTask task, CallbackInfoReturnable<Boolean> cir) {
    if (MCioMultiplayerSync.getInstance().isPacingActive()) {
      cir.setReturnValue(true);
    }
  }
}
