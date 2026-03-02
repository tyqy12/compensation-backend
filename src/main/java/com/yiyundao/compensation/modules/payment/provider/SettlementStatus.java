package com.yiyundao.compensation.modules.payment.provider;

public enum SettlementStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED,
    CANCELLED,
    AUDITING,
    SIGNING,
    TAXING,
    WITHDRAWING
}

