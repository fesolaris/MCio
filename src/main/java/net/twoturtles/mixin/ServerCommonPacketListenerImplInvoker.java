package net.twoturtles.mixin;

import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerCommonPacketListenerImpl.class)
public interface ServerCommonPacketListenerImplInvoker {
  @Invoker("keepConnectionAlive")
  void mcioKeepConnectionAlive();
}
