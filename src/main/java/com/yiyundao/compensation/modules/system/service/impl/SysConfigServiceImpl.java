package com.yiyundao.compensation.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyundao.compensation.infrastructure.dao.SysConfigMapper;
import com.yiyundao.compensation.modules.system.entity.SysConfig;
import com.yiyundao.compensation.modules.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysConfigServiceImpl implements SysConfigService {

    private final SysConfigMapper sysConfigMapper;

    private SysConfig find(String key) {
        return sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key)
                .last("limit 1"));
    }

    @Override
    public String getString(String key, String defaultValue) {
        try {
            SysConfig c = find(key);
            if (c == null || c.getConfigValue() == null) return defaultValue;
            return c.getConfigValue();
        } catch (Exception e) {
            log.warn("read config failed: {}", key, e);
            return defaultValue;
        }
    }

    @Override
    public Integer getInt(String key, Integer defaultValue) {
        try {
            String v = getString(key, null);
            return v == null ? defaultValue : Integer.parseInt(v.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public Long getLong(String key, Long defaultValue) {
        try {
            String v = getString(key, null);
            return v == null ? defaultValue : Long.parseLong(v.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public Boolean getBool(String key, Boolean defaultValue) {
        try {
            String v = getString(key, null);
            return v == null ? defaultValue : Boolean.parseBoolean(v.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}

