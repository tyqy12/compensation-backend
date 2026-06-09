package com.yiyundao.compensation.modules.user.dto;

public record UserPlatformBindingResult(Status status, String message, Long workflowId) {

    public enum Status {
        SUCCESS,
        PENDING_APPROVAL
    }

    public static UserPlatformBindingResult success() {
        return new UserPlatformBindingResult(Status.SUCCESS, "绑定成功", null);
    }

    public static UserPlatformBindingResult pendingApproval(Long workflowId, String message) {
        return new UserPlatformBindingResult(Status.PENDING_APPROVAL, message, workflowId);
    }

    public boolean pendingApproval() {
        return status == Status.PENDING_APPROVAL;
    }
}
