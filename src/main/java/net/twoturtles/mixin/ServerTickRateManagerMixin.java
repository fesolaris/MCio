package net.twoturtles.mixin;

import net.minecraft.server.ServerTickRateManager;
import net.twoturtles.MCioMultiplayerSync;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerTickRateManager.class)
public class ServerTickRateManagerMixin {
  /**
   * Keep the server loop in sprint mode (no inter-tick sleep) while pacing is active. The world is
   * frozen and only advances via onEndServerTick grants, so while waiting for clients this loop
   * spins and uses a full core.
   */
  @Inject(method = "checkShouldSprintThisTick", at = @At("HEAD"), cancellable = true)
  private void mcioForceSprint(CallbackInfoReturnable<Boolean> cir) {
    if (MCioMultiplayerSync.getInstance().isPacingActive()) {
      cir.setReturnValue(true);
    }
  }
}
