package org.est.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * 资源管理工具：安全地管理 ServerSocket 生命周期
 */
public final class ResourceManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceManager.class);

    /**
     * 安全地关闭 ServerSocket
     */
    public static void closeSocket(ServerSocket socket, String context) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
                LOGGER.debug("已关闭 ServerSocket ({})", context);
            } catch (IOException e) {
                LOGGER.warn("关闭 ServerSocket ({}) 时出错: {}", context, e.getMessage());
            }
        }
    }

    /**
     * 安全地创建 ServerSocket，失败时自动清理
     */
    public static ServerSocket createServerSocket(int port) throws IOException {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket(port);
            LOGGER.debug("成功创建 ServerSocket，端口: {}", port);
            return socket;
        } catch (IOException e) {
            // 如果创建失败，socket 应该是 null，但为了安全还是尝试关闭
            closeSocket(socket, "创建失败时");
            throw e;
        }
    }

    /**
     * 尝试绑定端口，带重试机制
     * 
     * @param port 端口号
     * @param maxRetries 最大重试次数
     * @param retryDelayMs 每次重试的延迟（毫秒）
     * @return 成功创建的 ServerSocket，失败返回 null
     */
    public static ServerSocket bindWithRetry(int port, int maxRetries, long retryDelayMs) {
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (attempt > 0) {
                LOGGER.info("重试绑定端口 {} (尝试 {}/{})...", port, attempt, maxRetries);
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOGGER.warn("重试等待被中断");
                    return null;
                }
            }

            try {
                ServerSocket socket = createServerSocket(port);
                LOGGER.info("成功绑定端口 {}", port);
                return socket;
            } catch (IOException e) {
                LOGGER.debug("绑定端口 {} 失败 (尝试 {}/{}): {}", port, attempt + 1, maxRetries + 1, e.getMessage());
                if (attempt == maxRetries) {
                    LOGGER.warn("达到最大重试次数，端口 {} 绑定失败", port);
                    return null;
                }
            }
        }
        return null;
    }
}

