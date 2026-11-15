package com.whale.clipboardsync;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * 写字板数据模型（按「页标题 + 整本标题 + 每行正文/勾选」组织）。
 * 原版结构：BlockEntityTag.pages(5页) → 每页 ListTag(12行) → 每行 { header, button, text }
 * 原版的「页标题」= 该页所有行的 header（统一值）；「整本标题」= BlockEntityTag.header（原版空着没用）。
 */
public class ClipboardData {
    public static final int PAGE_COUNT = 5;
    public static final int LINE_COUNT = 12;

    public String author = "";
    public String boardId = "";
    public String boardName = "";          // 整本标题（写回 BlockEntityTag.header + clipboardsync_name）
    public int currentpage = 0;
    public final String[] pageTitles = new String[PAGE_COUNT];   // 每页标题（写进该页所有行 header）
    public final String[][] texts = new String[PAGE_COUNT][LINE_COUNT];
    public final int[][] buttons = new int[PAGE_COUNT][LINE_COUNT];

    public ClipboardData() {
        for (int i = 0; i < PAGE_COUNT; i++) pageTitles[i] = "";
        for (int p = 0; p < PAGE_COUNT; p++)
            for (int l = 0; l < LINE_COUNT; l++) { texts[p][l] = ""; buttons[p][l] = 0; }
    }

    public static ClipboardData empty() { return new ClipboardData(); }

    public static ClipboardData readFrom(ItemStack stack) {
        ClipboardData d = new ClipboardData();
        if (stack == null || stack.isEmpty() || !stack.hasTag()) return d;
        CompoundTag root = stack.getTag();
        if (root == null) return d;
        d.boardId = root.getString("clipboardsync_id");
        d.boardName = root.getString("clipboardsync_name");
        if (!root.contains("BlockEntityTag", Tag.TAG_COMPOUND)) return d;
        CompoundTag be = root.getCompound("BlockEntityTag");
        d.author = be.getString("author");
        d.currentpage = be.getInt("currentpage");
        String origHeader = be.getString("header");
        if (!origHeader.isEmpty()) d.boardName = origHeader;
        if (be.contains("pages", Tag.TAG_LIST)) {
            ListTag pages = be.getList("pages", Tag.TAG_LIST);
            for (int p = 0; p < Math.min(pages.size(), PAGE_COUNT); p++) {
                ListTag page = pages.getList(p);
                if (page.size() > 0) d.pageTitles[p] = page.getCompound(0).getString("header");
                for (int l = 0; l < Math.min(page.size(), LINE_COUNT); l++) {
                    CompoundTag line = page.getCompound(l);
                    d.texts[p][l] = line.getString("text");
                    d.buttons[p][l] = line.getByte("button");
                }
            }
        }
        return d;
    }

    /** 写回：页标题写进该页所有行 header；整本标题写进 BlockEntityTag.header + clipboardsync_name。 */
    public void writeTo(ItemStack stack) {
        CompoundTag root = stack.getOrCreateTag();
        CompoundTag be = root.getCompound("BlockEntityTag");
        be.putString("author", author);
        be.putInt("currentpage", currentpage);
        if (!boardName.isEmpty()) {
            be.putString("header", boardName);
            root.putString("clipboardsync_name", boardName);
        }
        ListTag newPages = new ListTag();
        for (int p = 0; p < PAGE_COUNT; p++) {
            ListTag page = new ListTag();
            for (int l = 0; l < LINE_COUNT; l++) {
                CompoundTag line = new CompoundTag();
                line.putString("header", pageTitles[p]);
                line.putString("text", texts[p][l]);
                line.putByte("button", (byte) buttons[p][l]);
                page.add(line);
            }
            newPages.add(page);
        }
        be.put("pages", newPages);
        root.put("BlockEntityTag", be);
    }

    public String fingerprint() {
        StringBuilder sb = new StringBuilder();
        sb.append(author).append('\n').append(currentpage).append('\n').append(boardName).append('\n');
        for (int p = 0; p < PAGE_COUNT; p++) {
            sb.append(pageTitles[p]).append('\n');
            for (int l = 0; l < LINE_COUNT; l++)
                sb.append(texts[p][l]).append('\u0001').append(buttons[p][l]).append('\n');
        }
        return sb.toString();
    }

    public ClipboardData copy() {
        ClipboardData d = new ClipboardData();
        d.author = author; d.boardId = boardId; d.boardName = boardName; d.currentpage = currentpage;
        for (int p = 0; p < PAGE_COUNT; p++) {
            d.pageTitles[p] = pageTitles[p];
            for (int l = 0; l < LINE_COUNT; l++) { d.texts[p][l] = texts[p][l]; d.buttons[p][l] = buttons[p][l]; }
        }
        return d;
    }

    /** JSON：id / name / currentpage / pageTitles[5] / pages(5页×12行，每行 [text, button]) */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"id\":\"").append(Json.escape(boardId))
          .append("\",\"name\":\"").append(Json.escape(boardName))
          .append("\",\"currentpage\":").append(currentpage)
          .append(",\"pageTitles\":[");
        for (int p = 0; p < PAGE_COUNT; p++) {
            if (p > 0) sb.append(',');
            sb.append('"').append(Json.escape(pageTitles[p])).append('"');
        }
        sb.append("],\"pages\":[");
        for (int p = 0; p < PAGE_COUNT; p++) {
            if (p > 0) sb.append(',');
            sb.append('[');
            for (int l = 0; l < LINE_COUNT; l++) {
                if (l > 0) sb.append(',');
                sb.append("[\"").append(Json.escape(texts[p][l])).append("\",")
                  .append(buttons[p][l]).append(']');
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
        if (m.get("id") instanceof String) d.boardId = (String) m.get("id");
        if (m.get("name") instanceof String) d.boardName = (String) m.get("name");
        Object cp = m.get("currentpage");
        if (cp instanceof Number) d.currentpage = ((Number) cp).intValue();
        Object pt = m.get("pageTitles");
        if (pt instanceof List) {
            List<?> tl = (List<?>) pt;
            for (int p = 0; p < Math.min(tl.size(), PAGE_COUNT); p++)
                if (tl.get(p) instanceof String) d.pageTitles[p] = (String) tl.get(p);
        }
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
                            List<?> arr = (List<?>) line;
                            if (arr.size() >= 1 && arr.get(0) instanceof String) d.texts[p][l] = (String) arr.get(0);
                            if (arr.size() >= 2 && arr.get(1) instanceof Number) d.buttons[p][l] = ((Number) arr.get(1)).intValue();
                        }
                    }
                }
            }
        }
        return d;
    }
}
