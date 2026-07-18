package com.yiyundao.compensation.modules.payroll.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payroll_policy_package")
public class PayrollPolicyPackage extends BaseEntity {
    private String code;
    private String name;
    @TableField("policy_type")
    private String policyType;
    @TableField("region_code")
    private String regionCode;
    @TableField("collection_entity_code")
    private String collectionEntityCode;
    @TableField("person_category")
    private String personCategory;
    @TableField("industry_risk_level")
    private String industryRiskLevel;
    @TableField("effective_from")
    private LocalDate effectiveFrom;
    @TableField("effective_to")
    private LocalDate effectiveTo;
    @TableField("source_document")
    private String sourceDocument;
    @TableField("source_url")
    private String sourceUrl;
    @TableField("payload_json")
    private String payloadJson;
    private String status;
    @TableField("version_no")
    private Long versionNo;
    private String checksum;
    @TableField("reviewed_by")
    private Long reviewedBy;
    @TableField("reviewed_at")
    private LocalDateTime reviewedAt;
    @TableField("published_by")
    private Long publishedBy;
    @TableField("published_at")
    private LocalDateTime publishedAt;
}
