# Clipboard Sync

在网页编辑器里打字，实时同步进 Minecraft 里的写字板；游戏里改了也会实时回传。

支持模组 **Useful Clipboard**（`usclb`）的写字板。

## 安装

1. 去右侧 **Releases** 下载 `clipboardsync-1.0.0.jar`。
2. 把它放进你的 `mods` 文件夹（联机时服务端 / 主机也要放）。
3. 重启游戏。

## 使用

1. 游戏里把手上的写字板（`usclb:clipboard`）拿在主手。
2. 双击 `editor.html`（Releases 里一起下载，用浏览器打开）。
3. 在网页里打字，约半秒就同步进游戏；游戏里改也会回传到网页。

## 说明

- **多人**：谁拿写字板、谁开编辑器，就同步谁的。各自开各自的编辑器即可。
- **端口**：默认 `8765`，被占用可改（见源码 `ClientSync.java` 和 `editor.html`）。
- **要求**：Minecraft 1.20.1 + Forge，且已安装 Useful Clipboard 模组。
- **许可**：MIT
