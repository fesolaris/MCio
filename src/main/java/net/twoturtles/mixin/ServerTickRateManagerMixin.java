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
   * While pacing is active, keep the server loop in sprint mode (no inter-tick sleep). The world is
   * frozen; MinecraftServerMixin runs a full tick only when MCioMultiplayerSync has granted one and
   * parks briefly on idle spins. During logins, fall back to normal 20 TPS ticking.
   */
  @Inject(method = "checkShouldSprintThisTick", at = @At("HEAD"), cancellable = true)
  private void mcioForceSprint(CallbackInfoReturnable<Boolean> cir) {
    MCioMultiplayerSync sync = MCioMultiplayerSync.getInstance();
    if (sync.isSprintStarted()) {
      // Sprint (and gate) while pacing; during logins fall back to normal 20 TPS ticking.
      cir.setReturnValue(sync.isPacingActive());
    }
  }
}
