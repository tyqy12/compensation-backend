package com.yiyundao.compensation.common.trace;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import jakarta.annotation.PostConstruct;

/**
 * 日志配置
 * <p>
 * 配置日志格式，集成链路追踪信息。
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-10
 */
@Slf4j
@Configuration
@Profile("!test")
public class LoggingConfig {

    @Value("${logging.format.trace-id-length:16}")
    private int traceIdLength;

    /**
     * 初始化日志配置
     */
    @PostConstruct
    public void initLogging() {
        try {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

            // 设置默认日志级别
            context.getLogger("ROOT").setLevel(Level.INFO);

            log.info("日志配置初始化完成，TraceId 长度: {}", traceIdLength);
        } catch (Exception e) {
            // 忽略配置错误
        }
    }
}
