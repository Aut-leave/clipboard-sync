package com.whale.clipboardsync;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 服务端 → 客户端：当前写字板内容（JSON），供本地编辑器拉取。 */
public class S2CStatePacket {
    private final String json;

    public S2CStatePacket(String json) { this.json = json; }

    public static void encode(S2CStatePacket msg, FriendlyByteBuf buf) {
        buf.writeByteArray(msg.json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static S2CStatePacket decode(FriendlyByteBuf buf) {
        return new S2CStatePacket(new String(buf.readByteArray(), java.nio.charset.StandardCharsets.UTF_8));
    }

    public static void handle(S2CStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientSync.onServerState(msg.json));
        ctx.get().setPacketHandled(true);
    }
}
