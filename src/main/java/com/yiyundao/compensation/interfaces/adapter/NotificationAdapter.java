package com.yiyundao.compensation.interfaces.adapter;

import com.yiyundao.compensation.enums.NotificationChannel;
import com.yiyundao.compensation.modules.notification.entity.NotificationRecord;

/**
 * 通知适配器接口
 * 支持多平台通知发送（企微/钉钉/飞书/短信/邮件）
 */
public interface NotificationAdapter {

    /**
     * 获取支持的通知渠道
     */
    NotificationChannel getSupportedChannel();

    /**
     * 发送通知
     * @param record 通知记录
     * @return 发送结果
     */
    NotificationSendResult sendNotification(NotificationRecord record);

    /**
     * 检查连接状态
     */
    boolean checkConnection();

    /**
     * 通知发送结果
     */
    class NotificationSendResult {
        private boolean success;
        private String responseCode;
        private String responseMessage;
        private String errorMessage;

        public NotificationSendResult() {}

        public NotificationSendResult(boolean success, String responseCode, String responseMessage) {
            this.success = success;
            this.responseCode = responseCode;
            this.responseMessage = responseMessage;
        }

        public NotificationSendResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public static NotificationSendResult success(String responseCode, String responseMessage) {
            return new NotificationSendResult(true, responseCode, responseMessage);
        }

        public static NotificationSendResult failure(String errorMessage) {
            return new NotificationSendResult(false, errorMessage);
        }

        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getResponseCode() { return responseCode; }
        public void setResponseCode(String responseCode) { this.responseCode = responseCode; }

        public String getResponseMessage() { return responseMessage; }
        public void setResponseMessage(String responseMessage) { this.responseMessage = responseMessage; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
}