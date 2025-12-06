package org.est;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.PatternLayoutEncoderBase;

public class ConditionalPatternLayout extends PatternLayout {
    
    @Override
    public String doLayout(ILoggingEvent event) {
        // 根据日志级别选择不同的格式
        if (event.getLevel().isGreaterOrEqual(ch.qos.logback.classic.Level.INFO)) {
            // INFO、WARN、ERROR级别使用简化格式：[HH:mm:ss INFO] 内容
            return String.format("[%s %s] %s%n", 
                formatTime(event.getTimeStamp()),
                event.getLevel(),
                event.getFormattedMessage());
        } else {
            // DEBUG级别使用详细格式：时间戳[线程名]DEBUG类名 - 内容
            return String.format("%d [%s] %s %s - %s%n",
                event.getTimeStamp(),
                event.getThreadName(),
                event.getLevel(),
                event.getLoggerName(),
                event.getFormattedMessage());
        }
    }
    
    private String formatTime(long timestamp) {
        // 将时间戳转换为 HH:mm:ss 格式
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss");
        return sdf.format(new java.util.Date(timestamp));
    }
    
    private String formatTimestamp(long timestamp) {
        // 将时间戳转换为可读格式：yyyy-MM-dd HH:mm:ss.SSS
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        return sdf.format(new java.util.Date(timestamp));
    }
    
    // 重写start方法以确保正确初始化
    @Override
    public void start() {
        // 确保pattern不为空
        if (getPattern() == null || getPattern().isEmpty()) {
            // 设置一个默认pattern，即使我们重写了doLayout方法
            setPattern("%d{HH:mm:ss} %-5level %logger{36} - %msg%n");
        }
        super.start();
    }
}
