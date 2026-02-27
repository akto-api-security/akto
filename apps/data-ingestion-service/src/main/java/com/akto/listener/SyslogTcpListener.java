package com.akto.listener;

import com.akto.log.LoggerMaker;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TCP syslog listener for Apigee MessageLogging policy.
 *
 * Supports both newline-delimited framing and RFC6587 octet-counted framing.
 */
public class SyslogTcpListener implements Runnable {

    private static final LoggerMaker logger = new LoggerMaker(SyslogTcpListener.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static final int DEFAULT_PORT = 5140;
    private static final int ACCEPT_BACKLOG = 200;
    private static final int CONNECTION_THREAD_POOL_SIZE = 8;
    private static final int SOCKET_READ_TIMEOUT_MILLIS = 30_000;
    private static final int READ_BUFFER_SIZE = 8192;
    private static final int MAX_PENDING_BYTES = 2_000_000;
    private static final int MAX_OCTET_FRAME_BYTES = 1_000_000;

    private final int port;
    private final ExecutorService connectionExecutor;
    private final SyslogMessageProcessor messageProcessor;

    public SyslogTcpListener() {
        String portEnv = System.getenv("SYSLOG_TCP_PORT");
        this.port = (portEnv != null) ? Integer.parseInt(portEnv) : DEFAULT_PORT;
        this.connectionExecutor = Executors.newFixedThreadPool(CONNECTION_THREAD_POOL_SIZE, r -> {
            Thread t = new Thread(r, "syslog-tcp-processor-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        });
        this.messageProcessor = new SyslogMessageProcessor();
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port, ACCEPT_BACKLOG)) {
            logger.infoAndAddToDb("Syslog TCP listener started on port " + port, LoggerMaker.LogDb.DATA_INGESTION);

            while (true) {
                try {
                    Socket client = serverSocket.accept();
                    client.setSoTimeout(SOCKET_READ_TIMEOUT_MILLIS);
                    connectionExecutor.submit(() -> handleClient(client));
                } catch (Exception e) {
                    logger.errorAndAddToDb("Error accepting syslog TCP connection: " + e.getMessage(), LoggerMaker.LogDb.DATA_INGESTION);
                }
            }
        } catch (Exception e) {
            logger.errorAndAddToDb("Syslog TCP listener error: " + e.getMessage(), LoggerMaker.LogDb.DATA_INGESTION);
        }
    }

    private void handleClient(Socket socket) {
        try (Socket client = socket; InputStream in = client.getInputStream()) {
            ByteArrayOutputStream pending = new ByteArrayOutputStream();
            byte[] readBuffer = new byte[READ_BUFFER_SIZE];

            while (true) {
                int read;
                try {
                    read = in.read(readBuffer);
                } catch (SocketTimeoutException timeout) {
                    break;
                }

                if (read == -1) {
                    break;
                }

                pending.write(readBuffer, 0, read);
                consumeFrames(pending, false);

                if (pending.size() > MAX_PENDING_BYTES) {
                    logger.warnAndAddToDb("Dropping oversized pending TCP syslog buffer from " + client.getRemoteSocketAddress(), LoggerMaker.LogDb.DATA_INGESTION);
                    pending.reset();
                }
            }

            consumeFrames(pending, true);
        } catch (Exception e) {
            logger.errorAndAddToDb("Error processing syslog TCP client: " + e.getMessage(), LoggerMaker.LogDb.DATA_INGESTION);
        }
    }

    private void consumeFrames(ByteArrayOutputStream pending, boolean eof) {
        byte[] data = pending.toByteArray();
        int end = data.length;
        int pos = 0;

        while (pos < end) {
            int octetResult = tryConsumeOctetCountedFrame(data, pos, end, eof);
            if (octetResult > pos) {
                pos = octetResult;
                continue;
            }
            if (octetResult == pos) {
                break; // Need more bytes for a full octet-counted frame.
            }

            int newline = indexOfByte(data, pos, end, (byte) '\n');
            if (newline == -1) {
                if (eof) {
                    processFrame(data, pos, end);
                    pos = end;
                }
                break;
            }

            processFrame(data, pos, newline);
            pos = newline + 1;
        }

        if (pos > 0) {
            pending.reset();
            if (pos < end) {
                pending.write(data, pos, end - pos);
            }
        }
    }

    /**
     * @return > start when bytes were consumed, == start when more bytes are required,
     * -1 when data does not look like octet-counted framing.
     */
    private int tryConsumeOctetCountedFrame(byte[] data, int start, int end, boolean eof) {
        int i = start;
        int digits = 0;

        while (i < end && data[i] >= '0' && data[i] <= '9' && digits < 10) {
            i++;
            digits++;
        }

        if (digits == 0) {
            return -1;
        }
        if (i >= end) {
            return eof ? -1 : start;
        }
        if (data[i] != ' ') {
            return -1;
        }

        int frameLength;
        try {
            String lenStr = new String(data, start, digits, StandardCharsets.US_ASCII);
            frameLength = Integer.parseInt(lenStr);
        } catch (Exception ignored) {
            return -1;
        }

        if (frameLength < 0 || frameLength > MAX_OCTET_FRAME_BYTES) {
            return -1;
        }

        int frameStart = i + 1;
        int frameEnd = frameStart + frameLength;
        if (frameEnd > end) {
            return eof ? -1 : start;
        }

        processFrame(data, frameStart, frameEnd);
        return frameEnd;
    }

    private int indexOfByte(byte[] data, int from, int to, byte needle) {
        for (int i = from; i < to; i++) {
            if (data[i] == needle) {
                return i;
            }
        }
        return -1;
    }

    private void processFrame(byte[] data, int start, int end) {
        int s = start;
        int e = end;

        while (s < e && (data[s] == '\r' || data[s] == '\n' || data[s] == ' ' || data[s] == '\t')) {
            s++;
        }
        while (e > s && (data[e - 1] == '\r' || data[e - 1] == '\n' || data[e - 1] == ' ' || data[e - 1] == '\t')) {
            e--;
        }

        if (e <= s) {
            return;
        }

        String raw = new String(data, s, e - s, StandardCharsets.UTF_8);
        messageProcessor.processRaw(raw);
    }
}
