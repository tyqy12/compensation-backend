package com.yiyundao.compensation.modules.employee.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.response.PageResponse;
import com.yiyundao.compensation.common.utils.VOConverter;
import com.yiyundao.compensation.enums.EmployeeStatus;
import com.yiyundao.compensation.enums.EmploymentType;
import com.yiyundao.compensation.enums.WorkflowType;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeeListItemVO;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeeVO;
import com.yiyundao.compensation.modules.approval.service.ApprovalEngine;
import com.yiyundao.compensation.modules.employee.dto.BindPlatformRequest;
import com.yiyundao.compensation.modules.employee.dto.BindPlatformResult;
import com.yiyundao.compensation.modules.employee.dto.BindPlatformResult.ConflictInfo;
import com.yiyundao.compensation.modules.employee.dto.BindResult;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.infrastructure.dao.EmployeeMapper;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.common.utils.ValidationUtils;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.modules.user.service.UserBindingService;
import com.yiyundao.compensation.service.EncryptionService;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl extends ServiceImpl<EmployeeMapper, Employee> implements EmployeeService {

    private final EncryptionService encryptionService;
    private final ObjectProvider<UserBindingService> userBindingServiceProvider;
    private final ObjectProvider<ApprovalEngine> approvalEngineProvider;
    private final SysUserService sysUserService;
    private final VOConverter voConverter;
    private final ObjectMapper objectMapper;

    private void validateEmployeeData(Employee employee) {
        log.debug("验证员工数据: {}", employee.getName());
        if (StringUtils.hasText(employee.getPhone()) && !ValidationUtils.isValidPhone(employee.getPhone())) {
            throw new BusinessException(ErrorCode.PARAM_FORMAT_ERROR, "手机号格式不正确");
        }
        if (StringUtils.hasText(employee.getEmail()) && !ValidationUtils.isValidEmail(employee.getEmail())) {
            throw new BusinessException(ErrorCode.PARAM_FORMAT_ERROR, "邮箱格式不正确");
        }
        log.debug("员工数据验证通过");
    }

    @Override
    @Transactional
    public EmployeeVO createEmployee(Employee employee) {
        log.info("创建员工: {}", employee.getName());
        validateEmployeeData(employee);
        if (existsByEmployeeId(employee.getEmployeeId())) {
            throw new BusinessException(ErrorCode.RESOURCE_EXISTS, "员工工号已存在: " + employee.getEmployeeId());
        }
        encryptSensitiveData(employee);
        setDefaultValues(employee);
        save(employee);
        log.info("员工创建成功: id={}, employeeId={}", employee.getId(), employee.getEmployeeId());
        return voConverter.toEmployeeVO(employee);
    }

    @Override
    @Transactional
    public EmployeeVO createEmployeeWithUser(Employee employee, String username) {
        EmployeeVO vo = createEmployee(employee);
        try {
            userBindingServiceProvider.getObject().ensureUserForEmployee(employee, username);
        } catch (Exception ex) {
            log.warn("自动创建用户失败: {}", ex.getMessage());
        }
        return vo;
    }

    @Override
    @Transactional
    public EmployeeVO updateEmployee(Long id, Employee updateInfo) {
        log.info("更新员工信息: id={}", id);
        Employee existingEmployee = getById(id);
        if (existingEmployee == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "员工不存在: " + id);
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
        return voConverter.toEmployeeVO(existingEmployee);
    }

    @Override
    public EmployeeVO getEmployeeVO(Long id) {
        Employee employee = getById(id);
        return voConverter.toEmployeeVO(employee);
    }

    @Override
    public PageResponse<EmployeeListItemVO> pageEmployees(int pageNum, int pageSize, String keyword,
                                                          String department, String status,
                                                          Boolean isOffline, String platformType,
                                                          Long managerId, String sortBy, String order) {
        log.info("分页查询员工: page={}, size={}, keyword={}", pageNum, pageSize, keyword);
        Page<Employee> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Employee> queryWrapper = buildQueryWrapper(keyword, department, status, isOffline, platformType, managerId, sortBy, order);
        Page<Employee> result = page(page, queryWrapper);
        List<EmployeeListItemVO> voList = result.getRecords().stream()
                .map(voConverter::toEmployeeListItemVO)
                .toList();
        return PageResponse.of(voList, result.getCurrent(), result.getSize(), result.getTotal());
    }

    @Override
    public List<EmployeeVO> getOfflineEmployees(Long managerId) {
        log.info("查询离线员工列表: managerId={}", managerId);
        LambdaQueryWrapper<Employee> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Employee::getOffline, true)
                   .eq(Employee::getStatus, EmployeeStatus.ACTIVE.getCode());
        if (managerId != null) queryWrapper.eq(Employee::getManagerId, managerId);
        return list(queryWrapper).stream()
                .map(voConverter::toEmployeeVO)
                .toList();
    }

    @Override
    @Transactional
    public BindPlatformResult bindPlatform(Long employeeId, BindPlatformRequest request) {
        log.info("绑定平台用户: employeeId={}, platformType={}, platformUserId={}",
                employeeId, request.getPlatformType(), request.getPlatformUserId());

        // 1. 检查员工是否存在
        Employee employee = getById(employeeId);
        if (employee == null) {
            log.warn("员工不存在: {}", employeeId);
            return BindPlatformResult.failed(BindResult.EMPLOYEE_NOT_FOUND, "员工不存在");
        }

        String platformType = normalizePlatform(request.getPlatformType());
        String platformUserId = request.getPlatformUserId().trim();

        // 2. 检查是否已是同一账号（无需重复绑定）
        if (platformType.equals(employee.getPlatformType())
                && platformUserId.equals(employee.getPlatformUserId())) {
            log.info("已是同一平台账号，无需重复绑定: employeeId={}", employeeId);
            return BindPlatformResult.alreadyBound(employeeId, employee.getEmployeeId(), employee.getName(),
                    platformType, platformUserId);
        }

        // 3. 检查目标平台账号是否已被其他员工占用
        Employee occupiedEmployee = getByPlatformUserId(platformUserId, platformType);
        if (occupiedEmployee != null && !occupiedEmployee.getId().equals(employeeId)) {
            // 冲突：平台账号已被其他员工占用
            log.warn("平台账号冲突: platformUserId={}, occupiedBy={}",
                    platformUserId, occupiedEmployee.getId());

            // 构建冲突信息
            ConflictInfo conflictInfo = ConflictInfo.builder()
                    .conflictType("PLATFORM_OCCUPIED")
                    .occupiedEmployeeId(occupiedEmployee.getId())
                    .occupiedEmployeeName(occupiedEmployee.getName())
                    .occupiedEmployeeNo(occupiedEmployee.getEmployeeId())
                    .occupiedPlatformUserId(platformUserId)
                    .detail(String.format("平台账号已被员工【%s(%s)】占用，如需强制绑定请等待审批",
                            occupiedEmployee.getName(), occupiedEmployee.getEmployeeId()))
                    .build();

            // 发起审批流程
            Long workflowId = startApprovalWorkflow(employee, platformType, platformUserId, conflictInfo);

            return BindPlatformResult.pendingApproval(employeeId, employee.getEmployeeId(), employee.getName(),
                    platformType, platformUserId, workflowId, "PLATFORM_LINK", conflictInfo);
        }

        // 4. 执行绑定（双向同步）
        performBinding(employee, platformType, platformUserId);

        // 5. 获取关联的用户ID
        Long userId = null;
        if (employee.getId() != null) {
            SysUser user = sysUserService.getOne(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmployeeId, employee.getId()));
            if (user != null) {
                userId = user.getId();
            }
        }

        log.info("平台用户绑定成功: employeeId={}, userId={}", employeeId, userId);
        return BindPlatformResult.success(employeeId, employee.getEmployeeId(), employee.getName(),
                platformType, platformUserId, userId);
    }

    @Override
    @Transactional
    public void unbindPlatform(Long employeeId, String reason) {
        log.info("解绑平台用户: employeeId={}, reason={}", employeeId, reason);

        Employee employee = getById(employeeId);
        if (employee == null) {
            log.warn("员工不存在: {}", employeeId);
            return;
        }

        // 记录解绑前的状态（用于审计）
        String oldPlatformType = employee.getPlatformType();
        String oldPlatformUserId = employee.getPlatformUserId();
        Long userId = null;

        if (employee.getId() != null) {
            SysUser user = sysUserService.getOne(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmployeeId, employee.getId()));
            if (user != null) {
                userId = user.getId();
            }
        }

        // 清空员工平台信息
        employee.setPlatformType(null);
        employee.setPlatformUserId(null);
        updateById(employee);

        // 同步清空用户平台信息
        if (userId != null) {
            SysUser user = sysUserService.getById(userId);
            if (user != null && oldPlatformType.equals(user.getPlatformType())
                    && oldPlatformUserId.equals(user.getPlatformUserId())) {
                user.setPlatformType(null);
                user.setPlatformUserId(null);
                sysUserService.updateById(user);
            }
        }

        log.info("平台用户解绑成功: employeeId={}, userId={}, reason={}", employeeId, userId, reason);
    }

    @Override
    @Transactional
    public BindPlatformResult executeApprovedBinding(Long workflowId, Long employeeId,
                                                      String platformType, String platformUserId) {
        log.info("执行审批通过后的绑定: workflowId={}, employeeId={}, platformType={}, platformUserId={}",
                workflowId, employeeId, platformType, platformUserId);

        Employee employee = getById(employeeId);
        if (employee == null) {
            log.warn("员工不存在: {}", employeeId);
            return BindPlatformResult.failed(BindResult.EMPLOYEE_NOT_FOUND, "员工不存在");
        }

        // 重新检查是否仍可绑定（可能情况已变化）
        Employee occupiedEmployee = getByPlatformUserId(platformUserId, platformType);
        if (occupiedEmployee != null && !occupiedEmployee.getId().equals(employeeId)) {
            log.warn("审批通过但平台账号已被其他员工占用: employeeId={}, occupiedBy={}",
                    employeeId, occupiedEmployee.getId());
            return BindPlatformResult.failed(BindResult.PLATFORM_CONFLICT, "审批通过但平台账号已被其他员工占用");
        }

        // 执行绑定
        performBinding(employee, platformType, platformUserId);

        Long userId = null;
        if (employee.getId() != null) {
            SysUser user = sysUserService.getOne(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmployeeId, employee.getId()));
            if (user != null) {
                userId = user.getId();
            }
        }

        log.info("审批通过后的绑定执行成功: employeeId={}, userId={}", employeeId, userId);
        return BindPlatformResult.success(employeeId, employee.getEmployeeId(), employee.getName(),
                platformType, platformUserId, userId);
    }

    /**
     * 执行实际的绑定操作（双向同步）
     */
    private void performBinding(Employee employee, String platformType, String platformUserId) {
        // 更新员工平台信息
        employee.setPlatformType(platformType);
        employee.setPlatformUserId(platformUserId);
        updateById(employee);

        // 同步更新关联用户的平台信息
        if (employee.getId() != null) {
            SysUser user = sysUserService.getOne(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmployeeId, employee.getId()));
            if (user != null) {
                user.setPlatformType(platformType);
                user.setPlatformUserId(platformUserId);
                sysUserService.updateById(user);
                log.debug("同步更新用户平台信息: userId={}", user.getId());
            }
        }
    }

    /**
     * 发起审批流程
     */
    private Long startApprovalWorkflow(Employee employee, String platformType, String platformUserId,
                                        ConflictInfo conflictInfo) {
        Long operatorId = getCurrentUserId();

        Map<String, Object> data = new HashMap<>();
        data.put("employeeId", employee.getId());
        data.put("employeeName", employee.getName());
        data.put("employeeNo", employee.getEmployeeId());
        data.put("platformType", platformType);
        data.put("platformUserId", platformUserId);
        data.put("conflictInfo", toJsonSafe(conflictInfo));
        data.put("action", "BIND_PLATFORM");
        data.put("reason", "平台账号冲突，申请强制绑定");

        ApprovalEngine approvalEngine = approvalEngineProvider.getObject();
        Long workflowId = approvalEngine.startWorkflow(
                WorkflowType.OFFLINE,
                "EMPLOYEE-" + employee.getId(),
                "PLATFORM_BIND",
                operatorId,
                data
        );

        log.info("发起平台绑定审批流程: workflowId={}, employeeId={}", workflowId, employee.getId());
        return workflowId;
    }

    /**
     * 标准化平台类型
     */
    private String normalizePlatform(String platform) {
        if (platform == null) return null;
        String p = platform.trim().toLowerCase();
        switch (p) {
            case "wechat":
            case "wecom":
            case "qywx":
            case "wx":
                return "wechat";
            case "dingtalk":
            case "dingding":
            case "dd":
                return "dingtalk";
            case "feishu":
            case "lark":
                return "feishu";
            default:
                return p;
        }
    }

    /**
     * 获取当前用户ID
     */
    private Long getCurrentUserId() {
        try {
            String username = SecurityContextHolder.getContext() != null
                    && SecurityContextHolder.getContext().getAuthentication() != null
                    ? SecurityContextHolder.getContext().getAuthentication().getName() : null;
            if (username != null) {
                SysUser user = sysUserService.findByUsername(username);
                if (user != null) return user.getId();
            }
        } catch (Exception ignored) {}
        return 1L; // 默认管理员
    }

    /**
     * 安全转JSON
     */
    private String toJsonSafe(Object o) {
        try { return objectMapper.writeValueAsString(o); } catch (Exception e) { return null; }
    }

    @Override
    @Transactional
    public void updateStatus(Long employeeId, EmployeeStatus status) {
        log.info("更新员工状态: employeeId={}, status={}", employeeId, status);
        LambdaUpdateWrapper<Employee> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Employee::getId, employeeId)
                    .set(Employee::getStatus, status != null ? status.getCode() : null);
        update(updateWrapper);
        log.info("员工状态更新成功");
    }

    @Override
    @Transactional
    public void batchImport(List<Employee> employees) {
        log.info("批量导入员工: count={}", employees.size());
        List<Employee> toSave = employees.stream().filter(e -> {
            if (!StringUtils.hasText(e.getEmployeeId()) || !StringUtils.hasText(e.getName())) {
                log.warn("跳过无效数据: 员工工号或姓名为空");
                return false;
            }
            if (existsByEmployeeId(e.getEmployeeId())) {
                log.warn("跳过重复员工工号: {}", e.getEmployeeId());
                return false;
            }
            return true;
        }).peek(this::encryptSensitiveData).peek(this::setDefaultValues).toList();
        saveBatch(toSave);
        log.info("批量导入完成: 成功导入{}个员工", toSave.size());
    }

    @Override
    public String getDecryptedIdCard(Long employeeId) {
        Employee employee = getById(employeeId);
        if (employee != null && StringUtils.hasText(employee.getEncryptedIdCard())) {
            return encryptionService.decryptIdCard(employee.getEncryptedIdCard());
        }
        return ""; // 返回空字符串而非 null，避免前端处理困难
    }

    @Override
    public String getDecryptedBankAccount(Long employeeId) {
        Employee employee = getById(employeeId);
        if (employee != null && StringUtils.hasText(employee.getBankAccount())) {
            return encryptionService.decrypt(employee.getBankAccount());
        }
        return ""; // 返回空字符串而非 null，避免前端处理困难
    }

    @Override
    public Employee getByPlatformUserId(String platformUserId, String platformType) {
        LambdaQueryWrapper<Employee> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Employee::getPlatformUserId, platformUserId)
                   .eq(Employee::getPlatformType, platformType)
                   .eq(Employee::getStatus, EmployeeStatus.ACTIVE.getCode());
        return getOne(queryWrapper);
    }

    @Override
    public Employee getByEmployeeId(String employeeId) {
        LambdaQueryWrapper<Employee> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Employee::getEmployeeId, employeeId);
        return getOne(queryWrapper);
    }

    @Override
    public boolean existsByEmployeeId(String employeeId) {
        LambdaQueryWrapper<Employee> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Employee::getEmployeeId, employeeId);
        return count(queryWrapper) > 0;
    }

    @Override
    public void setOfflineManager(Long employeeId, Long managerId) {
        Employee employee = getById(employeeId);
        if (employee == null) {
            throw new IllegalArgumentException("Employee not found: " + employeeId);
        }
        employee.setManagerId(managerId);
        updateById(employee);
    }

    private void encryptSensitiveData(Employee employee) {
        if (StringUtils.hasText(employee.getEncryptedIdCard())) {
            employee.setEncryptedIdCard(encryptionService.encryptIdCard(employee.getEncryptedIdCard()));
        }
        if (StringUtils.hasText(employee.getBankAccount())) {
            employee.setBankAccount(encryptionService.encrypt(employee.getBankAccount()));
        }
    }

    private void setDefaultValues(Employee employee) {
        if (!StringUtils.hasText(employee.getStatus())) {
            employee.setStatus(EmployeeStatus.ACTIVE.getCode());
        }
        if (!StringUtils.hasText(employee.getEmploymentType())) {
            employee.setEmploymentType(EmploymentType.FULL_TIME.getCode());
        }
        if (employee.getOffline() == null) {
            employee.setOffline(false);
        }
    }

    private LambdaQueryWrapper<Employee> buildQueryWrapper(String keyword, String department, String status,
                                                           Boolean isOffline, String platformType, Long managerId,
                                                           String sortBy, String order) {
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
        switch (sortBy.toLowerCase()) {
            case "name" -> queryWrapper.orderBy(true, asc, Employee::getName);
            case "employeeid" -> queryWrapper.orderBy(true, asc, Employee::getEmployeeId);
            case "hiredate" -> queryWrapper.orderBy(true, asc, Employee::getHireDate);
            case "updatetime" -> queryWrapper.orderBy(true, asc, Employee::getUpdateTime);
            default -> queryWrapper.orderByDesc(Employee::getCreateTime);
        }
        return queryWrapper;
    }
}
