# Clipboard Sync

为 Minecraft Forge 1.20.1 模组 **Useful Clipboard**（`usclb`）的写字板做的**双向实时同步**模组。

在浏览器里的网页编辑器中打字，内容会经游戏自己的连接实时同步进游戏里你手上的写字板；游戏里改了写字板，也会实时回传到网页。**零端口穿透、零同步盘**。

## 原理

```
网页编辑器 (editor.html)
   ↕ 连本机 127.0.0.1:8765（不出门）
你电脑上的客户端模组（本模组）
   ↕ 走游戏连接（多人联机那条现成的通道）
服务端上的本模组
   ↕
主手拿着的写字板 (usclb:clipboard)
```

- 客户端模组在本机开一个极简 HTTP 服务（默认端口 `8765`），供网页编辑器读写。
- 数据通过 Forge 网络通道在客户端与服务端之间传递，复用游戏本身的联机连接。
- 服务端权威地读写玩家主手写字板的 NBT。

## 数据结构

写字板数据位于物品 `tag.BlockEntityTag`：

```
BlockEntityTag
├── author (String)
├── currentpage (Int)
└── pages (ListTag)         5 页
      └── 每页 ListTag      12 行
            └── CompoundTag{ header, text, button }
```

本模组只同步每行的 `header` / `text`，`button`、`author`、`currentpage` 及坐标等其它字段原样保留。

## 编译

需要 **JDK 17** 与 **Gradle 8.x**（或用 IntelliJ IDEA 打开工程自动处理）。

```bash
gradle build
```

产物：`build/libs/clipboardsync-1.0.0.jar`

## 安装

1. 把 `clipboardsync-1.0.0.jar` 放进**双方**的 `mods` 文件夹（客户端与服务端都要装）。
2. 重启游戏。
3. 游戏里把手上的写字板（`usclb:clipboard`）拿在主手。
4. 双击 `editor.html` 用浏览器打开，即可编辑并同步。

> 谁用编辑器，就同步谁主手拿着的写字板。多人时各自开各自的编辑器。

## 端口

默认 `8765`。要改的话两处一起改：

- `ClientSync.java` 里的 `PORT`（改完重新编译）；
- `editor.html` 顶部的 `PORT`。

## 要求

- Minecraft 1.20.1 + Forge 47.x
- 已安装 Useful Clipboard（`usclb`）模组

## 许可

MIT
