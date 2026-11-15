package com.whale.clipboardsync;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 服务端逻辑：把编辑器文字写进主手写字板，并周期检测游戏内改动回推给客户端。 */
public class ServerSync {
    private static final Map<UUID, String> lastFingerprint = new HashMap<>();
    private static final Map<UUID, Map<Integer, String[]>> idBackup = new HashMap<>();

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
        // 每 tick 先恢复被原版界面覆盖的 id
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            restoreIds(p);
        }
        if (e.getServer().getTickCount() % 10 != 0) return; // 每 0.5 秒
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            ItemStack stack = p.getMainHandItem();
            if (!isClipboard(stack)) continue;
            ensureIdentity(p, stack);
            ClipboardData d = ClipboardData.readFrom(stack);
            String fp = d.fingerprint();
            String prev = lastFingerprint.get(p.getUUID());
            if (!fp.equals(prev)) {
                lastFingerprint.put(p.getUUID(), fp);
                ModNetwork.sendToClient(p, d.toJson());
            }
        }
    }

    /** 按背包槽位记住每块写字板的 id/名字，被原版界面覆盖后立刻恢复。 */
    private static void restoreIds(ServerPlayer p) {
        Inventory inv = p.getInventory();
        Map<Integer, String[]> backup = idBackup.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!isClipboard(s)) continue;
            CompoundTag t = s.getOrCreateTag();
            if (t.getString("clipboardsync_id").isEmpty()) {
                String[] saved = backup.get(i);
                if (saved != null) {
                    t.putString("clipboardsync_id", saved[0]);
                    if (!saved[1].isEmpty()) t.putString("clipboardsync_name", saved[1]);
                }
            }
        }
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!isClipboard(s)) continue;
            CompoundTag t = s.getOrCreateTag();
            String id = t.getString("clipboardsync_id");
            if (!id.isEmpty()) backup.put(i, new String[]{id, t.getString("clipboardsync_name")});
        }
    }

    /** 确保这块写字板有稳定的 id 和名字；没有就补上，并同步写回物品。 */
    private static void ensureIdentity(ServerPlayer player, ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        boolean changed = false;
        if (tag.getString("clipboardsync_id").isEmpty()) {
            tag.putString("clipboardsync_id", UUID.randomUUID().toString());
            changed = true;
        }
        if (tag.getString("clipboardsync_name").isEmpty()) {
            tag.putString("clipboardsync_name", nextName(player));
            changed = true;
        }
        if (changed) {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(
                    player.inventoryMenu.containerId,
                    player.inventoryMenu.getStateId(),
                    36 + player.getInventory().selected,
                    stack.copy()));
        }
    }

    /** 生成一个在背包里不重复的默认名：已有最大编号 + 1。 */
    private static String nextName(ServerPlayer player) {
        int max = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!isClipboard(s)) continue;
            int x = parseNum(s.getOrCreateTag().getString("clipboardsync_name"));
            if (x > max) max = x;
        }
        return "clipboard(" + (max + 1) + ")";
    }

    private static int parseNum(String s) {
        if (s == null) return 0;
        int l = s.lastIndexOf('(');
        int r = s.lastIndexOf(')');
        if (l < 0 || r <= l) return 0;
        try { return Integer.parseInt(s.substring(l + 1, r)); } catch (Exception e) { return 0; }
    }

    public static boolean isClipboard(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && "usclb:clipboard".equals(key.toString());
    }
}
