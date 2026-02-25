package com.yiyundao.compensation.common.utils;

import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.service.EncryptionService;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeeListItemVO;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeeVO;
import org.springframework.stereotype.Component;

@Component
public class VOConverter {

    private final EncryptionService encryptionService;

    public VOConverter(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
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
        vo.setPosition(employee.getPosition());
        vo.setPlatformUserId(employee.getPlatformUserId());
        vo.setPlatformType(employee.getPlatformType());
        vo.setManagerId(employee.getManagerId());
        vo.setHireDate(employee.getHireDate());
        vo.setStatus(employee.getStatus());
        vo.setBankAccountMasked(encryptionService.maskBankAccount(employee.getBankAccount()));
        vo.setBankName(employee.getBankName());
        vo.setOffline(employee.getOffline());
        vo.setCreateTime(employee.getCreateTime());
        vo.setUpdateTime(employee.getUpdateTime());
        return vo;
    }

    public EmployeeListItemVO toEmployeeListItemVO(Employee employee) {
        if (employee == null) return null;
        return EmployeeListItemVO.from(employee);
    }
}
