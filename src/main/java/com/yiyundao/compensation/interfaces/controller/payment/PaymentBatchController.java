package com.yiyundao.compensation.interfaces.controller.payment;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.interfaces.vo.payment.PaymentBatchVO;
import com.yiyundao.compensation.interfaces.vo.payment.PaymentRecordItemVO;
import com.yiyundao.compensation.modules.payment.service.PaymentBatchService;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.service.AlipayService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/payment/batch")
@RequiredArgsConstructor
public class PaymentBatchController {

    private final PaymentBatchService paymentBatchService;
    private final PaymentRecordService paymentRecordService;
    private final AlipayService alipayService;

    // 分页查询支付批次
    @GetMapping
    public ApiResponse<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false, defaultValue = "submitTime") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String order
    ) {
        Page<PaymentBatch> p = paymentBatchService.pagePaymentBatches(page, size, keyword, status, paymentType, startDate, endDate, sortBy, order);
        Map<String, Object> result = new HashMap<>();
        result.put("records", p.getRecords().stream().map(PaymentBatchVO::from).toList());
        result.put("total", p.getTotal());
        result.put("current", p.getCurrent());
        result.put("size", p.getSize());
        return ApiResponse.success(result);
    }

    // 获取批次详情
    @GetMapping("/{batchNo}")
    public ApiResponse<PaymentBatchVO> detail(@PathVariable String batchNo) {
        PaymentBatch batch = paymentBatchService.getByBatchNo(batchNo);
        return ApiResponse.success(batch == null ? null : PaymentBatchVO.from(batch));
    }

    // 获取批次记录列表，可按状态过滤
    @GetMapping("/{batchNo}/records")
    public ApiResponse<List<PaymentRecordItemVO>> records(@PathVariable String batchNo,
                                                          @RequestParam(required = false) String status) {
        PaymentStatus st = null;
        if (status != null) {
            st = PaymentStatus.fromCode(status);
        }
        List<PaymentRecord> list = paymentRecordService.getByBatchNo(batchNo, st);
        return ApiResponse.success(list.stream().map(PaymentRecordItemVO::from).collect(Collectors.toList()));
    }

    // 启动批量转账（异步）
    @PostMapping("/{batchNo}/start")
    public ApiResponse<String> start(@PathVariable String batchNo) {
        alipayService.batchTransfer(batchNo);
        return ApiResponse.success("批量转账已启动");
    }
}
