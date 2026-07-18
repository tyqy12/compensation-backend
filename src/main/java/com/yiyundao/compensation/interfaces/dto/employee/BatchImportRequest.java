package com.yiyundao.compensation.interfaces.dto.employee;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BatchImportRequest {
    @Valid
    @NotEmpty
    @Size(max = 500, message = "单次最多导入500名员工")
    private List<EmployeeCreateRequest> employees;
}
