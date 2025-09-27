package com.yiyundao.compensation.modules.system.service;

import com.yiyundao.compensation.dto.OrganizationSyncResult;
import com.yiyundao.compensation.service.OrganizationSyncService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrgSyncTaskService {

    private final OrganizationSyncService organizationSyncService;

    private final Map<String, TaskInfo> tasks = new ConcurrentHashMap<>();

    public String start(String platform) {
        String id = UUID.randomUUID().toString();
        TaskInfo info = new TaskInfo();
        info.setId(id);
        info.setPlatform(platform);
        info.setStatus("PENDING");
        info.setStartTime(LocalDateTime.now());
        tasks.put(id, info);

        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(1);
        exec.setMaxPoolSize(2);
        exec.setThreadNamePrefix("org-sync-");
        exec.initialize();
        exec.submit(() -> runTask(id, platform));
        return id;
    }

    private void runTask(String id, String platform) {
        TaskInfo info = tasks.get(id);
        if (info == null) return;
        info.setStatus("RUNNING");
        try {
            if ("all".equalsIgnoreCase(platform)) {
                List<OrganizationSyncResult> results = organizationSyncService.syncAllPlatforms();
                info.setResult("synced all: " + results.size());
            } else {
                OrganizationSyncResult r = organizationSyncService.syncPlatform(platform);
                info.setResult("synced " + platform + ": " + (r.isSuccess() ? "OK" : "FAILED"));
            }
            info.setStatus("SUCCEEDED");
        } catch (Exception e) {
            info.setStatus("FAILED");
            info.setError(e.getMessage());
        } finally {
            info.setEndTime(LocalDateTime.now());
        }
    }

    public TaskInfo get(String id) {
        return tasks.get(id);
    }

    @Data
    public static class TaskInfo {
        private String id;
        private String platform;
        private String status;
        private String result;
        private String error;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }
}

