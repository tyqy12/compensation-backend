package com.yiyundao.compensation.modules.payroll.service;

import org.springframework.web.multipart.MultipartFile;

public interface PayrollImportService {
    /** 解析并校验CSV，返回预览统计（JSON字符串，后续可换DTO） */
    String previewCsv(Long batchId, MultipartFile file);

    /** 写入导入暂存表，返回导入统计 */
    String commitCsv(Long batchId, MultipartFile file);
}

