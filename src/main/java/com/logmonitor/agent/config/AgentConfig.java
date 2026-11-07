package com.logmonitor.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentConfig {

    /** 本机标识 */
    private String host;

    /** 应用名称 */
    private String appName;

    /** 日志处理器URL */
    private String processorUrl;

    /** 心跳地址 */
    private String heartbeatUrl;

    /** 监控的日志文件列表 */
    private List<WatchFileConfig> watchFiles;

    /** 文件检查间隔(毫秒) */
    private long watchInterval = 1000;

    /** 批量发送大小 */
    private int batchSize = 50;

    /** 批量发送间隔(毫秒) */
    private long batchInterval = 500;

    /** 读取缓冲区大小 */
    private int bufferSize = 8192;

    /** 是否从文件末尾开始读取 */
    private boolean tailMode = true;

    @Data
    public static class WatchFileConfig {
        /** 日志文件路径 */
        private String path;
        /** 应用名称 */
        private String appName;
    }
}
