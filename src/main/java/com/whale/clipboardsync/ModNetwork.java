package com.whale.clipboardsync;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetwork {
    private static final String VER = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("clipboardsync", "main"),
            () -> VER, VER::equals, VER::equals);

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, C2SUpdatePacket.class,
                C2SUpdatePacket::encode, C2SUpdatePacket::decode, C2SUpdatePacket::handle);
        CHANNEL.registerMessage(id++, S2CStatePacket.class,
                S2CStatePacket::encode, S2CStatePacket::decode, S2CStatePacket::handle);
    }

    public static void sendToServer(String json) {
        CHANNEL.sendToServer(new C2SUpdatePacket(json));
    }

    public static void sendToClient(ServerPlayer player, String json) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CStatePacket(json));
    }
}
