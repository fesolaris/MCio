package net.twoturtles;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class MCioSyncPayloads {
  public static final CustomPacketPayload.Type<ReadyPayload> READY =
      new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("mcio", "ready"));
  public static final CustomPacketPayload.Type<TickDonePayload> TICK_DONE =
      new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("mcio", "tick_done"));
  public static final CustomPacketPayload.Type<ServerTickPayload> SERVER_TICK =
      new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("mcio", "server_tick"));

  public record ReadyPayload() implements CustomPacketPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, ReadyPayload> CODEC =
        StreamCodec.unit(new ReadyPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
      return READY;
    }
  }

  public record TickDonePayload(int clientTick) implements CustomPacketPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, TickDonePayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TickDonePayload::clientTick, TickDonePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
      return TICK_DONE;
    }
  }

  public record ServerTickPayload(int serverTick) implements CustomPacketPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerTickPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ServerTickPayload::serverTick, ServerTickPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
      return SERVER_TICK;
    }
  }

  public static void registerCommon() {
    PayloadTypeRegistry.playC2S().register(READY, ReadyPayload.CODEC);
    PayloadTypeRegistry.playC2S().register(TICK_DONE, TickDonePayload.CODEC);
    PayloadTypeRegistry.playC2S().register(SERVER_TICK, ServerTickPayload.CODEC);
  }

  private MCioSyncPayloads() {}
}
