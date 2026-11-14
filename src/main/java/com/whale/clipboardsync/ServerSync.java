package com.whale.clipboardsync;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 服务端逻辑：把编辑器文字写进主手写字板，并周期检测游戏内改动回推给客户端。 */
public class ServerSync {
    private static final Map<UUID, String> lastFingerprint = new HashMap<>();

    /** 客户端编辑器提交新文字：写进发送者主手的写字板。 */
    public static void onClientUpdate(String json, ServerPlayer sender) {
        if (sender == null) return;
        ItemStack stack = sender.getMainHandItem();
        if (!isClipboard(stack)) return;
        ClipboardData existing = ClipboardData.readFrom(stack);
        ClipboardData data = ClipboardData.fromJson(json);
        data.author = existing.author; // 编辑器不负责作者名，保留原值
        data.writeTo(stack);
        sender.setItemInHand(InteractionHand.MAIN_HAND, stack);
        lastFingerprint.put(sender.getUUID(), data.fingerprint());
    }

    /** 周期检测：游戏里主手写字板被改了，就推送给该玩家的客户端。 */
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (e.getServer().getTickCount() % 10 != 0) return; // 每 0.5 秒
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            ItemStack stack = p.getMainHandItem();
            if (!isClipboard(stack)) continue;
            ClipboardData d = ClipboardData.readFrom(stack);
            String fp = d.fingerprint();
            String prev = lastFingerprint.get(p.getUUID());
            if (!fp.equals(prev)) {
                lastFingerprint.put(p.getUUID(), fp);
                ModNetwork.sendToClient(p, d.toJson());
            }
        }
    }

    public static boolean isClipboard(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && "usclb:clipboard".equals(key.toString());
    }
}
