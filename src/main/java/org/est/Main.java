package org.est;

import org.est.config.AppConfig;
import org.est.forward.Forwarder;
import org.est.lan.LanAnnouncer;
import org.est.net.IpWhitelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

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
        logger.info("如需修改配置，可编辑 config.yaml 或在启动时传入自定义路径。");
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
        Path configPath;
        
        // 如果命令行参数提供了路径，直接使用
        if (args.length > 0) {
            configPath = Paths.get(args[0]);
            if (!Files.exists(configPath)) {
                generateTemplateConfig(configPath);
                System.exit(0);
            }
            System.out.println("从指定路径读取配置: " + configPath.toAbsolutePath());
            return AppConfig.load(configPath);
        }
        
        // 自动查找配置文件，按优先级顺序
        // 1. jar 文件同目录下的 config.yaml
        Path jarDirConfig = getJarDirectory().resolve("config.yaml");
        if (Files.exists(jarDirConfig)) {
            System.out.println("从 jar 同目录读取配置: " + jarDirConfig.toAbsolutePath());
            return AppConfig.load(jarDirConfig);
        }
        
        // 2. 工作目录下的 config.yaml
        Path cwdConfig = Paths.get("config.yaml");
        if (Files.exists(cwdConfig)) {
            System.out.println("从工作目录读取配置: " + cwdConfig.toAbsolutePath());
            return AppConfig.load(cwdConfig);
        }
        
        // 3. 都找不到，生成模板到 jar 文件同目录
        System.out.println("未找到配置文件，正在自动查找...");
        System.out.println("  已检查: " + jarDirConfig.toAbsolutePath());
        System.out.println("  已检查: " + cwdConfig.toAbsolutePath());
        generateTemplateConfig(jarDirConfig);
        System.exit(0);
        return null; // 不会执行到这里
    }
    
    /**
     * 获取 jar 文件所在的目录
     * 如果是从 IDE 运行（非 jar），返回工作目录
     */
    private static Path getJarDirectory() {
        try {
            // 获取 Main.class 的位置
            java.net.URL location = Main.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation();
            
            // 处理 file:// 协议的路径
            String path = location.toURI().getPath();
            // Windows 路径可能以 / 开头，需要处理
            if (path.startsWith("/") && path.length() > 3 && path.charAt(2) == ':') {
                path = path.substring(1);
            }
            
            java.io.File file = new java.io.File(path);
            if (file.isFile()) {
                // 如果是 jar 文件，返回其所在目录
                return file.getParentFile().toPath();
            } else {
                // 如果是目录（开发环境），返回工作目录
                return Paths.get(System.getProperty("user.dir"));
            }
        } catch (Exception e) {
            // 出错时返回工作目录
            return Paths.get(System.getProperty("user.dir"));
        }
    }

    private static void generateTemplateConfig(Path configPath) throws Exception {
        System.out.println("未找到配置文件: " + configPath.toAbsolutePath());
        System.out.println("正在生成配置文件模板...");
        
        // 尝试从资源文件读取模板
        InputStream templateStream = Main.class.getClassLoader().getResourceAsStream("config.yaml");
        String templateContent;
        
        if (templateStream != null) {
            // 从资源文件读取模板
            try (Scanner scanner = new Scanner(templateStream, StandardCharsets.UTF_8.name())) {
                scanner.useDelimiter("\\A");
                templateContent = scanner.hasNext() ? scanner.next() : getDefaultTemplate();
            }
        } else {
            // 如果资源文件不存在，使用硬编码的模板
            templateContent = getDefaultTemplate();
        }
        
        // 写入配置文件
        Files.writeString(configPath, templateContent, StandardCharsets.UTF_8);
        System.out.println("配置文件模板已生成: " + configPath.toAbsolutePath());
        System.out.println("请编辑配置文件后重新运行程序。");
    }

    private static String getDefaultTemplate() {
        return "remote:\n" +
                "  host: proxy.example.com\n" +
                "  port: 25565\n" +
                "\n" +
                "local:\n" +
                "  listenPort: 9099\n" +
                "\n" +
                "lan:\n" +
                "  motd: \"&a远程Velocity代理\"\n" +
                "  version: \"1.21.10\"\n" +
                "  maxPlayers: 20\n" +
                "  announceIntervalMs: 1000\n" +
                "  broadcastPort: 4445\n" +
                "  broadcastAddress: 255.255.255.255\n" +
                "\n" +
                "security:\n" +
                "  # whitelist:\n" +
                "    # - 192.168.0.0/24\n" +
                "    #- fd00::/8\n" +
                "\n" +
                "credentials:\n" +
                "  enabled: false\n" +
                "  token: \"\"\n" +
                "\n" +
                "logging:\n" +
                "  level: INFO\n";
    }
}
