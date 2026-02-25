package com.yiyundao.compensation.service;

/**
 * 定时任务处理器接口
 * <p>
 * 所有定时任务处理器都需要实现此接口。
 * 处理器通过 Spring Bean 名称与 ScheduledTask.handlerBean 字段关联。
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-02-01
 */
public interface TaskHandler {

    /**
     * 获取处理器支持的任务类型
     *
     * @return 任务类型标识
     */
    String getTaskType();

    /**
     * 执行任务
     *
     * @param taskKey  任务Key
     * @param taskData 任务数据（可选）
     * @return 执行结果
     * @throws Exception 执行异常
     */
    TaskExecutionResult execute(String taskKey, Object taskData) throws Exception;

    /**
     * 任务执行结果
     */
    record TaskExecutionResult(
            boolean success,
            String message,
            Object resultData
    ) {
        public static TaskExecutionResult success(String message) {
            return new TaskExecutionResult(true, message, null);
        }

        public static TaskExecutionResult success(String message, Object resultData) {
            return new TaskExecutionResult(true, message, resultData);
        }

        public static TaskExecutionResult failure(String message) {
            return new TaskExecutionResult(false, message, null);
        }

        public static TaskExecutionResult failure(String message, Object errorData) {
            return new TaskExecutionResult(false, message, errorData);
        }
    }
}
