package com.yiyundao.compensation.modules.system.service;

public interface SysConfigService {
    String getString(String key, String defaultValue);
    Integer getInt(String key, Integer defaultValue);
    Long getLong(String key, Long defaultValue);
    Boolean getBool(String key, Boolean defaultValue);
}

