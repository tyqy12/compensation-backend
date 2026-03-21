package com.yiyundao.compensation.modules.employee.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 平台绑定请求DTO
 *
 * @author 芙宁娜
 * @since 2026-01-28
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "平台绑定请求")
public class BindPlatformRequest {

    /**
     * 平台类型：wechat/dingtalk/feishu
     */
    @NotBlank(message = "平台类型不能为空")
    @Schema(description = "平台类型", example = "wechat",
            allowableValues = {"wechat", "dingtalk", "feishu"})
    private String provider;

    /**
     * 平台用户ID
     */
    @NotBlank(message = "平台用户ID不能为空")
    @Schema(description = "平台用户ID", example = "wx_user_123")
    private String subjectId;
    @JsonIgnore
    private String legacyPlatformType;
    @JsonIgnore
    private String legacyPlatformUserId;

    /**
     * 强制绑定（忽略冲突，直接覆盖）
     * 注意：使用此选项会触发审批流程
     */
    @Schema(description = "是否强制绑定（冲突时发起审批）", example = "false")
    @Builder.Default
    private boolean forceBind = false;

    @JsonAnySetter
    public void captureLegacyPlatformFields(String key, Object value) {
        if (value == null || key == null) {
            return;
        }
        if ("platformType".equals(key)) {
            this.legacyPlatformType = String.valueOf(value);
            return;
        }
        if ("platformUserId".equals(key)) {
            this.legacyPlatformUserId = String.valueOf(value);
        }
    }
}
