package org.est.forward;

import org.est.config.AppConfig;
import org.est.net.IpWhitelist;
import org.est.util.ProcessKiller;
import org.est.util.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Forwarder implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Forwarder.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private final AppConfig.Remote remoteConfig;
    private final AppConfig.Local localConfig;
    private final IpWhitelist whitelist;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket serverSocket;

    public Forwarder(AppConfig.Remote remoteConfig,
                     AppConfig.Local localConfig,
                     IpWhitelist whitelist) {
        this.remoteConfig = Objects.requireNonNull(remoteConfig);
        this.localConfig = Objects.requireNonNull(localConfig);
        this.whitelist = Objects.requireNonNull(whitelist);
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("转发器已经在运行中");
        }

        int port = localConfig.listenPort();
        
        // 清理可能存在的旧资源
        cleanupResources("启动前");

        // 步骤 1: 尝试直接绑定端口
        try {
            this.serverSocket = ResourceManager.createServerSocket(port);
            startAcceptLoop();
            LOGGER.info("TCP转发已启动，监听端口 {} -> {}:{}", port, remoteConfig.host(), remoteConfig.port());
            return;
        } catch (BindException e) {
            LOGGER.info("端口 {} 被占用，开始处理...", port);
            // 继续到步骤 2
        } catch (IOException e) {
            running.set(false);
            cleanupResources("启动失败");
            throw e;
        }

        // 步骤 2: 尝试终止同名进程
        LOGGER.info("尝试自动终止占用端口 {} 的同名进程...", port);
        boolean killed = ProcessKiller.killProcessOnPort(port);
        
        // 步骤 3: 等待端口释放并重试
        int maxRetries = killed ? 3 : 5; // 如果杀死了进程，少重试几次
        long retryDelay = killed ? 1000 : 2000; // 如果没杀死进程，等待更久（可能是 TIME_WAIT）
        
        if (killed) {
            LOGGER.info("同名进程已终止，等待端口释放...");
        } else {
            LOGGER.info("未检测到占用端口的进程，可能是 TIME_WAIT 状态，等待端口释放...");
        }

        ServerSocket retrySocket = ResourceManager.bindWithRetry(port, maxRetries, retryDelay);
        
        if (retrySocket != null) {
            this.serverSocket = retrySocket;
            startAcceptLoop();
            LOGGER.info("TCP转发已启动，监听端口 {} -> {}:{}", port, remoteConfig.host(), remoteConfig.port());
            return;
        }

        // 步骤 4: 所有重试都失败，抛出异常
        running.set(false);
        cleanupResources("重试失败");
        String errorMsg = String.format(
                "无法绑定端口 %d：端口已被占用。\n" +
                "解决方案：\n" +
                "1. 检查是否有其他程序占用该端口：netstat -ano | findstr :%d\n" +
                "2. 关闭占用端口的进程，或修改 application.yaml 中的 local.listenPort 为其他端口\n" +
                "3. 检查是否有之前的 MinecraftLanRedirect 实例仍在运行\n" +
                "4. 如果是 TIME_WAIT 状态，请等待 2-4 分钟后重试",
                port, port);
        LOGGER.error(errorMsg);
        throw new IOException(errorMsg);
    }

    /**
     * 启动接受连接的循环
     */
    private void startAcceptLoop() {
        executor.submit(this::acceptLoop);
    }

    /**
     * 清理所有资源
     */
    private void cleanupResources(String context) {
        ResourceManager.closeSocket(this.serverSocket, context);
        this.serverSocket = null;
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                if (serverSocket == null || serverSocket.isClosed()) {
                    LOGGER.warn("ServerSocket 未初始化或已关闭，退出 acceptLoop");
                    break;
                }
                Socket client = serverSocket.accept();
                InetAddress address = client.getInetAddress();
                if (!whitelist.isAllowed(address)) {
                    LOGGER.warn("拒绝未在白名单中的连接: {}", address);
                    client.close();
                    continue;
                }
                executor.submit(() -> handleClient(client));
            } catch (SocketException se) {
                if (running.get()) {
                    LOGGER.error("监听套接字错误", se);
                }
            } catch (IOException e) {
                if (running.get()) {
                    LOGGER.error("接受连接失败", e);
                }
            }
        }
        LOGGER.debug("acceptLoop 已退出");
    }

    private void handleClient(Socket client) {
        InetSocketAddress clientAddress = (InetSocketAddress) client.getRemoteSocketAddress();
        LOGGER.info("客户端连接: {}", clientAddress);
        try (Socket remote = new Socket()) {
            remote.connect(new InetSocketAddress(remoteConfig.host(), remoteConfig.port()),
                    (int) CONNECT_TIMEOUT.toMillis());
            remote.setTcpNoDelay(true);
            client.setTcpNoDelay(true);
            CompletableFuture<Void> upstream = CompletableFuture.runAsync(() -> {
                try {
                    pipe(client.getInputStream(), remote.getOutputStream());
                } catch (IOException e) {
                    LOGGER.error("上游数据流异常", e);
                }
            }, executor);
            CompletableFuture<Void> downstream = CompletableFuture.runAsync(() -> {
                try {
                    pipe(remote.getInputStream(), client.getOutputStream());
                } catch (IOException e) {
                    LOGGER.error("下游数据流异常", e);
                }
            }, executor);
            CompletableFuture.allOf(upstream, downstream).join();
        } catch (Exception e) {
            LOGGER.error("转发会话异常: {}", clientAddress, e);
        } finally {
            try {
                client.close();
            } catch (IOException ignored) {
            }
            LOGGER.info("客户端断开: {}", clientAddress);
        }
    }

    private void pipe(InputStream in, OutputStream out) {
        byte[] buffer = new byte[16 * 1024];
        try {
            int len;
            while ((len = in.read(buffer)) >= 0) {
                out.write(buffer, 0, len);
                out.flush();
            }
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            LOGGER.debug("转发器已经关闭或未启动");
            return;
        }
        LOGGER.info("正在关闭 TCP 转发器，释放端口 {}...", localConfig.listenPort());
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                LOGGER.debug("ServerSocket 已关闭");
            } catch (IOException e) {
                LOGGER.warn("关闭 ServerSocket 时发生异常", e);
            }
        }
        executor.shutdownNow();
        try {
            // 等待线程池关闭，最多等待 5 秒
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                LOGGER.warn("线程池未能在 5 秒内完全关闭");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("等待线程池关闭时被中断");
        }
        LOGGER.info("TCP转发已关闭，端口 {} 已释放", localConfig.listenPort());
    }
}

