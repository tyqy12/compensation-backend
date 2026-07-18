package com.yiyundao.compensation.common.utils;

import com.yiyundao.compensation.enums.SettlementAccountType;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeDepartmentService;
import com.yiyundao.compensation.modules.user.entity.ExternalIdentity;
import com.yiyundao.compensation.modules.user.service.ExternalIdentityService;
import com.yiyundao.compensation.service.EncryptionService;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeeListItemVO;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeeVO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

@Component
public class VOConverter {

    private final EncryptionService encryptionService;
    private final ExternalIdentityService externalIdentityService;
    private final EmployeeDepartmentService employeeDepartmentService;

    public VOConverter(EncryptionService encryptionService,
                       ExternalIdentityService externalIdentityService,
                       EmployeeDepartmentService employeeDepartmentService) {
        this.encryptionService = encryptionService;
        this.externalIdentityService = externalIdentityService;
        this.employeeDepartmentService = employeeDepartmentService;
    }

    public EmployeeVO toEmployeeVO(Employee employee) {
        if (employee == null) return null;
        EmployeeVO vo = new EmployeeVO();
        vo.setId(employee.getId());
        vo.setEmployeeId(employee.getEmployeeId());
        vo.setName(employee.getName());
        vo.setPhoneMasked(encryptionService.maskPhone(employee.getPhone()));
        vo.setEmail(employee.getEmail());
        vo.setDepartment(employee.getDepartment());
        vo.setDepartments(resolveDepartments(employee));
        vo.setPosition(employee.getPosition());
        ExternalIdentity identity = externalIdentityService.findPrimaryByEmployeeId(employee.getId());
        vo.setSubjectId(identity != null ? identity.getSubjectId() : null);
        vo.setProvider(identity != null ? identity.getProvider() : null);
        vo.setEmploymentType(employee.getEmploymentType());
        vo.setManagerId(employee.getManagerId());
        vo.setHireDate(employee.getHireDate());
        vo.setStatus(employee.getStatus());
        String settlementType = resolveSettlementType(employee);
        String settlementMasked = maskEncryptedSettlementAccount(employee.getSettlementAccount(), settlementType);
        String bankMasked = maskEncryptedBankAccount(employee.getBankAccount());
        if (!StringUtils.hasText(settlementMasked) && "bank_card".equals(settlementType)) {
            settlementMasked = bankMasked;
        }
        if (!StringUtils.hasText(bankMasked) && "bank_card".equals(settlementType)) {
            bankMasked = settlementMasked;
        }
        vo.setSettlementAccountType(settlementType);
        vo.setSettlementAccountTypeName(resolveSettlementTypeName(settlementType));
        vo.setSettlementAccountMasked(settlementMasked);
        vo.setSettlementAccountName(employee.getSettlementAccountName());
        vo.setBankAccountMasked(bankMasked);
        vo.setBankName(employee.getBankName());
        vo.setBankBranchName(employee.getBankBranchName());
        vo.setOffline(employee.getOffline());
        vo.setCreateTime(employee.getCreateTime());
        vo.setUpdateTime(employee.getUpdateTime());
        return vo;
    }

    public EmployeeListItemVO toEmployeeListItemVO(Employee employee) {
        return toEmployeeListItemVO(employee, null);
    }

    public EmployeeListItemVO toEmployeeListItemVO(Employee employee, List<String> relatedDepartments) {
        if (employee == null) return null;
        EmployeeListItemVO vo = EmployeeListItemVO.from(employee);
        vo.setDepartments(resolveDepartments(employee, relatedDepartments));
        return vo;
    }

    private List<String> resolveDepartments(Employee employee) {
        return resolveDepartments(employee, null);
    }

    private List<String> resolveDepartments(Employee employee, List<String> relatedDepartments) {
        if (employee == null) {
            return List.of();
        }
        List<String> related = relatedDepartments != null ? relatedDepartments : employee.getId() == null
                ? List.of()
                : employeeDepartmentService.findDepartmentNames(employee.getId());
        if (related != null && !related.isEmpty()) {
            return related;
        }
        if (!StringUtils.hasText(employee.getDepartment())) {
            return List.of();
        }
        return Arrays.stream(employee.getDepartment().split("[,/，、]"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String maskEncryptedBankAccount(String encryptedBankAccount) {
        if (!StringUtils.hasText(encryptedBankAccount)) {
            return null;
        }
        try {
            return encryptionService.maskBankAccount(encryptionService.decrypt(encryptedBankAccount));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String maskEncryptedSettlementAccount(String encryptedAccount, String settlementType) {
        if (!StringUtils.hasText(encryptedAccount)) {
            return null;
        }
        try {
            String plain = encryptionService.decrypt(encryptedAccount);
            if (!StringUtils.hasText(plain)) {
                return null;
            }
            String type = normalizeType(settlementType);
            if ("bank_card".equals(type)) {
                return encryptionService.maskBankAccount(plain);
            }
            if ("alipay".equals(type)) {
                return maskAlipayAccount(plain);
            }
            if ("wechat".equals(type)) {
                return maskGenericAccount(plain, 3, 3);
            }
            return maskGenericAccount(plain, 2, 2);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String maskAlipayAccount(String account) {
        int at = account.indexOf('@');
        if (at > 1) {
            String prefix = account.substring(0, at);
            String suffix = account.substring(at);
            if (prefix.length() <= 2) {
                return prefix.charAt(0) + "***" + suffix;
            }
            return prefix.substring(0, 2) + "***" + suffix;
        }
        return maskGenericAccount(account, 3, 2);
    }

    private String maskGenericAccount(String account, int prefix, int suffix) {
        if (!StringUtils.hasText(account)) {
            return null;
        }
        String value = account.trim();
        if (value.length() <= prefix + suffix) {
            return "****";
        }
        return value.substring(0, prefix) + "****" + value.substring(value.length() - suffix);
    }

    private String resolveSettlementType(Employee employee) {
        if (employee == null) {
            return null;
        }
        if (StringUtils.hasText(employee.getSettlementAccountType())) {
            return normalizeType(employee.getSettlementAccountType());
        }
        if (StringUtils.hasText(employee.getSettlementAccount()) || StringUtils.hasText(employee.getBankAccount())) {
            return SettlementAccountType.BANK_CARD.getCode();
        }
        return null;
    }

    private String resolveSettlementTypeName(String type) {
        SettlementAccountType settlementAccountType = SettlementAccountType.fromCode(type);
        return settlementAccountType != null ? settlementAccountType.getName() : type;
    }

    private String normalizeType(String type) {
        return type == null ? null : type.trim().toLowerCase();
    }
}
