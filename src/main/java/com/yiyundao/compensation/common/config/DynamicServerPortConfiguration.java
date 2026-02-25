package com.yiyundao.compensation.common.config;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

/**
 * 在开发环境中检测端口冲突，并自动回退到可用端口。
 */
@Slf4j
@Configuration
@Profile("dev")
public class DynamicServerPortConfiguration implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final String PROPERTY_SOURCE_NAME = "dynamicServerPort";
    private static final int DEFAULT_PORT = 8080;
    private static final int MAX_PORT = 65535;
    private static final int SEARCH_RANGE = 50;

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        Integer configuredPort = environment.getProperty("server.port", Integer.class, DEFAULT_PORT);

        if (configuredPort <= 0 || configuredPort > MAX_PORT) {
            configuredPort = DEFAULT_PORT;
        }

        if (isPortAvailable(configuredPort)) {
            return;
        }

        int startPort = Math.min(MAX_PORT, Math.max(configuredPort + 1, 1024));
        int endPort = Math.min(MAX_PORT, startPort + SEARCH_RANGE);
        int fallbackPort = findAvailablePort(startPort, endPort);

        if (fallbackPort == -1) {
            log.error("检测到端口 {} 已被占用，且在区间 [{}-{}] 内未找到可用端口，请手动指定 server.port。",
                configuredPort, startPort, endPort);
            return;
        }

        overrideServerPort(environment.getPropertySources(), fallbackPort);
        log.warn("检测到端口 {} 已被占用，自动切换到可用端口 {}。", configuredPort, fallbackPort);
    }

    private void overrideServerPort(MutablePropertySources propertySources, int port) {
        Map<String, Object> portProperties = new HashMap<>(1);
        portProperties.put("server.port", port);

        if (propertySources.contains(PROPERTY_SOURCE_NAME)) {
            propertySources.replace(PROPERTY_SOURCE_NAME, new MapPropertySource(PROPERTY_SOURCE_NAME, portProperties));
        } else {
            propertySources.addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, portProperties));
        }
    }

    private int findAvailablePort(int startPort, int endPort) {
        for (int port = startPort; port <= endPort; port++) {
            if (isPortAvailable(port)) {
                return port;
            }
        }
        return -1;
    }

    private boolean isPortAvailable(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(false);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }
}
