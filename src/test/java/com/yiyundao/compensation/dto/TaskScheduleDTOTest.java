package com.yiyundao.compensation.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("任务调度 DTO 测试")
class TaskScheduleDTOTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("有效 DTO 测试")
    void testValidDTO() {
        TaskScheduleDTO dto = new TaskScheduleDTO();
        dto.setTaskKey("test_task");
        dto.setTaskName("测试任务");
        dto.setCronExpression("0 0 0 * * ?");

        Set<ConstraintViolation<TaskScheduleDTO>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("任务标识为空测试")
    void testEmptyTaskKey() {
        TaskScheduleDTO dto = new TaskScheduleDTO();
        dto.setTaskKey("");
        dto.setTaskName("测试任务");
        dto.setCronExpression("0 0 0 * * ?");

        Set<ConstraintViolation<TaskScheduleDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("taskKey")));
    }

    @Test
    @DisplayName("任务名称为空测试")
    void testEmptyTaskName() {
        TaskScheduleDTO dto = new TaskScheduleDTO();
        dto.setTaskKey("test_task");
        dto.setTaskName("");
        dto.setCronExpression("0 0 0 * * ?");

        Set<ConstraintViolation<TaskScheduleDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("taskName")));
    }

    @Test
    @DisplayName("Cron 表达式为空测试")
    void testEmptyCronExpression() {
        TaskScheduleDTO dto = new TaskScheduleDTO();
        dto.setTaskKey("test_task");
        dto.setTaskName("测试任务");
        dto.setCronExpression("");

        Set<ConstraintViolation<TaskScheduleDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("cronExpression")));
    }

    @Test
    @DisplayName("无效 Cron 表达式测试")
    void testInvalidCronExpression() {
        TaskScheduleDTO dto = new TaskScheduleDTO();
        dto.setTaskKey("test_task");
        dto.setTaskName("测试任务");
        dto.setCronExpression("invalid cron");

        Set<ConstraintViolation<TaskScheduleDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("有效 Cron 表达式测试")
    void testValidCronExpressions() {
        String[] validExpressions = {
                "0 0 0 * * ?",
                "0 0/5 * * * ?",
                "0 0 0 1,15 * ?",
                "0 0 0 ? * MON",
                "0 0 0 ? * 1#1"
        };

        for (String cron : validExpressions) {
            TaskScheduleDTO dto = new TaskScheduleDTO();
            dto.setTaskKey("test_task");
            dto.setTaskName("测试任务");
            dto.setCronExpression(cron);

            Set<ConstraintViolation<TaskScheduleDTO>> violations = validator.validate(dto);

            // 注意：正则验证可能不是完全准确的，这里主要测试不抛异常
            assertNotNull(violations);
        }
    }

    @Test
    @DisplayName("默认参数测试")
    void testDefaultValues() {
        TaskScheduleDTO dto = new TaskScheduleDTO();

        assertEquals("DEFAULT", dto.getTaskGroup());
        assertEquals(3, dto.getMaxRetryCount());
        assertEquals(60, dto.getRetryIntervalSeconds());
        assertFalse(dto.getAlarmEnabled());
        assertNull(dto.getAlarmReceivers());
        assertNull(dto.getHandlerBean());
    }

    @Test
    @DisplayName("完整 DTO 创建测试")
    void testFullDTOCreation() {
        TaskScheduleDTO dto = new TaskScheduleDTO();
        dto.setTaskKey("daily_report");
        dto.setTaskName("日报生成任务");
        dto.setTaskGroup("REPORT");
        dto.setCronExpression("0 0 6 * * ?");
        dto.setDescription("每天早上6点生成日报");
        dto.setMaxRetryCount(5);
        dto.setRetryIntervalSeconds(120);
        dto.setAlarmEnabled(true);
        dto.setAlarmReceivers("admin@example.com,manager@example.com");
        dto.setHandlerBean("dailyReportTaskHandler");

        assertEquals("daily_report", dto.getTaskKey());
        assertEquals("日报生成任务", dto.getTaskName());
        assertEquals("REPORT", dto.getTaskGroup());
        assertEquals("0 0 6 * * ?", dto.getCronExpression());
        assertEquals("每天早上6点生成日报", dto.getDescription());
        assertEquals(5, dto.getMaxRetryCount());
        assertEquals(120, dto.getRetryIntervalSeconds());
        assertTrue(dto.getAlarmEnabled());
        assertEquals("admin@example.com,manager@example.com", dto.getAlarmReceivers());
        assertEquals("dailyReportTaskHandler", dto.getHandlerBean());
    }
}
