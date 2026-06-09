package com.yiyundao.compensation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;

@Data
@TableName("scheduled_task_execution")
public class ScheduledTaskExecution {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private Long taskId;

    @TableField("task_key")
    private String taskKey;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("status")
    private ExecutionStatus status;

    @TableField("result")
    private String result;

    @TableField("error_message")
    private String errorMessage;

    @TableField("trace_id")
    private String traceId;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Getter
    public enum ExecutionStatus {
        RUNNING(0),
        SUCCESS(1),
        FAILED(2),
        RETRYING(3);

        @EnumValue
        private final int code;

        ExecutionStatus(int code) {
            this.code = code;
        }
    }
}
