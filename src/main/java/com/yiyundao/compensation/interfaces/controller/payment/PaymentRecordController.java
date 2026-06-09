package com.yiyundao.compensation.interfaces.controller.payment;

import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.infrastructure.dao.EmployeeMapper;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.interfaces.vo.payment.PaymentRecordItemVO;
import com.yiyundao.compensation.modules.payment.provider.SettlementResult;
import com.yiyundao.compensation.modules.payment.provider.SettlementStatus;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payment.service.SettlementService;
import com.yiyundao.compensation.security.SecurityAnnotations;
import com.yiyundao.compensation.service.EncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payment")
@SecurityAnnotations.IsFinanceOrAdmin
@RequiredArgsConstructor
public class PaymentRecordController {

    private final PaymentRecordService paymentRecordService;
    private final SettlementService settlementService;
    private final EmployeeMapper employeeMapper;
    private final EncryptionService encryptionService;

    // 单条记录查询
    @GetMapping("/record/{id}")
    public ApiResponse<PaymentRecordItemVO> detail(@PathVariable Long id) {
        PaymentRecord r = paymentRecordService.getById(id);
        if (r == null) {
            return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "支付记录不存在");
        }
        Employee employee = r.getEmployeeId() == null ? null : employeeMapper.selectById(r.getEmployeeId());
        return ApiResponse.success(PaymentRecordItemVO.from(r, employee, encryptionService));
    }

    // 单条重试
    @PostMapping("/record/{id}/retry")
    public ApiResponse<String> retry(@PathVariable Long id) {
        try {
            SettlementResult result = settlementService.retryFailedRecord(id);
            if (!result.isSuccess()) {
                throw new BusinessException("重试失败: " + result.getErrorMsg());
            }
            return ApiResponse.success("重试成功", result.getProviderTradeNo());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("重试失败: " + e.getMessage());
        }
    }

    // 查询转账状态
    @GetMapping("/transfer-status")
    public ApiResponse<String> transferStatus(@RequestParam String outBizNo,
                                              @RequestParam(defaultValue = "alipay") String providerCode) {
        try {
            SettlementStatus status = settlementService.queryStatus(providerCode, outBizNo);
            return ApiResponse.success(status.name().toLowerCase());
        } catch (Exception e) {
            throw new BusinessException("查询失败: " + e.getMessage());
        }
    }
}
