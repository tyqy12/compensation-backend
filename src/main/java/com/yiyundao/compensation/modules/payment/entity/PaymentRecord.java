package com.yiyundao.compensation.modules.payment.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.enums.PaymentType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payment_record")
public class PaymentRecord extends BaseEntity {

    @TableField("batch_no")
    private String batchNo;

    @TableField("employee_id")
    private Long employeeId;

    @TableField("payment_type")
    private PaymentType paymentType;

    private BigDecimal amount;
    private String currency;

    @TableField("payment_method")
    private String paymentMethod;

    @TableField("recipient_account")
    private String recipientAccount;

    @TableField("recipient_name")
    private String recipientName;

    @TableField("payment_desc")
    private String paymentDesc;

    private PaymentStatus status;

    @TableField("alipay_order_no")
    private String alipayOrderNo;

    @TableField("alipay_trade_no")
    private String alipayTradeNo;

    @TableField("error_code")
    private String errorCode;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("payment_time")
    private LocalDateTime paymentTime;

    @TableField("notification_time")
    private LocalDateTime notificationTime;
}

