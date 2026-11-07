package com.logmonitor.agent.watcher;

import com.logmonitor.agent.config.AgentConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日志文件监控器
 * 负责监控配置的日志文件，检测文件变化并触发读取
 */
@Slf4j
@Component
public class LogFileWatcher {

    @Autowired
    private AgentConfig config;

    @Autowired
    private LogFileReader logFileReader;

    /** 日期占位符正则: {date} 或 {date:yyyy-MM-dd} */
    private static final Pattern DATE_PLACEHOLDER = Pattern.compile("\\{date(?::([^}]+))?\\}");

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(4);
    private final List<FileWatchTask> watchTasks = new ArrayList<>();

    @PostConstruct
    public void startWatching() {
        List<AgentConfig.WatchFileConfig> watchFiles = config.getWatchFiles();
        if (watchFiles == null || watchFiles.isEmpty()) {
            log.warn("没有配置需要监控的日志文件，Agent 将不会读取任何日志");
            return;
        }

        for (AgentConfig.WatchFileConfig wf : watchFiles) {
            String resolvedPath = resolvePlaceholders(wf.getPath());
            File file = new File(resolvedPath);
            log.info("开始监控日志文件: {} (原始配置: {}), 应用: {}", resolvedPath, wf.getPath(), wf.getAppName());
            FileWatchTask task = new FileWatchTask(file, resolvedPath, wf.getPath(),
                    wf.getAppName(), config.getHost());
            watchTasks.add(task);
            scheduler.scheduleWithFixedDelay(task, 0,
                    config.getWatchInterval(), TimeUnit.MILLISECONDS);
        }

        log.info("日志文件监控器启动完成，共监控 {} 个文件", watchTasks.size());
    }

    /**
     * 解析路径中的占位符
     * 支持: {date} 等价于 {date:yyyy-MM-dd}
     *       {date:yyyyMMdd} 自定义格式
     */
    static String resolvePlaceholders(String path) {
        Matcher matcher = DATE_PLACEHOLDER.matcher(path);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String format = matcher.group(1);
            if (format == null || format.isEmpty()) {
                format = "yyyy-MM-dd";
            }
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern(format));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(dateStr));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    @PreDestroy
    public void stopWatching() {
        log.info("正在停止日志文件监控器...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("日志文件监控器已停止");
    }

    /**
     * 单个文件监控任务
     */
    private class FileWatchTask implements Runnable {
        private final String rawPath;       // 原始配置路径(含占位符)
        private final String appName;
        private final String host;
        private String currentResolvedPath;  // 当前解析后的路径
        private File file;
        private long lastModified = 0;
        private long lastPosition = 0;
        private boolean initialized = false;
        private String lastDate;             // 记录日期, 用于检测跨天

        FileWatchTask(File file, String resolvedPath, String rawPath,
                      String appName, String host) {
            this.file = file;
            this.currentResolvedPath = resolvedPath;
            this.rawPath = rawPath;
            this.appName = appName;
            this.host = host;
            this.lastDate = LocalDate.now().toString();
        }

        @Override
        public void run() {
            try {
                // 检测跨天: 如果路径包含日期占位符, 每天重新解析路径
                if (rawPath.contains("{date")) {
                    String today = LocalDate.now().toString();
                    if (!today.equals(lastDate)) {
                        log.info("检测到日期变更: {} -> {}, 切换到当天日志文件", lastDate, today);
                        lastDate = today;
                        String newPath = resolvePlaceholders(rawPath);
                        if (!newPath.equals(currentResolvedPath)) {
                            currentResolvedPath = newPath;
                            file = new File(newPath);
                            lastModified = 0;
                            lastPosition = 0;
                            initialized = false;
                        }
                    }
                }

                if (!file.exists()) {
                    if (initialized) {
                        log.debug("日志文件不存在: {}", file.getAbsolutePath());
                    }
                    return;
                }

                long currentModified = file.lastModified();

                // 首次读取
                if (!initialized) {
                    initialized = true;
                    lastModified = currentModified;
                    if (config.isTailMode()) {
                        lastPosition = file.length(); // tail模式从末尾开始
                        log.info("tail模式: 从文件末尾开始监控 {}", file.getAbsolutePath());
                    } else {
                        lastPosition = 0;
                        log.info("从头开始读取文件 {}", file.getAbsolutePath());
                    }
                    return;
                }

                // 文件有变化
                if (currentModified > lastModified || file.length() > lastPosition) {
                    long newPosition = logFileReader.readLines(
                            file, appName, host, lastPosition);
                    if (newPosition > lastPosition) {
                        lastPosition = newPosition;
                    }
                    lastModified = currentModified;
                }

                // 文件被截断（轮转）
                if (file.length() < lastPosition) {
                    log.info("检测到日志文件轮转: {}", file.getAbsolutePath());
                    lastPosition = 0;
                }

            } catch (Exception e) {
                log.error("监控文件异常: {}", file != null ? file.getAbsolutePath() : currentResolvedPath, e);
            }
        }
    }
}
