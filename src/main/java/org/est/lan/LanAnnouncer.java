package org.est.lan;

import org.est.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LanAnnouncer implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(LanAnnouncer.class);
    private final AppConfig.Lan lanConfig;
    private final AppConfig.Local localConfig;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private DatagramSocket socket;

    public LanAnnouncer(AppConfig.Lan lanConfig, AppConfig.Local localConfig) {
        this.lanConfig = Objects.requireNonNull(lanConfig);
        this.localConfig = Objects.requireNonNull(localConfig);
    }

    public void start() throws Exception {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        this.socket = new DatagramSocket();
        this.socket.setBroadcast(true);
        long interval = lanConfig.announceIntervalMillis();
        scheduler.scheduleAtFixedRate(this::broadcast, 0, interval, TimeUnit.MILLISECONDS);
        LOGGER.info("LAN 广播线程已启动，每 {} ms 广播一次", interval);
    }

    private void broadcast() {
        try {
            String payload = buildPayload();
            byte[] data = payload.getBytes(StandardCharsets.UTF_8);
            InetAddress address = InetAddress.getByName(lanConfig.broadcastAddress());
            DatagramPacket packet = new DatagramPacket(
                    data, data.length, address, lanConfig.broadcastPort());
            socket.send(packet);
            LOGGER.debug("已广播 LAN 包: {}", payload);
        } catch (Exception e) {
            LOGGER.error("广播 LAN 包失败", e);
        }
    }

    private String buildPayload() {
        // MOTD 完全由用户配置决定，不自动添加版本等信息
        return "[MOTD]" + lanConfig.motd() + "[/MOTD][AD]" + localConfig.listenPort() + "[/AD]";
    }

    @Override
    public void close() {
        running.set(false);
        scheduler.shutdownNow();
        if (socket != null) {
            socket.close();
        }
        LOGGER.info("LAN 广播已关闭");
    }
}

