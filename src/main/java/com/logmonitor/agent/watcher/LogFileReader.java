package com.logmonitor.agent.watcher;

import com.logmonitor.agent.sender.LogSender;
import com.logmonitor.common.model.LogEntry;
import com.logmonitor.common.util.LogParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * 日志文件读取器
 * 增量读取日志文件的新增行
 */
@Slf4j
@Component
public class LogFileReader {

    @Autowired
    private LogSender logSender;

    /**
     * 从指定位置开始读取文件新增内容
     *
     * @param file     日志文件
     * @param appName  应用名称
     * @param host     主机名
     * @param position 上次读取位置
     * @return 新的读取位置
     */
    public long readLines(File file, String appName, String host, long position) {
        long lineNumber = position; // 简化行号处理
        int readCount = 0;

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (position > raf.length()) {
                return 0;
            }

            raf.seek(position);

            String line;
            while ((line = raf.readLine()) != null) {
                // 处理编码
                String decodedLine = new String(
                        line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);

                if (!decodedLine.trim().isEmpty()) {
                    lineNumber++;
                    LogEntry entry = LogParser.parse(
                            decodedLine, file.getAbsolutePath(),
                            host, appName, lineNumber);
                    logSender.send(entry);
                    readCount++;
                }
            }

            if (readCount > 0) {
                log.debug("从文件 {} 读取了 {} 行日志", file.getName(), readCount);
            }

            return raf.getFilePointer();

        } catch (Exception e) {
            log.error("读取日志文件异常: {}", file.getAbsolutePath(), e);
            return position;
        }
    }
}
