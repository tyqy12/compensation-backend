package com.yiyundao.compensation.interfaces.dto.approval;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApprovalDecisionRequest {
    @NotNull
    private Long approverId;
    private String comment;
}

