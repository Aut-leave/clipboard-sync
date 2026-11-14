package com.whale.clipboardsync;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端 → 服务端：编辑器提交的新文字（JSON）。 */
public class C2SUpdatePacket {
    private final String json;

    public C2SUpdatePacket(String json) { this.json = json; }

    public static void encode(C2SUpdatePacket msg, FriendlyByteBuf buf) {
        buf.writeByteArray(msg.json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static C2SUpdatePacket decode(FriendlyByteBuf buf) {
        return new C2SUpdatePacket(new String(buf.readByteArray(), java.nio.charset.StandardCharsets.UTF_8));
    }

    public static void handle(C2SUpdatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ServerSync.onClientUpdate(msg.json, ctx.get().getSender()));
        ctx.get().setPacketHandled(true);
    }
}
