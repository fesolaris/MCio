package net.twoturtles.mixin;

import net.minecraft.world.TickRateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TickRateManager.class)
public interface TickRateManagerAccessor {
  @Accessor("isFrozen")
  void mcioSetFrozen(boolean frozen);
}
