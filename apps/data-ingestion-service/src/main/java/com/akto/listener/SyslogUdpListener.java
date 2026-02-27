package com.akto.listener;

import com.akto.log.LoggerMaker;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * UDP syslog listener for Apigee MessageLogging policy.
 */
public class SyslogUdpListener implements Runnable {

    private static final LoggerMaker logger = new LoggerMaker(SyslogUdpListener.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static final int BUFFER_SIZE = 65535; // Max UDP payload size
    private static final int DEFAULT_PORT = 5140;
    private static final int THREAD_POOL_SIZE = 4;

    private final int port;
    private final ExecutorService executorService;
    private final SyslogMessageProcessor messageProcessor;

    public SyslogUdpListener() {
        String portEnv = System.getenv("SYSLOG_UDP_PORT");
        this.port = (portEnv != null) ? Integer.parseInt(portEnv) : DEFAULT_PORT;
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE, r -> {
            Thread t = new Thread(r, "syslog-udp-processor-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        });
        this.messageProcessor = new SyslogMessageProcessor();
    }

    @Override
    public void run() {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(port);
            logger.infoAndAddToDb("Syslog UDP listener started on port " + port, LoggerMaker.LogDb.DATA_INGESTION);

            byte[] buffer = new byte[BUFFER_SIZE];
            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, BUFFER_SIZE);
                    socket.receive(packet);

                    // Copy packet bytes before async handoff; DatagramPacket reuses the same backing buffer.
                    int packetLength = packet.getLength();
                    byte[] packetCopy = new byte[packetLength];
                    System.arraycopy(packet.getData(), packet.getOffset(), packetCopy, 0, packetLength);

                    executorService.submit(() -> messageProcessor.processPacket(packetCopy, packetLength));
                } catch (Exception e) {
                    logger.errorAndAddToDb("Error receiving syslog UDP packet: " + e.getMessage(), LoggerMaker.LogDb.DATA_INGESTION);
                }
            }
        } catch (Exception e) {
            logger.errorAndAddToDb("Syslog UDP listener error: " + e.getMessage(), LoggerMaker.LogDb.DATA_INGESTION);
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }
}
