package com.yiyundao.compensation.interfaces.controller.admin;

import com.yiyundao.compensation.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import com.yiyundao.compensation.security.SecurityAnnotations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/monitor")
@RequiredArgsConstructor
@SecurityAnnotations.IsAdmin
public class MonitorAdminController {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final Environment environment;

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        Map<String, Object> data = new HashMap<>();
        data.put("app", appInfo());
        data.put("jvm", jvmInfo());
        data.put("datasource", dbInfo());
        data.put("redis", redisInfo());
        return ApiResponse.success(data);
    }

    private Map<String, Object> appInfo() {
        Map<String, Object> m = new HashMap<>();
        String[] profiles = environment.getActiveProfiles();
        m.put("profiles", profiles);
        m.put("now", Instant.now().toString());
        long jvmStart = ManagementFactory.getRuntimeMXBean().getStartTime();
        m.put("uptimeSeconds", Duration.between(Instant.ofEpochMilli(jvmStart), Instant.now()).toSeconds());
        return m;
    }

    private Map<String, Object> jvmInfo() {
        Map<String, Object> m = new HashMap<>();
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = mem.getHeapMemoryUsage();
        m.put("heapInit", heap.getInit());
        m.put("heapUsed", heap.getUsed());
        m.put("heapCommitted", heap.getCommitted());
        m.put("heapMax", heap.getMax());
        m.put("threadCount", ManagementFactory.getThreadMXBean().getThreadCount());
        return m;
    }

    private Map<String, Object> dbInfo() {
        Map<String, Object> m = new HashMap<>();
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            m.put("ping", one != null && one == 1 ? "OK" : "FAILED");
        } catch (Exception e) {
            m.put("ping", "FAILED");
            m.put("error", e.getMessage());
        }
        return m;
    }

    private Map<String, Object> redisInfo() {
        Map<String, Object> m = new HashMap<>();
        try {
            String pong = stringRedisTemplate.getRequiredConnectionFactory().getConnection().ping();
            m.put("ping", pong);
        } catch (Exception e) {
            m.put("ping", "FAILED");
            m.put("error", e.getMessage());
        }
        return m;
    }
}

