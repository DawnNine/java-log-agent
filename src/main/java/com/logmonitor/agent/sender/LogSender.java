package com.logmonitor.agent.sender;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.logmonitor.agent.config.AgentConfig;
import com.logmonitor.common.model.LogEntry;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 日志发送器
 * 批量发送日志到日志处理器
 */
@Slf4j
@Component
public class LogSender {

    @Autowired
    private AgentConfig config;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private final BlockingQueue<LogEntry> sendQueue = new LinkedBlockingQueue<>(50000);
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean running = new AtomicBoolean(true);

    @PostConstruct
    public void start() {
        // 定时批量发送
        scheduler.scheduleWithFixedDelay(this::flushBatch,
                config.getBatchInterval(),
                config.getBatchInterval(),
                TimeUnit.MILLISECONDS);

        log.info("日志发送器启动, 批量大小: {}, 发送间隔: {}ms",
                config.getBatchSize(), config.getBatchInterval());
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        scheduler.shutdown();
        // 发送剩余日志
        flushBatch();
        log.info("日志发送器已停止");
    }

    /**
     * 将日志加入发送队列
     */
    public void send(LogEntry entry) {
        if (!sendQueue.offer(entry)) {
            log.warn("发送队列已满，丢弃日志: {}", entry.getId());
        }
    }

    /**
     * 批量发送队列中的日志
     */
    private void flushBatch() {
        if (sendQueue.isEmpty()) {
            return;
        }

        List<LogEntry> batch = new ArrayList<>();
        sendQueue.drainTo(batch, config.getBatchSize());

        if (batch.isEmpty()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(batch);
            RequestBody body = RequestBody.create(
                    json, MediaType.parse("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url(config.getProcessorUrl() + "/batch")
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    log.debug("成功发送 {} 条日志到处理器", batch.size());
                } else {
                    log.warn("发送日志失败，响应码: {}, 消息: {}",
                            response.code(), response.body() != null ? response.body().string() : "");
                    // 失败时重新入队
                    requeueBatch(batch);
                }
            }
        } catch (JsonProcessingException e) {
            log.error("序列化日志失败", e);
        } catch (IOException e) {
            log.error("发送日志到处理器失败: {}", e.getMessage());
            requeueBatch(batch);
        }
    }

    private void requeueBatch(List<LogEntry> batch) {
        for (LogEntry entry : batch) {
            sendQueue.offer(entry);
        }
    }
}
