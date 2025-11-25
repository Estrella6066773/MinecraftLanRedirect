package org.est.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 进程管理工具：检测端口占用并杀死同名进程
 */
public final class ProcessKiller {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessKiller.class);
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("windows");

    /**
     * 尝试杀死占用指定端口的同名进程
     *
     * @param port 端口号
     * @return 是否成功杀死进程
     */
    public static boolean killProcessOnPort(int port) {
        LOGGER.info("开始检测端口 {} 的占用情况...", port);
        Optional<Long> pidOpt = findProcessIdByPort(port);
        if (pidOpt.isEmpty()) {
            // 如果 netstat 检测不到，但端口确实被占用，可能是 TIME_WAIT 状态
            // 这种情况下无法通过终止进程来解决，需要等待
            LOGGER.info("端口 {} 未被 netstat 检测到占用进程", port);
            LOGGER.debug("如果端口确实被占用，可能是 TIME_WAIT 状态，需要等待自动释放");
            return false;
        }

        long pid = pidOpt.get();
        LOGGER.info("检测到端口 {} 被进程 PID {} 占用", port, pid);

        ProcessInfo info = getProcessInfo(pid);
        if (info == null) {
            LOGGER.warn("无法获取进程 PID {} 的信息，可能进程已退出", pid);
            return false;
        }

        LOGGER.info("进程信息 - PID: {}, 名称: {}, 命令行: {}", pid, info.name, 
                info.commandLine.length() > 200 ? info.commandLine.substring(0, 200) + "..." : info.commandLine);

        if (isSameProgram(info)) {
            LOGGER.info("检测到同名进程占用端口，正在终止进程 PID {}...", pid);
            if (killProcess(pid)) {
                LOGGER.info("成功终止进程 PID {}", pid);
                // 等待进程完全退出
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return true;
            } else {
                LOGGER.warn("终止进程 PID {} 失败，可能需要管理员权限", pid);
                return false;
            }
        } else {
            LOGGER.warn("端口 {} 被其他程序占用（PID: {}, 名称: {}），无法自动终止", port, pid, info.name);
            return false;
        }
    }

    /**
     * 通过端口号查找进程ID
     */
    private static Optional<Long> findProcessIdByPort(int port) {
        try {
            Process process;
            if (IS_WINDOWS) {
                // 使用 -ano 参数：-a 显示所有连接，-n 以数字形式显示，-o 显示进程ID
                process = new ProcessBuilder("netstat", "-ano").start();
            } else {
                process = new ProcessBuilder("lsof", "-ti", ":" + port).start();
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                String portStr = String.valueOf(port);
                List<String> matchingLines = new ArrayList<>();
                
                while ((line = reader.readLine()) != null) {
                    if (IS_WINDOWS) {
                        // Windows netstat 输出格式示例：
                        // TCP    0.0.0.0:9999           0.0.0.0:0              LISTENING       12345
                        // TCP    [::]:9999              [::]:0                 LISTENING       12345
                        // 需要匹配：协议 + 本地地址:端口 + 远程地址:端口 + 状态 + PID
                        line = line.trim();
                        if (line.contains(":" + portStr)) {
                            matchingLines.add(line);
                            // 尝试提取 PID（最后一列）
                            String[] parts = line.split("\\s+");
                            if (parts.length >= 5) {
                                try {
                                    long pid = Long.parseLong(parts[parts.length - 1]);
                                    // 只返回 LISTENING 状态的进程
                                    // 其他状态（TIME_WAIT、ESTABLISHED等）通常是已关闭或正在关闭的连接
                                    String state = parts.length >= 4 ? parts[parts.length - 2] : "";
                                    if ("LISTENING".equalsIgnoreCase(state)) {
                                        LOGGER.info("从 netstat 输出中找到 LISTENING 状态的 PID: {} (行: {})", pid, line);
                                        return Optional.of(pid);
                                    } else {
                                        LOGGER.debug("端口 {} 有连接但状态为 {} (非 LISTENING): {}", port, state, line);
                                    }
                                } catch (NumberFormatException e) {
                                    LOGGER.debug("无法解析 PID，行: {}", line);
                                }
                            }
                        }
                    } else {
                        // Linux/Mac: 直接输出PID
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty()) {
                            try {
                                long pid = Long.parseLong(trimmed);
                                return Optional.of(pid);
                            } catch (NumberFormatException e) {
                                // 忽略非数字行
                            }
                        }
                    }
                }
                
                // 如果没有找到 LISTENING 状态，但找到了其他状态的连接，记录日志
                if (!matchingLines.isEmpty()) {
                    LOGGER.info("端口 {} 有连接但无 LISTENING 状态，可能是 TIME_WAIT 或其他状态。匹配的行数: {}", 
                            port, matchingLines.size());
                    for (String match : matchingLines) {
                        LOGGER.debug("匹配行: {}", match);
                    }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                LOGGER.warn("查找端口进程命令退出码: {} (非零)", exitCode);
            }
        } catch (Exception e) {
            LOGGER.warn("查找端口 {} 占用进程时出错: {}", port, e.getMessage(), e);
        }
        return Optional.empty();
    }
    
    /**
     * 直接测试端口是否可用（通过尝试绑定）
     * 注意：此方法会短暂占用端口，调用后应立即使用，避免端口被其他进程占用
     */
    public static boolean isPortAvailable(int port) {
        try (java.net.ServerSocket testSocket = new java.net.ServerSocket(port)) {
            // 成功创建说明端口可用
            return true;
        } catch (java.io.IOException e) {
            // 创建失败说明端口被占用
            return false;
        }
    }

    /**
     * 获取进程信息
     */
    private static ProcessInfo getProcessInfo(long pid) {
        try {
            Process process;
            if (IS_WINDOWS) {
                // Windows: 使用 wmic
                process = new ProcessBuilder("wmic", "process", "where", "processid=" + pid,
                        "get", "name,commandline", "/format:list").start();
            } else {
                // Linux/Mac: 使用 ps
                process = new ProcessBuilder("ps", "-p", String.valueOf(pid), "-o", "comm=,args=").start();
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String name = null;
                String commandLine = null;
                String line;

                if (IS_WINDOWS) {
                    // Windows wmic 输出格式：
                    // Name=java.exe
                    // CommandLine=java -jar ...
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("Name=")) {
                            name = line.substring(5).trim();
                        } else if (line.startsWith("CommandLine=")) {
                            commandLine = line.substring(12).trim();
                        }
                    }
                } else {
                    // Linux/Mac ps 输出格式：
                    // java  java -jar ...
                    line = reader.readLine();
                    if (line != null) {
                        String[] parts = line.trim().split("\\s+", 2);
                        if (parts.length >= 1) {
                            name = parts[0];
                        }
                        if (parts.length >= 2) {
                            commandLine = parts[1];
                        }
                    }
                }

                if (name != null) {
                    return new ProcessInfo(name, commandLine != null ? commandLine : "");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                LOGGER.debug("获取进程信息命令退出码: {}", exitCode);
            }
        } catch (Exception e) {
            LOGGER.warn("获取进程 PID {} 信息时出错", pid, e);
        }
        return null;
    }

    /**
     * 判断是否是同一个程序
     */
    private static boolean isSameProgram(ProcessInfo info) {
        // 检查进程名是否是 java.exe 或 java
        if (!info.name.equalsIgnoreCase("java.exe") && !info.name.equals("java")) {
            LOGGER.debug("进程名不匹配: {} (期望: java 或 java.exe)", info.name);
            return false;
        }

        // 检查命令行是否包含 MinecraftLanRedirect 相关标识
        String cmdLower = info.commandLine.toLowerCase();
        boolean isMatch = cmdLower.contains("minecraftlanredirect") ||
               cmdLower.contains("minecraft-lan-redirect") ||
               cmdLower.contains("org.est.main") ||
               cmdLower.contains("org.est.forward") ||
               cmdLower.contains("org.est.lan");
        
        if (!isMatch) {
            LOGGER.debug("命令行不匹配: {}", info.commandLine.length() > 100 
                    ? info.commandLine.substring(0, 100) + "..." : info.commandLine);
        }
        
        return isMatch;
    }

    /**
     * 杀死进程
     */
    private static boolean killProcess(long pid) {
        try {
            Process process;
            if (IS_WINDOWS) {
                process = new ProcessBuilder("taskkill", "/F", "/PID", String.valueOf(pid)).start();
            } else {
                process = new ProcessBuilder("kill", "-9", String.valueOf(pid)).start();
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return true;
            } else {
                // 读取错误输出
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String error = reader.readLine();
                    if (error != null) {
                        LOGGER.debug("终止进程失败: {}", error);
                    }
                }
                return false;
            }
        } catch (Exception e) {
            LOGGER.warn("终止进程 PID {} 时出错", pid, e);
            return false;
        }
    }

    private static final class ProcessInfo {
        final String name;
        final String commandLine;

        ProcessInfo(String name, String commandLine) {
            this.name = name;
            this.commandLine = commandLine;
        }
    }
}

