package com.yiyundao.compensation.modules.employee.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class EmployeeBatchImportResult {

    private int total;
    private int imported;
    private int skipped;
    private int bound;
    private List<String> errors = new ArrayList<>();
}
