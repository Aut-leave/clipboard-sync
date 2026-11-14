package com.whale.clipboardsync;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * 写字板（usclb:clipboard）文字数据模型。
 * 真实结构（由游戏内 /data get 确认）：
 *   ItemStack.tag.BlockEntityTag
 *     ├── author: String
 *     ├── currentpage: Int
 *     └── pages: ListTag(5 页) → 每页 ListTag(12 行) → 每行 CompoundTag{ header, text, button }
 */
public class ClipboardData {
    public static final int PAGE_COUNT = 5;
    public static final int LINE_COUNT = 12;

    public String author = "";
    public int currentpage = 0;
    public final String[][] headers = new String[PAGE_COUNT][LINE_COUNT];
    public final String[][] texts = new String[PAGE_COUNT][LINE_COUNT];

    public ClipboardData() {
        for (int p = 0; p < PAGE_COUNT; p++)
            for (int l = 0; l < LINE_COUNT; l++) {
                headers[p][l] = "";
                texts[p][l] = "";
            }
    }

    public static ClipboardData empty() { return new ClipboardData(); }

    public static ClipboardData readFrom(ItemStack stack) {
        ClipboardData d = new ClipboardData();
        if (stack == null || stack.isEmpty() || !stack.hasTag()) return d;
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains("BlockEntityTag", Tag.TAG_COMPOUND)) return d;
        CompoundTag be = root.getCompound("BlockEntityTag");
        d.author = be.getString("author");
        d.currentpage = be.getInt("currentpage");
        if (be.contains("pages", Tag.TAG_LIST)) {
            ListTag pages = be.getList("pages", Tag.TAG_LIST);
            for (int p = 0; p < Math.min(pages.size(), PAGE_COUNT); p++) {
                ListTag page = pages.getList(p); // 每页一个 ListTag
                for (int l = 0; l < Math.min(page.size(), LINE_COUNT); l++) {
                    CompoundTag line = page.getCompound(l);
                    d.headers[p][l] = line.getString("header");
                    d.texts[p][l] = line.getString("text");
                }
            }
        }
        return d;
    }

    /** 写回物品：更新 pages/author/currentpage，保留 button 原值与 x/y/z/id/ForgeCaps 等其它字段。 */
    public void writeTo(ItemStack stack) {
        CompoundTag root = stack.getOrCreateTag();
        CompoundTag be = root.getCompound("BlockEntityTag");
        ListTag oldPages = be.getList("pages", Tag.TAG_LIST);
        be.putString("author", author);
        be.putInt("currentpage", currentpage);
        ListTag newPages = new ListTag();
        for (int p = 0; p < PAGE_COUNT; p++) {
            ListTag oldPage = p < oldPages.size() ? oldPages.getList(p) : null;
            ListTag page = new ListTag();
            for (int l = 0; l < LINE_COUNT; l++) {
                CompoundTag line = new CompoundTag();
                line.putString("header", headers[p][l]);
                line.putString("text", texts[p][l]);
                byte btn = 0;
                if (oldPage != null && l < oldPage.size()) {
                    btn = oldPage.getCompound(l).getByte("button");
                }
                line.putByte("button", btn);
                page.add(line);
            }
            newPages.add(page);
        }
        be.put("pages", newPages);
        root.put("BlockEntityTag", be);
    }

    /** 内容指纹，用于检测「游戏里被改了」；不含 button（非同步目标）。 */
    public String fingerprint() {
        StringBuilder sb = new StringBuilder();
        sb.append(author).append('\n').append(currentpage).append('\n');
        for (int p = 0; p < PAGE_COUNT; p++)
            for (int l = 0; l < LINE_COUNT; l++)
                sb.append(headers[p][l]).append('\u0001').append(texts[p][l]).append('\n');
        return sb.toString();
    }

    public ClipboardData copy() {
        ClipboardData d = new ClipboardData();
        d.author = author;
        d.currentpage = currentpage;
        for (int p = 0; p < PAGE_COUNT; p++)
            for (int l = 0; l < LINE_COUNT; l++) {
                d.headers[p][l] = headers[p][l];
                d.texts[p][l] = texts[p][l];
            }
        return d;
    }

    /** JSON：只含 currentpage + pages（5 页 × 12 行，每行 [header, text]）。author 由服务端保留。 */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"currentpage\":").append(currentpage).append(",\"pages\":[");
        for (int p = 0; p < PAGE_COUNT; p++) {
            if (p > 0) sb.append(',');
            sb.append('[');
            for (int l = 0; l < LINE_COUNT; l++) {
                if (l > 0) sb.append(',');
                sb.append("[\"").append(Json.escape(headers[p][l])).append("\",\"")
                  .append(Json.escape(texts[p][l])).append("\"]");
            }
            sb.append(']');
        }
        sb.append("]}");
        return sb.toString();
    }

    public static ClipboardData fromJson(String s) {
        ClipboardData d = new ClipboardData();
        if (s == null || s.isEmpty()) return d;
        Object v = Json.parse(s);
        if (!(v instanceof Map)) return d;
        Map<?, ?> m = (Map<?, ?>) v;
        Object cp = m.get("currentpage");
        if (cp instanceof Number) d.currentpage = ((Number) cp).intValue();
        Object pages = m.get("pages");
        if (pages instanceof List) {
            List<?> pl = (List<?>) pages;
            for (int p = 0; p < Math.min(pl.size(), PAGE_COUNT); p++) {
                Object page = pl.get(p);
                if (page instanceof List) {
                    List<?> ll = (List<?>) page;
                    for (int l = 0; l < Math.min(ll.size(), LINE_COUNT); l++) {
                        Object line = ll.get(l);
                        if (line instanceof List) {
                            List<?> pair = (List<?>) line;
                            if (pair.size() >= 1 && pair.get(0) instanceof String)
                                d.headers[p][l] = (String) pair.get(0);
                            if (pair.size() >= 2 && pair.get(1) instanceof String)
                                d.texts[p][l] = (String) pair.get(1);
                        }
                    }
                }
            }
        }
        return d;
    }
}
