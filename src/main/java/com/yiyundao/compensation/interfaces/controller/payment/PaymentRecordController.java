package com.yiyundao.compensation.interfaces.controller.payment;

import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.interfaces.vo.payment.PaymentRecordItemVO;
import com.yiyundao.compensation.modules.payment.provider.SettlementResult;
import com.yiyundao.compensation.modules.payment.provider.SettlementStatus;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payment.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentRecordController {

    private final PaymentRecordService paymentRecordService;
    private final SettlementService settlementService;

    // 单条记录查询
    @GetMapping("/record/{id}")
    public ApiResponse<PaymentRecordItemVO> detail(@PathVariable Long id) {
        PaymentRecord r = paymentRecordService.getById(id);
        return ApiResponse.success(r == null ? null : PaymentRecordItemVO.from(r));
    }

    // 单条重试
    @PostMapping("/record/{id}/retry")
    public ApiResponse<String> retry(@PathVariable Long id) {
        try {
            SettlementResult result = settlementService.singleTransfer(id);
            if (!result.isSuccess()) {
                throw new BusinessException("重试失败: " + result.getErrorMsg());
            }
            return ApiResponse.success("重试成功", result.getProviderTradeNo());
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
