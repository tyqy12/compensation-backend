package com.yiyundao.compensation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "任务调度配置")
public class TaskScheduleDTO {

    @Schema(description = "任务唯一标识")
    @NotBlank(message = "任务标识不能为空")
    private String taskKey;

    @Schema(description = "任务名称")
    @NotBlank(message = "任务名称不能为空")
    private String taskName;

    @Schema(description = "任务分组")
    private String taskGroup = "DEFAULT";

    @Schema(description = "Cron 表达式")
    @NotBlank(message = "Cron 表达式不能为空")
    @Pattern(regexp = "^\\S+(\\s+\\S+){5,6}$", message = "无效的 Cron 表达式")
    private String cronExpression;

    @Schema(description = "任务描述")
    private String description;

    @Schema(description = "最大重试次数")
    private Integer maxRetryCount = 3;

    @Schema(description = "重试间隔（秒）")
    private Integer retryIntervalSeconds = 60;

    @Schema(description = "是否启用告警")
    private Boolean alarmEnabled = false;

    @Schema(description = "告警接收人（邮箱或手机号，逗号分隔）")
    private String alarmReceivers;

    @Schema(description = "任务处理器 Bean 名称")
    private String handlerBean;
}
