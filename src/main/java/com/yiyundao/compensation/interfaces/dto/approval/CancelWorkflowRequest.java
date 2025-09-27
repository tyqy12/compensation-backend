package com.yiyundao.compensation.interfaces.dto.approval;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CancelWorkflowRequest {
    @NotNull
    private Long operatorId;
    private String reason;
}

