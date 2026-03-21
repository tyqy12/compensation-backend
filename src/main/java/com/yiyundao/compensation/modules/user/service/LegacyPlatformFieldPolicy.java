package com.yiyundao.compensation.modules.user.service;

import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 旧平台字段下线策略（渐进式）
 * mode:
 * - compat: 兼容接受，不告警
 * - warn: 兼容接受，记录告警
 * - reject: 拒绝旧字段输入
 */
@Slf4j
@Component
public class LegacyPlatformFieldPolicy {

    @Getter
    private final Mode mode;
    @Getter
    private final Mode workflowDataMode;

    public LegacyPlatformFieldPolicy(@Value("${legacy.platform-field.mode:warn}") String rawMode,
                                     @Value("${legacy.platform-field.workflow-data-mode:warn}") String rawWorkflowDataMode) {
        this.mode = Mode.from(rawMode);
        this.workflowDataMode = Mode.from(rawWorkflowDataMode);
    }

    public void handleLegacyInput(String scene, String platformType, String platformUserId) {
        if (!StringUtils.hasText(platformType) && !StringUtils.hasText(platformUserId)) {
            return;
        }
        if (mode == Mode.REJECT) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "平台字段(platformType/platformUserId)已下线，请使用统一绑定接口");
        }
        if (mode == Mode.WARN) {
            log.warn("检测到旧平台字段输入: scene={}, mode={}, platformType={}, platformUserId={}",
                    scene, mode.name().toLowerCase(), platformType, mask(platformUserId));
        } else {
            log.debug("兼容旧平台字段输入: scene={}, mode={}", scene, mode.name().toLowerCase());
        }
    }

    public void handleLegacyWorkflowFallback(String scene,
                                             Long workflowId,
                                             String preferredKey,
                                             String legacyKey,
                                             String legacyValue) {
        if (!StringUtils.hasText(legacyValue)) {
            return;
        }
        if (workflowDataMode == Mode.REJECT) {
            throw new IllegalArgumentException("审批字段(" + legacyKey + ")已下线，请使用" + preferredKey);
        }
        if (workflowDataMode == Mode.WARN) {
            log.warn("审批数据命中旧字段回退: scene={}, mode={}, workflowId={}, preferredKey={}, legacyKey={}",
                    scene, workflowDataMode.name().toLowerCase(), workflowId, preferredKey, legacyKey);
        } else {
            log.debug("审批数据命中旧字段回退: scene={}, mode={}, workflowId={}, preferredKey={}, legacyKey={}",
                    scene, workflowDataMode.name().toLowerCase(), workflowId, preferredKey, legacyKey);
        }
    }

    private String mask(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String text = value.trim();
        if (text.length() <= 4) {
            return "****";
        }
        return text.substring(0, 2) + "****" + text.substring(text.length() - 2);
    }

    public enum Mode {
        COMPAT, WARN, REJECT;

        public static Mode from(String raw) {
            if (!StringUtils.hasText(raw)) {
                return WARN;
            }
            String normalized = raw.trim().toUpperCase();
            try {
                return Mode.valueOf(normalized);
            } catch (Exception ignored) {
                return WARN;
            }
        }
    }
}
