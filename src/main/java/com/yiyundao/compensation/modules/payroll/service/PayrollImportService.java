package com.yiyundao.compensation.modules.payroll.service;

import com.yiyundao.compensation.interfaces.dto.payroll.PayrollManualImportItemRequest;
import com.yiyundao.compensation.interfaces.vo.payroll.PayrollImportItemVO;
import com.yiyundao.compensation.interfaces.vo.payroll.PayrollImportSalaryItemVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PayrollImportService {
    /** 解析并校验CSV，返回预览统计（JSON字符串，后续可换DTO） */
    String previewCsv(Long batchId, MultipartFile file);

    /** 写入导入暂存表，返回导入统计 */
    String commitCsv(Long batchId, MultipartFile file);

    List<PayrollImportItemVO> listItems(Long batchId);

    PayrollImportItemVO addManualItem(Long batchId, PayrollManualImportItemRequest request);

    PayrollImportItemVO updateItem(Long batchId, Long itemId, PayrollManualImportItemRequest request);

    boolean deleteItem(Long batchId, Long itemId);

    List<PayrollImportSalaryItemVO> listEnabledSalaryItems();
}

