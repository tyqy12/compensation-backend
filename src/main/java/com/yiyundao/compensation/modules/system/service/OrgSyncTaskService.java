package com.yiyundao.compensation.modules.system.service;

import com.yiyundao.compensation.dto.OrganizationSyncResult;
import com.yiyundao.compensation.service.OrganizationSyncService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class OrgSyncTaskService {

    private final OrganizationSyncService organizationSyncService;
    private final ThreadPoolTaskExecutor executor;

    private final Map<String, TaskInfo> tasks = new ConcurrentHashMap<>();
    private static final int MAX_TASKS = 1000;

    public OrgSyncTaskService(OrganizationSyncService organizationSyncService) {
        this.organizationSyncService = organizationSyncService;
        this.executor = new ThreadPoolTaskExecutor();
        this.executor.setCorePoolSize(1);
        this.executor.setMaxPoolSize(2);
        this.executor.setQueueCapacity(100);
        this.executor.setThreadNamePrefix("org-sync-");
        this.executor.setWaitForTasksToCompleteOnShutdown(true);
        this.executor.setAwaitTerminationSeconds(30);
        this.executor.initialize();
    }

    public synchronized String start(String platform) {
        cleanupFinishedTasks();
        if (isRunningFor(platform)) {
            throw new IllegalStateException("该平台已有组织同步任务运行中");
        }
        if (tasks.size() >= MAX_TASKS) {
            throw new IllegalStateException("组织同步任务过多，请稍后再试");
        }
        String id = UUID.randomUUID().toString();
        TaskInfo info = new TaskInfo();
        info.setId(id);
        info.setPlatform(platform);
        info.setStatus("PENDING");
        info.setStartTime(LocalDateTime.now());
        tasks.put(id, info);

        executor.submit(() -> runTask(id, platform));
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

    public boolean isRunningFor(String platform) {
        for (TaskInfo t : tasks.values()) {
            if (("RUNNING".equalsIgnoreCase(t.getStatus()) || "PENDING".equalsIgnoreCase(t.getStatus())) &&
                (platform.equalsIgnoreCase(t.getPlatform()) || "all".equalsIgnoreCase(t.getPlatform()))) {
                return true;
            }
        }
        return false;
    }

    private void cleanupFinishedTasks() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        tasks.entrySet().removeIf(entry -> {
            TaskInfo task = entry.getValue();
            return task != null && task.getEndTime() != null && task.getEndTime().isBefore(cutoff);
        });
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
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
