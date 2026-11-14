package com.whale.clipboardsync;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** 客户端逻辑：本地 HTTP 桥，供网页编辑器读写；走游戏连接与服务端通信。 */
public class ClientSync {
    public static final int PORT = 8765;

    private static volatile String currentJson = null; // null = 尚未收到服务端真实状态
    private static MiniHttp server;

    public static void start() {
        if (server != null) return;
        try {
            server = new MiniHttp(PORT);
        } catch (IOException e) {
            System.err.println("[clipboardsync] 本地桥启动失败(端口" + PORT + "): " + e.getMessage());
        }
    }

    /** 服务端推送的最新状态。 */
    public static void onServerState(String json) {
        currentJson = json;
    }

    public static String getState() {
        return currentJson;
    }

    /** 编辑器提交 → 经游戏连接发给服务端。 */
    public static void pushUpdate(String json) {
        ModNetwork.sendToServer(json);
    }

    private static String corsHeaders() {
        return "Access-Control-Allow-Origin: *\r\n"
             + "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n"
             + "Access-Control-Allow-Headers: Content-Type\r\n";
    }

    /** 极简 HTTP 服务器，零依赖。 */
    private static final class MiniHttp {
        private final ServerSocket serverSocket;
        private final Thread thread;

        MiniHttp(int port) throws IOException {
            serverSocket = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"));
            thread = new Thread(this::loop, "clipboard-sync-http");
            thread.setDaemon(true);
            thread.start();
        }

        void loop() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket s = serverSocket.accept();
                    new Thread(() -> handle(s), "cs-http-conn").start();
                } catch (IOException ignored) {
                }
            }
        }

        void handle(Socket s) {
            try (Socket cs = s) {
                cs.setSoTimeout(8000);
                InputStream in = cs.getInputStream();
                OutputStream out = cs.getOutputStream();

                String head = readHeaders(in);
                if (head.isEmpty()) return;
                String[] lines = head.split("\r\n");
                String method = "", path = "";
                if (lines.length > 0) {
                    String[] parts = lines[0].split(" ");
                    if (parts.length >= 2) { method = parts[0]; path = parts[1]; }
                }
                int contentLength = 0;
                for (String l : lines) {
                    if (l.regionMatches(true, 0, "content-length:", 0, 15)) {
                        try { contentLength = Integer.parseInt(l.substring(15).trim()); }
                        catch (NumberFormatException ignored) {}
                    }
                }
                String body = "";
                if (contentLength > 0) {
                    byte[] b = new byte[contentLength];
                    int read = 0;
                    while (read < contentLength) {
                        int r = in.read(b, read, contentLength - read);
                        if (r < 0) break;
                        read += r;
                    }
                    body = new String(b, 0, read, StandardCharsets.UTF_8);
                }

                String response;
                if (method.equals("OPTIONS")) {
                    response = "HTTP/1.1 204 No Content\r\n" + corsHeaders() + "Content-Length: 0\r\n\r\n";
                } else if (method.equals("GET") && path.equals("/state")) {
                    if (currentJson == null) {
                        response = "HTTP/1.1 204 No Content\r\n" + corsHeaders() + "Content-Length: 0\r\n\r\n";
                    } else {
                        byte[] jb = currentJson.getBytes(StandardCharsets.UTF_8);
                        response = "HTTP/1.1 200 OK\r\n" + corsHeaders()
                                + "Content-Type: application/json; charset=utf-8\r\n"
                                + "Content-Length: " + jb.length + "\r\n\r\n" + currentJson;
                    }
                } else if (method.equals("POST") && path.equals("/update")) {
                    pushUpdate(body);
                    response = "HTTP/1.1 200 OK\r\n" + corsHeaders()
                            + "Content-Type: application/json; charset=utf-8\r\n"
                            + "Content-Length: 2\r\n\r\n{}";
                } else {
                    response = "HTTP/1.1 404 Not Found\r\n" + corsHeaders() + "Content-Length: 0\r\n\r\n";
                }
                out.write(response.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (Exception ignored) {
            }
        }

        static String readHeaders(InputStream in) throws IOException {
            StringBuilder sb = new StringBuilder();
            int a = -1, b = -1, c = -1;
            while (true) {
                int d = in.read();
                if (d < 0) break;
                sb.append((char) d);
                if (a == '\r' && b == '\n' && c == '\r' && d == '\n') break;
                a = b; b = c; c = d;
            }
            return sb.toString();
        }
    }
}
