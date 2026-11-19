package com.sugar.net;

import com.sugar.SugarMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/** Canal de red del mod. Por ahora solo lleva la sacudida de pantalla. */
public final class SugarNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(SugarMod.id("main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        CHANNEL.registerMessage(0, ShakePacket.class, ShakePacket::encode, ShakePacket::decode, ShakePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, WavePacket.class, WavePacket::encode, WavePacket::decode, WavePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void wave(ServerPlayer player, net.minecraft.world.phys.Vec3 c, double speed, double max, int style) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new WavePacket(c.x, c.y, c.z, (float) speed, (float) max, style));
    }

    public record WavePacket(double x, double y, double z, float speed, float max, int style) {
        static void encode(WavePacket p, FriendlyByteBuf buf) {
            buf.writeDouble(p.x); buf.writeDouble(p.y); buf.writeDouble(p.z);
            buf.writeFloat(p.speed); buf.writeFloat(p.max); buf.writeVarInt(p.style);
        }

        static WavePacket decode(FriendlyByteBuf buf) {
            return new WavePacket(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readVarInt());
        }

        static void handle(WavePacket p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.sugar.client.ClientFx.addWave(p.x, p.y, p.z, p.speed, p.max, p.style)));
            ctx.get().setPacketHandled(true);
        }
    }

    public static void shake(ServerPlayer player, float intensity, int ticks) {
        if (intensity <= 0 || ticks <= 0) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ShakePacket(intensity, ticks));
    }

    public record ShakePacket(float intensity, int ticks) {
        static void encode(ShakePacket p, FriendlyByteBuf buf) {
            buf.writeFloat(p.intensity);
            buf.writeVarInt(p.ticks);
        }

        static ShakePacket decode(FriendlyByteBuf buf) {
            return new ShakePacket(buf.readFloat(), buf.readVarInt());
        }

        static void handle(ShakePacket p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.sugar.client.ClientFx.startShake(p.intensity, p.ticks)));
            ctx.get().setPacketHandled(true);
        }
    }

    private SugarNetwork() {}
}
