package com.yiyundao.compensation.interfaces.dto.employee;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BatchImportRequest {
    @Valid
    @NotEmpty
    private List<EmployeeCreateRequest> employees;
}

