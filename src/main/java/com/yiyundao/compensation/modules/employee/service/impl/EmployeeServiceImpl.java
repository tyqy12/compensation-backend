package com.yiyundao.compensation.modules.employee.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.infrastructure.dao.EmployeeMapper;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.service.EncryptionService;
import com.yiyundao.compensation.common.utils.SensitiveDataValidator;
import com.yiyundao.compensation.common.utils.SensitiveDataValidator.ValidationResult;
import com.yiyundao.compensation.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl extends ServiceImpl<EmployeeMapper, Employee> implements EmployeeService {

    private final EncryptionService encryptionService;
    private final SensitiveDataValidator sensitiveDataValidator;

    private void validateEmployeeData(Employee employee) {
        log.debug("验证员工数据: {}", employee.getName());

        if (StringUtils.hasText(employee.getName())) {
            ValidationResult nameResult = sensitiveDataValidator.validateName(employee.getName());
            if (!nameResult.isValid()) {
                throw new IllegalArgumentException("姓名验证失败: " + nameResult.getMessage());
            }
        }
        if (StringUtils.hasText(employee.getEncryptedIdCard())) {
            ValidationResult idCardResult = sensitiveDataValidator.validateIdCard(employee.getEncryptedIdCard());
            if (!idCardResult.isValid()) {
                throw new IllegalArgumentException("身份证号验证失败: " + idCardResult.getMessage());
            }
        }
        if (StringUtils.hasText(employee.getPhone())) {
            ValidationResult phoneResult = sensitiveDataValidator.validatePhone(employee.getPhone());
            if (!phoneResult.isValid()) {
                throw new IllegalArgumentException("手机号验证失败: " + phoneResult.getMessage());
            }
        }
        if (StringUtils.hasText(employee.getBankAccount())) {
            ValidationResult bankCardResult = sensitiveDataValidator.validateBankCard(employee.getBankAccount());
            if (!bankCardResult.isValid()) {
                throw new IllegalArgumentException("银行卡号验证失败: " + bankCardResult.getMessage());
            }
        }
        log.debug("员工数据验证通过");
    }

    @Override
    @Transactional
    public Employee createEmployee(Employee employee) {
        log.info("创建员工: {}", employee.getName());
        validateEmployeeData(employee);
        if (existsByEmployeeId(employee.getEmployeeId())) {
            throw new BusinessException("员工工号已存在: " + employee.getEmployeeId());
        }
        if (StringUtils.hasText(employee.getEncryptedIdCard())) {
            employee.setEncryptedIdCard(encryptionService.encryptIdCard(employee.getEncryptedIdCard()));
        }
        if (StringUtils.hasText(employee.getBankAccount())) {
            employee.setBankAccount(encryptionService.encrypt(employee.getBankAccount()));
        }
        if (!StringUtils.hasText(employee.getStatus())) {
            employee.setStatus("active");
        }
        if (employee.getOffline() == null) {
            employee.setOffline(false);
        }
        save(employee);
        log.info("员工创建成功: id={}, employeeId={}", employee.getId(), employee.getEmployeeId());
        return employee;
    }

    @Override
    @Transactional
    public Employee updateEmployee(Long id, Employee updateInfo) {
        log.info("更新员工信息: id={}", id);
        Employee existingEmployee = getById(id);
        if (existingEmployee == null) {
            throw new BusinessException(404, "员工不存在: " + id);
        }
        if (StringUtils.hasText(updateInfo.getName())) existingEmployee.setName(updateInfo.getName());
        if (StringUtils.hasText(updateInfo.getPhone())) existingEmployee.setPhone(updateInfo.getPhone());
        if (StringUtils.hasText(updateInfo.getEmail())) existingEmployee.setEmail(updateInfo.getEmail());
        if (StringUtils.hasText(updateInfo.getDepartment())) existingEmployee.setDepartment(updateInfo.getDepartment());
        if (StringUtils.hasText(updateInfo.getPosition())) existingEmployee.setPosition(updateInfo.getPosition());
        if (updateInfo.getHireDate() != null) existingEmployee.setHireDate(updateInfo.getHireDate());
        if (StringUtils.hasText(updateInfo.getEncryptedIdCard())) {
            existingEmployee.setEncryptedIdCard(encryptionService.encryptIdCard(updateInfo.getEncryptedIdCard()));
        }
        if (StringUtils.hasText(updateInfo.getBankAccount())) {
            existingEmployee.setBankAccount(encryptionService.encrypt(updateInfo.getBankAccount()));
        }
        if (StringUtils.hasText(updateInfo.getBankName())) existingEmployee.setBankName(updateInfo.getBankName());
        updateById(existingEmployee);
        log.info("员工信息更新成功: id={}", id);
        return existingEmployee;
    }

    @Override
    @Transactional
    public void bindPlatformUser(Long employeeId, String platformUserId, String platformType) {
        log.info("绑定平台用户: employeeId={}, platformType={}", employeeId, platformType);
        LambdaUpdateWrapper<Employee> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Employee::getId, employeeId)
                    .set(Employee::getPlatformUserId, platformUserId)
                    .set(Employee::getPlatformType, platformType);
        update(updateWrapper);
        log.info("平台用户绑定成功");
    }

    @Override
    @Transactional
    public void setOfflineManager(Long employeeId, Long managerId) {
        log.info("设置离线员工管理员: employeeId={}, managerId={}", employeeId, managerId);
        Employee manager = getById(managerId);
        if (manager == null || !StringUtils.hasText(manager.getPlatformUserId())) {
            throw new BusinessException("管理员必须绑定企业微信或钉钉平台");
        }
        LambdaUpdateWrapper<Employee> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Employee::getId, employeeId)
                    .set(Employee::getManagerId, managerId)
                    .set(Employee::getOffline, true);
        update(updateWrapper);
        log.info("离线员工管理员设置成功");
    }

    @Override
    public Page<Employee> pageEmployees(int pageNum, int pageSize, String keyword, String department,
                                       String status, Boolean isOffline,
                                       String platformType, Long managerId,
                                       String sortBy, String order) {
        log.info("分页查询员工: page={}, size={}, keyword={}", pageNum, pageSize, keyword);
        Page<Employee> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Employee> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(wrapper -> wrapper
                .like(Employee::getName, keyword)
                .or().like(Employee::getEmployeeId, keyword)
                .or().like(Employee::getPhone, keyword)
                .or().like(Employee::getEmail, keyword)
            );
        }
        if (StringUtils.hasText(department)) queryWrapper.eq(Employee::getDepartment, department);
        if (StringUtils.hasText(status)) queryWrapper.eq(Employee::getStatus, status);
        if (isOffline != null) queryWrapper.eq(Employee::getOffline, isOffline);
        if (StringUtils.hasText(platformType)) queryWrapper.eq(Employee::getPlatformType, platformType);
        if (managerId != null) queryWrapper.eq(Employee::getManagerId, managerId);

        boolean asc = "asc".equalsIgnoreCase(order);
        if ("name".equalsIgnoreCase(sortBy)) {
            queryWrapper.orderBy(true, asc, Employee::getName);
        } else if ("employeeId".equalsIgnoreCase(sortBy)) {
            queryWrapper.orderBy(true, asc, Employee::getEmployeeId);
        } else if ("hireDate".equalsIgnoreCase(sortBy)) {
            queryWrapper.orderBy(true, asc, Employee::getHireDate);
        } else if ("updateTime".equalsIgnoreCase(sortBy)) {
            queryWrapper.orderBy(true, asc, Employee::getUpdateTime);
        } else {
            queryWrapper.orderByDesc(Employee::getCreateTime);
        }
        return page(page, queryWrapper);
    }

    @Override
    public List<Employee> getOfflineEmployees(Long managerId) {
        log.info("查询离线员工列表: managerId={}", managerId);
        LambdaQueryWrapper<Employee> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Employee::getOffline, true)
                   .eq(Employee::getStatus, "active");
        if (managerId != null) queryWrapper.eq(Employee::getManagerId, managerId);
        return list(queryWrapper);
    }

    @Override
    public Employee getByPlatformUserId(String platformUserId, String platformType) {
        LambdaQueryWrapper<Employee> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Employee::getPlatformUserId, platformUserId)
                   .eq(Employee::getPlatformType, platformType)
                   .eq(Employee::getStatus, "active");
        return getOne(queryWrapper);
    }

    @Override
    public Employee getByEmployeeId(String employeeId) {
        LambdaQueryWrapper<Employee> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Employee::getEmployeeId, employeeId)
                   .eq(Employee::getStatus, "active");
        return getOne(queryWrapper);
    }

    @Override
    public boolean existsByEmployeeId(String employeeId) {
        LambdaQueryWrapper<Employee> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Employee::getEmployeeId, employeeId);
        return count(queryWrapper) > 0;
    }

    @Override
    @Transactional
    public void updateStatus(Long employeeId, String status) {
        log.info("更新员工状态: employeeId={}, status={}", employeeId, status);
        LambdaUpdateWrapper<Employee> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Employee::getId, employeeId)
                    .set(Employee::getStatus, status);
        update(updateWrapper);
        log.info("员工状态更新成功");
    }

    @Override
    @Transactional
    public void batchImport(List<Employee> employees) {
        log.info("批量导入员工: count={}", employees.size());
        for (Employee employee : employees) {
            if (!StringUtils.hasText(employee.getEmployeeId()) || !StringUtils.hasText(employee.getName())) {
                throw new BusinessException("员工工号和姓名不能为空");
            }
            if (existsByEmployeeId(employee.getEmployeeId())) {
                log.warn("跳过重复员工工号: {}", employee.getEmployeeId());
                continue;
            }
            if (StringUtils.hasText(employee.getEncryptedIdCard())) {
                employee.setEncryptedIdCard(encryptionService.encryptIdCard(employee.getEncryptedIdCard()));
            }
            if (StringUtils.hasText(employee.getBankAccount())) {
                employee.setBankAccount(encryptionService.encrypt(employee.getBankAccount()));
            }
            if (!StringUtils.hasText(employee.getStatus())) employee.setStatus("active");
            if (employee.getOffline() == null) employee.setOffline(false);
        }
        saveBatch(employees);
        log.info("批量导入完成: 成功导入{}个员工", employees.size());
    }

    @Override
    public String getDecryptedIdCard(Long employeeId) {
        Employee employee = getById(employeeId);
        if (employee != null && StringUtils.hasText(employee.getEncryptedIdCard())) {
            return encryptionService.decryptIdCard(employee.getEncryptedIdCard());
        }
        return null;
    }

    @Override
    public String getDecryptedBankAccount(Long employeeId) {
        Employee employee = getById(employeeId);
        if (employee != null && StringUtils.hasText(employee.getBankAccount())) {
            return encryptionService.decrypt(employee.getBankAccount());
        }
        return null;
    }
}
