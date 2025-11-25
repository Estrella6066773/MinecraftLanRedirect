package org.est;

import org.est.config.AppConfig;
import org.est.forward.Forwarder;
import org.est.lan.LanAnnouncer;
import org.est.net.IpWhitelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class Main {

    public static void main(String[] args) throws Exception {
        configureLoggingDefaults();
        AppConfig config = loadConfig(args);
        applyLogLevel(config.logging().level());
        Logger logger = LoggerFactory.getLogger(Main.class);
        IpWhitelist whitelist = IpWhitelist.from(config.security().whitelist());
        printStartupHints(logger, config);

        try (Forwarder forwarder = new Forwarder(config.remote(), config.local(), whitelist);
             LanAnnouncer announcer = new LanAnnouncer(config.lan(), config.local())) {
            logger.info("正在启动 TCP 转发器...");
            forwarder.start();
            logger.info("TCP 转发器已启动，监听端口 {}。", config.local().listenPort());

            logger.info("正在启动 LAN 广播线程...");
            announcer.start();
            logger.info("LAN 广播线程运行中，Minecraft 客户端应能在 LAN 列表看到伪装服务器。");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("收到关闭信号，正在清理资源...");
                try {
                    forwarder.close();
                    announcer.close();
                } catch (Exception ignored) {
                }
            }));
            logger.info("Velocity LAN 伪装器已启动，按 Ctrl+C 退出。");
            waitForever();
        } catch (Exception ex) {
            logger.error("启动过程中发生异常：{}", ex.getMessage(), ex);
            System.exit(1);
        }
    }

    private static void printStartupHints(Logger logger, AppConfig config) {
        logger.info("即将把本地端口 {} 转发至远程 {}:{}",
                config.local().listenPort(), config.remote().host(), config.remote().port());
        logger.info("LAN 广播：MOTD='{}' 版本='{}' 最大人数={} 广播地址={}:{} 每 {}ms",
                config.lan().motd(), config.lan().version(), config.lan().maxPlayers(),
                config.lan().broadcastAddress(), config.lan().broadcastPort(), config.lan().announceIntervalMillis());
        if (config.security().whitelist().isEmpty()) {
            logger.warn("当前未启用 IP 白名单，局域网内所有客户端均可连接。");
        } else {
            logger.info("IP 白名单生效，允许范围：{}", String.join(", ", config.security().whitelist()));
        }
        if (config.credentials().enabled()) {
            logger.info("远程代理启用了凭证，已携带 token。");
        } else {
            logger.info("未配置远程凭证，如远端需要鉴权请在 credentials 中启用。");
        }
        logger.info("如需修改配置，可编辑 application.yaml 或在启动时传入自定义路径。");
    }

    private static void waitForever() throws InterruptedException {
        while (true) {
            Thread.sleep(Long.MAX_VALUE);
        }
    }

    private static void configureLoggingDefaults() {
        if (System.getProperty("org.slf4j.simpleLogger.showDateTime") == null) {
            System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
        }
        if (System.getProperty("org.slf4j.simpleLogger.defaultLogLevel") == null) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");
        }
    }

    private static void applyLogLevel(String level) {
        if (level != null && !level.isBlank()) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", level.toLowerCase());
        }
    }

    private static AppConfig loadConfig(String[] args) throws Exception {
        if (args.length > 0) {
            Path external = Paths.get(args[0]);
            System.out.println("从 " + external.toAbsolutePath() + " 读取配置");
            return AppConfig.load(external);
        }
        Path cwdConfig = Paths.get("application.yaml");
        if (Files.exists(cwdConfig)) {
            System.out.println("从工作目录读取配置: " + cwdConfig.toAbsolutePath());
            return AppConfig.load(cwdConfig);
        }
        InputStream in = Main.class.getClassLoader().getResourceAsStream("application.yaml");
        if (in == null) {
            throw new IllegalStateException("未找到默认配置 application.yaml");
        }
        System.out.println("使用打包内置配置");
        return AppConfig.load(in);
    }
}
