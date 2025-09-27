package com.yiyundao.compensation.interfaces.dto.approval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class StartWorkflowRequest {
    @NotBlank
    private String workflowType; // WorkflowType code
    @NotBlank
    private String businessKey;
    @NotBlank
    private String businessType;
    @NotNull
    private Long initiatorId;
    private Map<String, Object> workflowData;
}

