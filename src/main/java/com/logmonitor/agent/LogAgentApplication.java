package com.logmonitor.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 日志转发 Agent 启动类
 *
 * 使用方式：
 *   java -jar log-agent.jar
 *   java -jar log-agent.jar --spring.config.location=file:./agent-config.yml
 *   java -jar log-agent.jar --agent.processor-url=http://192.168.1.100:8082/api/logs \
 *       --agent.host=server01 --agent.app-name=myapp \
 *       --agent.watch-files[0].path=/var/log/myapp/app.log \
 *       --agent.watch-files[0].app-name=myapp \
 *       --agent.watch-files[1].path=/var/log/tomcat/access_log.log \
 *       --agent.watch-files[1].app-name=tomcat
 */
@SpringBootApplication(exclude = {
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class,
        DataSourceAutoConfiguration.class
})
@EnableScheduling
@ComponentScan(basePackages = {"com.logmonitor.agent"})
public class LogAgentApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(LogAgentApplication.class);

        // 支持外部配置文件: 优先读取 jar 同级目录的 agent-config.yml
        // 可通过 --spring.config.location 或 --spring.config.additional-location 覆盖
        app.run(args);
    }
}
