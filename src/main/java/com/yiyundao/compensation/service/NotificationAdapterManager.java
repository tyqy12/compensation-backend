package com.yiyundao.compensation.service;

import com.yiyundao.compensation.enums.NotificationChannel;
import com.yiyundao.compensation.interfaces.adapter.NotificationAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知适配器管理器
 * 负责注册和管理所有通知适配器
 */
@Slf4j
@Configuration
public class NotificationAdapterManager {

    @Autowired
    private List<NotificationAdapter> adapters;

    /**
     * 注册所有通知适配器到Map中，供依赖注入使用
     */
    @Bean
    public Map<NotificationChannel, NotificationAdapter> notificationAdapters() {
        Map<NotificationChannel, NotificationAdapter> adapterMap = new HashMap<>();

        for (NotificationAdapter adapter : adapters) {
            NotificationChannel channel = adapter.getSupportedChannel();
            adapterMap.put(channel, adapter);
            log.info("注册通知适配器: channel={}, adapter={}", channel, adapter.getClass().getSimpleName());
        }

        log.info("通知适配器注册完成，共注册{}个适配器", adapterMap.size());
        return adapterMap;
    }

    // 其他实例方法已移除；建议直接注入 Map<NotificationChannel, NotificationAdapter>
}
