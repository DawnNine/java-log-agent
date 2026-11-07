package com.logmonitor.agent.heartbeat;

import com.logmonitor.agent.config.AgentConfig;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * 心跳任务
 * 定时向日志处理器发送心跳，上报Agent状态
 */
@Slf4j
@Component
public class HeartbeatTask {

    @Autowired
    private AgentConfig config;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    @Scheduled(fixedDelay = 10000) // 每10秒发送一次心跳
    public void sendHeartbeat() {
        try {
            Map<String, Object> heartbeat = new HashMap<>();
            heartbeat.put("host", config.getHost());
            heartbeat.put("appName", config.getAppName());
            heartbeat.put("ip", InetAddress.getLocalHost().getHostAddress());
            heartbeat.put("timestamp", System.currentTimeMillis());
            heartbeat.put("status", "RUNNING");

            String json = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(heartbeat);

            RequestBody body = RequestBody.create(
                    json, MediaType.parse("application/json"));

            Request request = new Request.Builder()
                    .url(config.getHeartbeatUrl())
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    log.debug("心跳发送成功");
                } else {
                    log.warn("心跳发送失败: {}", response.code());
                }
            }
        } catch (IOException e) {
            log.warn("心跳发送异常: {}", e.getMessage());
        }
    }
}
