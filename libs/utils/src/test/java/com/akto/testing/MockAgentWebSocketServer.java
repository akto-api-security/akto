package com.akto.testing;

import com.mongodb.BasicDBObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A chat agent over WebSocket, shaped like a real target: the handshake is refused (401) without a
 * cookie, a {"event":"user_message","data":{"session_id",...}} frame is answered by an ack, two
 * streamed chunks and a final frame, and the turn count is kept per session_id.
 */
final class MockAgentWebSocketServer implements AutoCloseable {

    /** One accepted or refused handshake: the request path and its headers (names lowercased). */
    static final class Handshake {
        final String path;
        final Map<String, String> headers;

        Handshake(String path, Map<String, String> headers) {
            this.path = path;
            this.headers = headers;
        }
    }

    final List<Handshake> handshakes = Collections.synchronizedList(new ArrayList<>());
    final List<String> receivedFrames = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Integer> turnsBySession = new ConcurrentHashMap<>();

    private final ServerSocket server;
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "mock-agent-ws");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean closed;
    /** When set, the server closes the connection right after each final frame (a dropped kept socket). */
    volatile boolean closeAfterReply;
    /** When >= 0, a "connect" greeting is sent this long after the handshake, from another thread (like a hub). */
    volatile long greetingDelayMs = -1;

    MockAgentWebSocketServer() throws IOException {
        server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        pool.submit(this::acceptLoop);
    }

    String url(String path) {
        return "ws://localhost:" + server.getLocalPort() + path;
    }

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket socket = server.accept();
                pool.submit(() -> handle(socket));
            } catch (IOException e) {
                return;
            }
        }
    }

    private void handle(Socket socket) {
        try (Socket s = socket) {
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();

            String[] head = readHead(in).split("\r\n");
            String path = head[0].split(" ")[1];
            Map<String, String> headers = new HashMap<>();
            for (int i = 1; i < head.length; i++) {
                int idx = head[i].indexOf(':');
                if (idx > 0) {
                    headers.put(head[i].substring(0, idx).trim().toLowerCase(), head[i].substring(idx + 1).trim());
                }
            }
            handshakes.add(new Handshake(path, headers));

            if (!headers.containsKey("cookie")) {
                out.write("HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                out.flush();
                return;
            }
            String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest(
                    (headers.get("sec-websocket-key") + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
            out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            long greetingDelay = greetingDelayMs;
            if (greetingDelay >= 0) {
                pool.submit(() -> {
                    try {
                        sendAfter(out, greetingDelay, new BasicDBObject("event", "connect").toJson());
                    } catch (Exception ignored) {
                    }
                });
            }

            while (true) {
                int b0 = in.read();
                if (b0 < 0) return;
                int opcode = b0 & 0x0F;
                byte[] payload = readPayload(in);
                if (opcode == 0x8) { // close: answer and stop
                    out.write(new byte[]{(byte) 0x88, 0});
                    out.flush();
                    return;
                }
                if (opcode == 0x9) { // ping
                    writeFrame(out, 0xA, payload);
                    continue;
                }
                if (opcode != 0x1) continue;

                String frame = new String(payload, StandardCharsets.UTF_8);
                receivedFrames.add(frame);
                String sessionId = sessionIdOf(frame);
                int turn = turnsBySession.merge(sessionId, 1, Integer::sum);

                sendAfter(out, 50, new BasicDBObject("event", "ack").append("session_id", sessionId).toJson());
                sendAfter(out, 250, new BasicDBObject("event", "chunk").append("text", "Hello ").toJson());
                sendAfter(out, 250, new BasicDBObject("event", "chunk").append("text", "world").toJson());
                sendAfter(out, 250, new BasicDBObject("event", "final").append("session_id", sessionId)
                        .append("turn", turn).toJson());
                if (closeAfterReply) {
                    Thread.sleep(50);
                    out.write(new byte[]{(byte) 0x88, 2, 0x03, (byte) 0xE8}); // close 1000
                    out.flush();
                    return;
                }
            }
        } catch (Exception e) {
            // the client went away: nothing to answer
        }
    }

    private static String sessionIdOf(String frame) {
        try {
            Object data = BasicDBObject.parse(frame).get("data");
            if (data instanceof Map) {
                Object sid = ((Map<?, ?>) data).get("session_id");
                return sid != null ? sid.toString() : "";
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static void sendAfter(OutputStream out, long delayMs, String text) throws Exception {
        Thread.sleep(delayMs);
        writeFrame(out, 0x1, text.getBytes(StandardCharsets.UTF_8));
    }

    private static synchronized void writeFrame(OutputStream out, int opcode, byte[] payload) throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x80 | opcode);
        if (payload.length < 126) {
            frame.write(payload.length);
        } else {
            frame.write(126);
            frame.write(payload.length >> 8);
            frame.write(payload.length & 0xFF);
        }
        frame.write(payload);
        out.write(frame.toByteArray());
        out.flush();
    }

    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int matched = 0;
        byte[] end = {'\r', '\n', '\r', '\n'};
        int b;
        while (matched < 4 && (b = in.read()) >= 0) {
            head.write(b);
            matched = (b == end[matched]) ? matched + 1 : (b == '\r' ? 1 : 0);
        }
        return head.toString("US-ASCII");
    }

    // a client frame is always masked
    private static byte[] readPayload(InputStream in) throws IOException {
        int b1 = in.read();
        boolean masked = (b1 & 0x80) != 0;
        long len = b1 & 0x7F;
        if (len == 126) {
            len = (in.read() << 8) | in.read();
        } else if (len == 127) {
            len = 0;
            for (int i = 0; i < 8; i++) len = (len << 8) | in.read();
        }
        byte[] mask = new byte[4];
        if (masked) readFully(in, mask);
        byte[] payload = new byte[(int) len];
        readFully(in, payload);
        if (masked) {
            for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
        }
        return payload;
    }

    private static void readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) throw new IOException("stream ended");
            off += n;
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
        server.close();
        pool.shutdownNow();
    }
}
