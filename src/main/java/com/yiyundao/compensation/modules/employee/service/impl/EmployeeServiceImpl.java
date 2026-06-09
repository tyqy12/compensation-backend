package com.yiyundao.compensation.modules.employee.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.response.PageResponse;
import com.yiyundao.compensation.common.utils.VOConverter;
import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.enums.EmployeeStatus;
import com.yiyundao.compensation.enums.EmploymentType;
import com.yiyundao.compensation.enums.SettlementAccountType;
import com.yiyundao.compensation.enums.UserStatus;
import com.yiyundao.compensation.enums.WorkflowType;
import com.yiyundao.compensation.infrastructure.dao.ApprovalWorkflowMapper;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeeListItemVO;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeeApprovalRecordVO;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeePayslipRecordVO;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeeVO;
import com.yiyundao.compensation.interfaces.vo.payment.PaymentRecordItemVO;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.service.ApprovalEngine;
import com.yiyundao.compensation.modules.employee.dto.BindPlatformRequest;
import com.yiyundao.compensation.modules.employee.dto.BindPlatformResult;
import com.yiyundao.compensation.modules.employee.dto.BindPlatformResult.ConflictInfo;
import com.yiyundao.compensation.modules.employee.dto.BindResult;
import com.yiyundao.compensation.modules.employee.dto.EmployeeProfileChangePayload;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.infrastructure.dao.EmployeeMapper;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.common.utils.ValidationUtils;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payroll.entity.PayCycle;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payroll.service.PayCycleService;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.modules.user.entity.ExternalIdentity;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.ExternalIdentityService;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl extends ServiceImpl<EmployeeMapper, Employee> implements EmployeeService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 200;

    private final EncryptionService encryptionService;
    private final ObjectProvider<UserBindingService> userBindingServiceProvider;
    private final ObjectProvider<ApprovalEngine> approvalEngineProvider;
    private final SysUserService sysUserService;
    private final ExternalIdentityService externalIdentityService;
    private final ApprovalWorkflowMapper approvalWorkflowMapper;
    private final PayrollLineService payrollLineService;
    private final PayrollBatchService payrollBatchService;
    private final PayCycleService payCycleService;
    private final PaymentRecordService paymentRecordService;
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
        normalizeEmploymentType(employee);
        validateFinancialData(employee.getSettlementAccountType(), employee.getSettlementAccount(), employee.getBankAccount());
        log.debug("员工数据验证通过");
    }

    @Override
    @Transactional
    public EmployeeVO createEmployee(Employee employee) {
        log.info("创建员工: {}", employee.getName());
        PlatformBinding platformBinding = extractPlatformBinding(employee);
        normalizeFinancialFields(employee);
        validateEmployeeData(employee);
        if (existsByEmployeeId(employee.getEmployeeId())) {
            throw new BusinessException(ErrorCode.RESOURCE_EXISTS, "员工工号已存在: " + employee.getEmployeeId());
        }
        encryptSensitiveData(employee);
        setDefaultValues(employee);
        employee.setProvider(null);
        employee.setSubjectId(null);
        save(employee);
        upsertPlatformBinding(platformBinding, employee.getId(), null, "manual");
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
        PlatformBinding platformBinding = extractPlatformBinding(updateInfo);
        validateFinancialDataForUpdate(existingEmployee, updateInfo);
        if (StringUtils.hasText(updateInfo.getName())) existingEmployee.setName(updateInfo.getName());
        if (StringUtils.hasText(updateInfo.getPhone())) existingEmployee.setPhone(updateInfo.getPhone());
        if (StringUtils.hasText(updateInfo.getEmail())) existingEmployee.setEmail(updateInfo.getEmail());
        if (updateInfo.getDepartment() != null) {
            String department = updateInfo.getDepartment().trim();
            existingEmployee.setDepartment(StringUtils.hasText(department) ? department : null);
        }
        if (StringUtils.hasText(updateInfo.getPosition())) existingEmployee.setPosition(updateInfo.getPosition());
        if (StringUtils.hasText(updateInfo.getEmploymentType())) {
            existingEmployee.setEmploymentType(normalizeEmploymentType(updateInfo.getEmploymentType()));
        }
        boolean statusUpdated = false;
        if (StringUtils.hasText(updateInfo.getStatus())) {
            existingEmployee.setStatus(parseEmployeeStatus(updateInfo.getStatus()).getCode());
            statusUpdated = true;
        }
        if (updateInfo.getOffline() != null) existingEmployee.setOffline(updateInfo.getOffline());
        if (updateInfo.getManagerId() != null) existingEmployee.setManagerId(updateInfo.getManagerId());
        if (updateInfo.getHireDate() != null) existingEmployee.setHireDate(updateInfo.getHireDate());
        if (StringUtils.hasText(updateInfo.getEncryptedIdCard())) {
            existingEmployee.setEncryptedIdCard(encryptionService.encryptIdCard(updateInfo.getEncryptedIdCard()));
        }
        if (StringUtils.hasText(updateInfo.getSettlementAccountType())) {
            existingEmployee.setSettlementAccountType(normalizeSettlementAccountType(updateInfo.getSettlementAccountType()));
        }
        if (StringUtils.hasText(updateInfo.getSettlementAccountName())) {
            existingEmployee.setSettlementAccountName(updateInfo.getSettlementAccountName().trim());
        }
        if (StringUtils.hasText(updateInfo.getBankBranchName())) {
            existingEmployee.setBankBranchName(updateInfo.getBankBranchName().trim());
        }
        String encryptedSettlementAccount = null;
        if (StringUtils.hasText(updateInfo.getSettlementAccount())) {
            encryptedSettlementAccount = encryptionService.encrypt(updateInfo.getSettlementAccount().trim());
            existingEmployee.setSettlementAccount(encryptedSettlementAccount);
        }
        String encryptedBankAccount = null;
        if (StringUtils.hasText(updateInfo.getBankAccount())) {
            encryptedBankAccount = encryptionService.encrypt(updateInfo.getBankAccount().trim());
            existingEmployee.setBankAccount(encryptedBankAccount);
        }
        if (StringUtils.hasText(updateInfo.getBankName())) existingEmployee.setBankName(updateInfo.getBankName());

        if (StringUtils.hasText(updateInfo.getSettlementAccount())
                && SettlementAccountType.BANK_CARD.getCode().equals(resolveSettlementType(existingEmployee))
                && !StringUtils.hasText(updateInfo.getBankAccount())) {
            existingEmployee.setBankAccount(encryptedSettlementAccount);
        }

        if (StringUtils.hasText(updateInfo.getBankAccount()) && !StringUtils.hasText(updateInfo.getSettlementAccount())) {
            if (!StringUtils.hasText(updateInfo.getSettlementAccountType())
                    || SettlementAccountType.BANK_CARD.getCode().equals(resolveSettlementType(existingEmployee))) {
                existingEmployee.setSettlementAccount(encryptedBankAccount);
                existingEmployee.setSettlementAccountType(SettlementAccountType.BANK_CARD.getCode());
            }
        }

        if (!SettlementAccountType.BANK_CARD.getCode().equals(resolveSettlementType(existingEmployee))) {
            existingEmployee.setBankAccount(null);
            existingEmployee.setBankName(null);
            existingEmployee.setBankBranchName(null);
        }

        if ((StringUtils.hasText(updateInfo.getSettlementAccount()) || StringUtils.hasText(updateInfo.getBankAccount()))
                && !StringUtils.hasText(existingEmployee.getSettlementAccountName())) {
            existingEmployee.setSettlementAccountName(existingEmployee.getName());
        }

        if (!StringUtils.hasText(existingEmployee.getSettlementAccountType())
                && (StringUtils.hasText(existingEmployee.getSettlementAccount())
                || StringUtils.hasText(existingEmployee.getBankAccount()))) {
            existingEmployee.setSettlementAccountType(SettlementAccountType.BANK_CARD.getCode());
        }

        updateById(existingEmployee);
        if (statusUpdated) {
            syncUserStatusAfterEmployeeStatusChange(existingEmployee.getId(), existingEmployee.getStatus());
        }
        if (platformBinding != null) {
            SysUser user = sysUserService.getOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getEmployeeId, existingEmployee.getId())
                    .last("limit 1"));
            upsertPlatformBinding(platformBinding, existingEmployee.getId(), user != null ? user.getId() : null, "manual");
        }
        log.info("员工信息更新成功: id={}", id);
        return voConverter.toEmployeeVO(existingEmployee);
    }

    @Override
    public EmployeeVO getCurrentEmployeeProfile(Long userId) {
        Employee employee = resolveEmployeeByUserId(userId);
        return getEmployeeVO(employee.getId());
    }

    @Override
    @Transactional
    public EmployeeVO updateCurrentEmployeeContact(Long userId, String phone, String email) {
        Employee employee = resolveEmployeeByUserId(userId);
        Employee update = new Employee();
        if (StringUtils.hasText(phone)) {
            update.setPhone(phone.trim());
        }
        if (StringUtils.hasText(email)) {
            update.setEmail(email.trim());
        }
        if (!StringUtils.hasText(update.getPhone()) && !StringUtils.hasText(update.getEmail())) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "至少提供一个联系方式字段（phone/email）");
        }
        return updateEmployee(employee.getId(), update);
    }

    @Override
    @Transactional
    public Long submitCurrentEmployeeProfileChange(Long userId, EmployeeProfileChangePayload payload, String reason) {
        Employee employee = resolveEmployeeByUserId(userId);
        if (payload == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少变更内容");
        }

        EmployeeProfileChangePayload normalizedPayload = payload.normalize();
        if (!normalizedPayload.hasAnyChangeField()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "至少提交一个需要审核的字段");
        }
        validateProfileChangePayload(normalizedPayload);
        ensureNoPendingProfileChange(employee.getId());

        Map<String, Object> workflowData = buildProfileChangeWorkflowData(employee, normalizedPayload, reason);
        ApprovalEngine approvalEngine = approvalEngineProvider.getObject();
        Long workflowId = approvalEngine.startWorkflow(
                WorkflowType.OFFLINE,
                buildEmployeeProfileChangeBusinessKey(employee.getId()),
                BUSINESS_TYPE_EMPLOYEE_PROFILE_CHANGE,
                userId,
                workflowData
        );
        log.info("员工资料变更申请已提交: workflowId={}, employeeId={}, initiatorId={}",
                workflowId, employee.getId(), userId);
        return workflowId;
    }

    private void ensureNoPendingProfileChange(Long employeeId) {
        Long pendingCount = approvalWorkflowMapper.selectCount(new LambdaQueryWrapper<ApprovalWorkflow>()
                .eq(ApprovalWorkflow::getEmployeeId, employeeId)
                .eq(ApprovalWorkflow::getBusinessType, BUSINESS_TYPE_EMPLOYEE_PROFILE_CHANGE)
                .eq(ApprovalWorkflow::getStatus, ApprovalStatus.PENDING));
        if (pendingCount != null && pendingCount > 0) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "已有待审批的资料变更申请，请等待处理完成后再提交");
        }
    }

    @Override
    public PageResponse<EmployeeApprovalRecordVO> pageCurrentEmployeeProfileChanges(Long userId, int pageNum, int pageSize) {
        Employee employee = resolveEmployeeByUserId(userId);
        return pageEmployeeApprovalsInternal(employee.getId(), userId, BUSINESS_TYPE_EMPLOYEE_PROFILE_CHANGE, pageNum, pageSize);
    }

    @Override
    @Transactional
    public EmployeeVO applyApprovedProfileChange(Long workflowId, Long employeeId, EmployeeProfileChangePayload payload) {
        if (workflowId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "workflowId不能为空");
        }
        if (employeeId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "employeeId不能为空");
        }
        if (payload == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "审批变更数据不能为空");
        }

        EmployeeProfileChangePayload normalizedPayload = payload.normalize();
        if (!normalizedPayload.hasAnyChangeField()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "审批变更数据为空");
        }
        validateProfileChangePayload(normalizedPayload);
        assertProfileChangeSnapshotStillMatches(workflowId, employeeId);

        Employee updateInfo = new Employee();
        updateInfo.setName(normalizedPayload.getName());
        updateInfo.setEncryptedIdCard(normalizedPayload.getIdCard());
        updateInfo.setSettlementAccountType(normalizedPayload.getSettlementAccountType());
        updateInfo.setSettlementAccount(normalizedPayload.getSettlementAccount());
        updateInfo.setSettlementAccountName(normalizedPayload.getSettlementAccountName());
        updateInfo.setBankAccount(normalizedPayload.getBankAccount());
        updateInfo.setBankName(normalizedPayload.getBankName());
        updateInfo.setBankBranchName(normalizedPayload.getBankBranchName());

        EmployeeVO result = updateEmployee(employeeId, updateInfo);
        log.info("审批通过后已应用员工资料变更: workflowId={}, employeeId={}", workflowId, employeeId);
        return result;
    }

    @Override
    public EmployeeVO getEmployeeVO(Long id) {
        Employee employee = getById(id);
        EmployeeVO vo = voConverter.toEmployeeVO(employee);
        if (vo == null || vo.getManagerId() == null) {
            return vo;
        }
        Employee manager = getById(vo.getManagerId());
        if (manager != null) {
            vo.setManagerName(manager.getName());
        }
        return vo;
    }

    @Override
    public PageResponse<EmployeeApprovalRecordVO> pageEmployeeApprovals(Long employeeId, int pageNum, int pageSize) {
        return pageEmployeeApprovalsInternal(employeeId, null, null, pageNum, pageSize);
    }

    @Override
    public PageResponse<EmployeePayslipRecordVO> pageEmployeePayslips(Long employeeId, int pageNum, int pageSize) {
        long current = safePage(pageNum);
        long size = safeSize(pageSize);
        Page<PayrollLine> result = payrollLineService.page(
                new Page<>(current, size),
                new LambdaQueryWrapper<PayrollLine>()
                        .eq(PayrollLine::getEmployeeId, employeeId)
                        .orderByDesc(PayrollLine::getId)
        );

        Set<Long> batchIds = result.getRecords().stream()
                .map(PayrollLine::getBatchId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, PayrollBatch> batchMap = batchIds.isEmpty() ? Map.of() : payrollBatchService.listByIds(batchIds)
                .stream()
                .filter(batch -> batch != null && batch.getId() != null)
                .collect(Collectors.toMap(PayrollBatch::getId, batch -> batch));

        Set<Long> cycleIds = batchMap.values().stream()
                .map(PayrollBatch::getPayCycleId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, PayCycle> cycleMap = cycleIds.isEmpty() ? Map.of() : payCycleService.listByIds(cycleIds)
                .stream()
                .filter(cycle -> cycle != null && cycle.getId() != null)
                .collect(Collectors.toMap(PayCycle::getId, cycle -> cycle));

        List<EmployeePayslipRecordVO> records = result.getRecords().stream().map(line -> {
            EmployeePayslipRecordVO vo = new EmployeePayslipRecordVO();
            vo.setLineId(line.getId());
            vo.setBatchId(line.getBatchId());
            vo.setEmploymentType(line.getEmploymentType());
            vo.setCurrency(line.getCurrency());
            vo.setGrossAmount(line.getGrossAmount());
            vo.setTaxAmount(line.getTaxAmount());
            vo.setSocialAmount(line.getSocialAmount());
            vo.setNetAmount(line.getNetAmount());
            vo.setStatus(line.getStatus());
            vo.setCreateTime(line.getCreateTime());
            vo.setUpdateTime(line.getUpdateTime());

            PayrollBatch batch = batchMap.get(line.getBatchId());
            if (batch != null) {
                vo.setPayCycleId(batch.getPayCycleId());
                vo.setPeriodLabel(batch.getPeriodLabel());
                vo.setBatchStatus(batch.getStatus() != null ? batch.getStatus().name().toLowerCase() : null);
                vo.setPaymentBatchNo(batch.getPaymentBatchNo());
            }
            if (vo.getPayCycleId() != null) {
                PayCycle cycle = cycleMap.get(vo.getPayCycleId());
                if (cycle != null) {
                    vo.setPeriodStart(cycle.getStartDate());
                    vo.setPeriodEnd(cycle.getEndDate());
                }
            }
            return vo;
        }).toList();

        return PageResponse.of(result, records);
    }

    @Override
    public PageResponse<PaymentRecordItemVO> pageEmployeePayments(Long employeeId, int pageNum, int pageSize) {
        long current = safePage(pageNum);
        long size = safeSize(pageSize);
        Page<PaymentRecord> result = paymentRecordService.page(
                new Page<>(current, size),
                new LambdaQueryWrapper<PaymentRecord>()
                        .eq(PaymentRecord::getEmployeeId, employeeId)
                        .orderByDesc(PaymentRecord::getCreateTime)
                        .orderByDesc(PaymentRecord::getId)
        );
        List<PaymentRecordItemVO> records = result.getRecords().stream()
                .map(PaymentRecordItemVO::from)
                .toList();
        return PageResponse.of(result, records);
    }

    @Override
    public PageResponse<EmployeeListItemVO> pageEmployees(int pageNum, int pageSize, String keyword,
                                                          String department, String status,
                                                          Boolean isOffline, String provider,
                                                          Long managerId, String sortBy, String order) {
        log.info("分页查询员工: page={}, size={}, keyword={}", pageNum, pageSize, keyword);
        Page<Employee> page = new Page<>(safePage(pageNum), safeSize(pageSize));
        LambdaQueryWrapper<Employee> queryWrapper = buildQueryWrapper(keyword, department, status, isOffline, provider, managerId, sortBy, order);
        Page<Employee> result = page(page, queryWrapper);
        Map<Long, ExternalIdentity> identityMap = buildPrimaryIdentityMap(result.getRecords());
        List<EmployeeListItemVO> voList = result.getRecords().stream()
                .map(employee -> {
                    EmployeeListItemVO vo = voConverter.toEmployeeListItemVO(employee);
                    ExternalIdentity identity = identityMap.get(employee.getId());
                    if (identity != null) {
                        vo.setProvider(identity.getProvider());
                        vo.setProviderName(translatePlatformName(identity.getProvider()));
                    }
                    return vo;
                })
                .toList();
        return PageResponse.of(voList, result.getCurrent(), result.getSize(), result.getTotal());
    }

    @Override
    public List<EmployeeVO> getOfflineEmployees(Long managerId) {
        log.info("查询架构外员工列表: managerId={}", managerId);
        LambdaQueryWrapper<Employee> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Employee::getOffline, true)
                   .eq(Employee::getStatus, EmployeeStatus.ACTIVE.getCode());
        if (managerId != null) queryWrapper.eq(Employee::getManagerId, managerId);
        return list(queryWrapper).stream()
                .map(voConverter::toEmployeeVO)
                .toList();
    }

    @Override
    public List<EmployeeVO> getResignedEmployees(Long managerId) {
        log.info("查询离职员工列表: managerId={}", managerId);
        LambdaQueryWrapper<Employee> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Employee::getStatus, EmployeeStatus.INACTIVE.getCode());
        if (managerId != null) queryWrapper.eq(Employee::getManagerId, managerId);
        return list(queryWrapper).stream()
                .map(voConverter::toEmployeeVO)
                .toList();
    }

    @Override
    @Transactional
    public BindPlatformResult bindPlatform(Long employeeId, BindPlatformRequest request) {
        log.info("绑定平台用户: employeeId={}, provider={}, subjectId={}",
                employeeId,
                request != null ? request.getProvider() : null,
                request != null ? request.getSubjectId() : null);

        // 1. 检查员工是否存在
        Employee employee = getById(employeeId);
        if (employee == null) {
            log.warn("员工不存在: {}", employeeId);
            return BindPlatformResult.failed(BindResult.EMPLOYEE_NOT_FOUND, "员工不存在");
        }

        String provider = normalizePlatform(request != null ? request.getProvider() : null);
        String subjectId = request != null && request.getSubjectId() != null ? request.getSubjectId().trim() : null;
        if (!StringUtils.hasText(provider) || !StringUtils.hasText(subjectId)) {
            return BindPlatformResult.failed(BindResult.UNKNOWN_ERROR, "平台类型与平台用户ID不能为空");
        }

        SysUser user = findUserByEmployeeId(employee.getId());
        Long userId = user != null ? user.getId() : null;

        // 2. 检查是否已是同一账号（无需重复绑定；必要时补齐用户关联）
        ExternalIdentity currentIdentity = externalIdentityService.findPrimaryByEmployeeId(employeeId);
        if (currentIdentity != null
                && provider.equals(currentIdentity.getProvider())
                && subjectId.equals(currentIdentity.getSubjectId())) {
            if (!isOccupiedByOtherBinding(currentIdentity, employeeId, userId)) {
                if (userId != null && !userId.equals(currentIdentity.getUserId())) {
                    performBinding(employee, provider, subjectId, "manual", false);
                    return BindPlatformResult.success(employeeId, employee.getEmployeeId(), employee.getName(),
                            provider, subjectId, userId);
                }
                log.info("已是同一平台账号，无需重复绑定: employeeId={}", employeeId);
                return BindPlatformResult.alreadyBound(employeeId, employee.getEmployeeId(), employee.getName(),
                        provider, subjectId);
            }
        }

        // 3. 检查目标平台账号是否已被其他员工或用户占用
        ExternalIdentity occupiedIdentity = externalIdentityService.findActiveIdentity(
                provider,
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                subjectId
        );
        if (isOccupiedByOtherBinding(occupiedIdentity, employeeId, userId)) {
            Employee occupiedEmployee = occupiedIdentity.getEmployeeId() != null
                    ? getById(occupiedIdentity.getEmployeeId())
                    : null;
            // 冲突：平台账号已被其他员工占用
            log.warn("平台账号冲突: subjectId={}, occupiedBy={}",
                    subjectId, occupiedIdentity.getEmployeeId());

            // 构建冲突信息
            ConflictInfo conflictInfo = ConflictInfo.builder()
                    .conflictType("PLATFORM_OCCUPIED")
                    .occupiedEmployeeId(occupiedEmployee != null ? occupiedEmployee.getId() : occupiedIdentity.getEmployeeId())
                    .occupiedEmployeeName(occupiedEmployee != null ? occupiedEmployee.getName() : null)
                    .occupiedEmployeeNo(occupiedEmployee != null ? occupiedEmployee.getEmployeeId() : null)
                    .occupiedUserId(occupiedIdentity.getUserId())
                    .occupiedProvider(provider)
                    .occupiedSubjectId(subjectId)
                    .detail(buildPlatformConflictDetail(occupiedEmployee, occupiedIdentity))
                    .build();

            // 发起审批流程
            Long workflowId = startApprovalWorkflow(employee, provider, subjectId, conflictInfo);

            return BindPlatformResult.pendingApproval(employeeId, employee.getEmployeeId(), employee.getName(),
                    provider, subjectId, workflowId, "PLATFORM_BIND", conflictInfo);
        }

        // 4. 执行绑定（双向同步）
        performBinding(employee, provider, subjectId, "manual", false);

        log.info("平台用户绑定成功: employeeId={}, userId={}", employeeId, userId);
        return BindPlatformResult.success(employeeId, employee.getEmployeeId(), employee.getName(),
                provider, subjectId, userId);
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

        Long userId = null;

        if (employee.getId() != null) {
            SysUser user = sysUserService.getOne(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmployeeId, employee.getId()).last("limit 1"));
            if (user != null) {
                userId = user.getId();
            }
        }

        ExternalIdentity identity = externalIdentityService.findPrimaryByEmployeeId(employeeId);
        if (identity == null && userId != null) {
            identity = externalIdentityService.findPrimaryByUserId(userId);
        }
        if (identity != null) {
            externalIdentityService.deactivatePlatformIdentity(
                    identity.getProvider(),
                    identity.getTenantKey(),
                    identity.getSubjectType(),
                    identity.getSubjectId(),
                    employeeId,
                    userId,
                    "manual"
            );
        }

        log.info("平台用户解绑成功: employeeId={}, userId={}, reason={}", employeeId, userId, reason);
    }

    @Override
    @Transactional
    public BindPlatformResult executeApprovedBinding(Long workflowId, Long employeeId,
                                                      String provider, String subjectId) {
        log.info("执行审批通过后的绑定: workflowId={}, employeeId={}, provider={}, subjectId={}",
                workflowId, employeeId, provider, subjectId);

        Employee employee = getById(employeeId);
        if (employee == null) {
            log.warn("员工不存在: {}", employeeId);
            return BindPlatformResult.failed(BindResult.EMPLOYEE_NOT_FOUND, "员工不存在");
        }

        String normalizedProvider = normalizePlatform(provider);
        String normalizedSubjectId = subjectId != null ? subjectId.trim() : null;
        if (!StringUtils.hasText(normalizedProvider) || !StringUtils.hasText(normalizedSubjectId)) {
            return BindPlatformResult.failed(BindResult.UNKNOWN_ERROR, "平台类型与平台用户ID不能为空");
        }

        assertApprovedBindingConflictStillMatches(workflowId, employeeId, normalizedProvider, normalizedSubjectId);

        // 执行绑定
        performBinding(employee, normalizedProvider, normalizedSubjectId, "approval:" + workflowId, true);

        SysUser user = findUserByEmployeeId(employee.getId());
        Long userId = user != null ? user.getId() : null;

        log.info("审批通过后的绑定执行成功: employeeId={}, userId={}", employeeId, userId);
        return BindPlatformResult.success(employeeId, employee.getEmployeeId(), employee.getName(),
                normalizedProvider, normalizedSubjectId, userId);
    }

    /**
     * 执行实际的绑定操作（双向同步）
     */
    private void performBinding(Employee employee, String provider, String subjectId, String source, boolean forceReassign) {
        // 仅维护 external_identity，旧字段不再写入
        SysUser user = findUserByEmployeeId(employee.getId());
        Long userId = user != null ? user.getId() : null;

        ExternalIdentity occupiedIdentity = externalIdentityService.findActiveIdentity(
                provider,
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                subjectId
        );
        if (isOccupiedByOtherBinding(occupiedIdentity, employee.getId(), userId)) {
            if (!forceReassign) {
                throw new IllegalStateException("外部身份已绑定其他员工或用户，provider=" + provider + ", subjectId=" + subjectId);
            }
            deactivateIdentity(occupiedIdentity, source);
        }

        ExternalIdentity currentIdentity = externalIdentityService.findPrimaryByEmployeeId(employee.getId());
        if (currentIdentity != null
                && isDifferentIdentity(currentIdentity, provider, subjectId)) {
            deactivateIdentity(currentIdentity, source);
        }

        externalIdentityService.upsertPlatformIdentity(
                provider,
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                subjectId,
                employee.getId(),
                userId,
                source,
                true
        );
    }

    private SysUser findUserByEmployeeId(Long employeeId) {
        if (employeeId == null) {
            return null;
        }
        return sysUserService.getOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmployeeId, employeeId).last("limit 1"));
    }

    private boolean isOccupiedByOtherBinding(ExternalIdentity identity, Long employeeId, Long userId) {
        if (identity == null) {
            return false;
        }
        boolean employeeConflict = identity.getEmployeeId() != null
                && employeeId != null
                && !identity.getEmployeeId().equals(employeeId);
        boolean userConflict = identity.getUserId() != null
                && (userId == null || !identity.getUserId().equals(userId));
        return employeeConflict || userConflict;
    }

    private boolean isDifferentIdentity(ExternalIdentity identity, String provider, String subjectId) {
        return identity != null
                && (!provider.equals(identity.getProvider()) || !subjectId.equals(identity.getSubjectId()));
    }

    private void deactivateIdentity(ExternalIdentity identity, String source) {
        if (identity == null) {
            return;
        }
        externalIdentityService.deactivatePlatformIdentity(
                identity.getProvider(),
                identity.getTenantKey(),
                identity.getSubjectType(),
                identity.getSubjectId(),
                identity.getEmployeeId(),
                identity.getUserId(),
                source
        );
    }

    private String buildPlatformConflictDetail(Employee occupiedEmployee, ExternalIdentity occupiedIdentity) {
        if (occupiedEmployee != null) {
            return String.format("平台账号已被员工【%s(%s)】占用，如需强制绑定请等待审批",
                    occupiedEmployee.getName(), occupiedEmployee.getEmployeeId());
        }
        if (occupiedIdentity != null && occupiedIdentity.getUserId() != null) {
            return "平台账号已被其他系统用户占用，如需强制绑定请等待审批";
        }
        return "平台账号已被占用，如需强制绑定请等待审批";
    }

    /**
     * 发起审批流程
     */
    private Long startApprovalWorkflow(Employee employee, String provider, String subjectId,
                                        ConflictInfo conflictInfo) {
        Long operatorId = getCurrentUserId();

        Map<String, Object> data = new HashMap<>();
        data.put("employeeId", employee.getId());
        data.put("employeeName", employee.getName());
        data.put("employeeNo", employee.getEmployeeId());
        data.put("provider", provider);
        data.put("subjectId", subjectId);
        data.put("conflictInfo", toJsonSafe(conflictInfo));
        data.put("snapshotOccupiedEmployeeId", conflictInfo != null ? conflictInfo.getOccupiedEmployeeId() : null);
        data.put("snapshotOccupiedUserId", conflictInfo != null ? conflictInfo.getOccupiedUserId() : null);
        data.put("snapshotOccupiedProvider", conflictInfo != null ? conflictInfo.getOccupiedProvider() : null);
        data.put("snapshotOccupiedSubjectId", conflictInfo != null ? conflictInfo.getOccupiedSubjectId() : null);
        data.put("action", "BIND_PLATFORM");
        data.put("reason", "平台账号冲突，申请强制绑定");

        ApprovalEngine approvalEngine = approvalEngineProvider.getObject();
        Long workflowId = approvalEngine.startWorkflow(
                WorkflowType.OFFLINE,
                buildEmployeeApprovalBusinessKey(employee.getId()),
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

    @SuppressWarnings("unchecked")
    private void assertApprovedBindingConflictStillMatches(Long workflowId, Long targetEmployeeId,
                                                           String provider, String subjectId) {
        Map<String, Object> data = loadWorkflowData(workflowId);
        Long snapshotEmployeeId = toLong(data.get("snapshotOccupiedEmployeeId"));
        Long snapshotUserId = toLong(data.get("snapshotOccupiedUserId"));
        boolean hasSnapshotUserId = data.containsKey("snapshotOccupiedUserId");
        String snapshotProvider = trimToNull(data.get("snapshotOccupiedProvider"));
        String snapshotSubjectId = trimToNull(data.get("snapshotOccupiedSubjectId"));
        if ((snapshotEmployeeId == null && snapshotUserId == null)
                || !StringUtils.hasText(snapshotProvider)
                || !StringUtils.hasText(snapshotSubjectId)) {
            return;
        }
        if (!provider.equals(normalizePlatform(snapshotProvider)) || !subjectId.equals(snapshotSubjectId)) {
            throw new BusinessException(ErrorCode.REQUEST_CONFLICT, "平台绑定审批数据已过期，请重新提交审批");
        }
        ExternalIdentity currentIdentity = externalIdentityService.findActiveIdentity(
                provider,
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                subjectId
        );
        if (currentIdentity == null) {
            return;
        }
        boolean belongsToTargetEmployee = targetEmployeeId != null && targetEmployeeId.equals(currentIdentity.getEmployeeId());
        if (belongsToTargetEmployee && (!hasSnapshotUserId || Objects.equals(snapshotUserId, currentIdentity.getUserId()))) {
            return;
        }
        boolean employeeChanged = !Objects.equals(snapshotEmployeeId, currentIdentity.getEmployeeId());
        boolean userChanged = hasSnapshotUserId && !Objects.equals(snapshotUserId, currentIdentity.getUserId());
        if (employeeChanged || userChanged) {
            throw new BusinessException(ErrorCode.REQUEST_CONFLICT, "平台账号占用关系已变更，请重新提交审批");
        }
    }

    private Map<String, Object> loadWorkflowData(Long workflowId) {
        if (workflowId == null || workflowId < 1) {
            return Map.of();
        }
        ApprovalWorkflow workflow = approvalWorkflowMapper.selectById(workflowId);
        if (workflow == null || !StringUtils.hasText(workflow.getWorkflowData())) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(workflow.getWorkflowData(), Map.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "解析审批数据失败");
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private PlatformBinding extractPlatformBinding(Employee employee) {
        if (employee == null) {
            return null;
        }
        if (!StringUtils.hasText(employee.getProvider()) && !StringUtils.hasText(employee.getSubjectId())) {
            return null;
        }
        if (!StringUtils.hasText(employee.getProvider()) || !StringUtils.hasText(employee.getSubjectId())) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "平台提供方与平台主体ID需同时传入");
        }
        String provider = normalizePlatform(employee.getProvider());
        String subjectId = employee.getSubjectId().trim();
        if (!StringUtils.hasText(provider) || !StringUtils.hasText(subjectId)) {
            return null;
        }
        return new PlatformBinding(provider, subjectId);
    }

    private void upsertPlatformBinding(PlatformBinding binding, Long employeeId, Long userId, String source) {
        if (binding == null) {
            return;
        }
        externalIdentityService.upsertPlatformIdentity(
                binding.provider(),
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                binding.subjectId(),
                employeeId,
                userId,
                source,
                true
        );
    }

    private record PlatformBinding(String provider, String subjectId) { }

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
        } catch (Exception ignored) {
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "未识别到当前登录用户");
    }

    /**
     * 安全转JSON
     */
    private String toJsonSafe(Object o) {
        try { return objectMapper.writeValueAsString(o); } catch (Exception e) { return null; }
    }

    private String buildEmployeeApprovalBusinessKey(Long employeeId) {
        return "EMPLOYEE-" + employeeId + "-" + System.currentTimeMillis();
    }

    private String buildEmployeeProfileChangeBusinessKey(Long employeeId) {
        return "EMPLOYEE-PROFILE-" + employeeId + "-" + System.currentTimeMillis();
    }

    private Employee resolveEmployeeByUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在");
        }
        if (user.getEmployeeId() == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "当前账号未绑定员工档案");
        }
        Employee employee = getById(user.getEmployeeId());
        if (employee == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "员工不存在: " + user.getEmployeeId());
        }
        return employee;
    }

    private void validateProfileChangePayload(EmployeeProfileChangePayload payload) {
        if (payload == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少变更内容");
        }
        if (StringUtils.hasText(payload.getName()) && payload.getName().trim().length() > 100) {
            throw new BusinessException(ErrorCode.PARAM_FORMAT_ERROR, "姓名长度不能超过100个字符");
        }
        if (StringUtils.hasText(payload.getIdCard()) && !ValidationUtils.isValidIdCard(payload.getIdCard().trim())) {
            throw new BusinessException(ErrorCode.PARAM_FORMAT_ERROR, "身份证号格式不正确");
        }
        if (StringUtils.hasText(payload.getSettlementAccountName())
                && payload.getSettlementAccountName().trim().length() > 100) {
            throw new BusinessException(ErrorCode.PARAM_FORMAT_ERROR, "收款账户实名长度不能超过100个字符");
        }
        if (StringUtils.hasText(payload.getBankName()) && payload.getBankName().trim().length() > 100) {
            throw new BusinessException(ErrorCode.PARAM_FORMAT_ERROR, "开户银行长度不能超过100个字符");
        }
        if (StringUtils.hasText(payload.getBankBranchName()) && payload.getBankBranchName().trim().length() > 120) {
            throw new BusinessException(ErrorCode.PARAM_FORMAT_ERROR, "开户支行长度不能超过120个字符");
        }
        validateFinancialData(payload.getSettlementAccountType(), payload.getSettlementAccount(), payload.getBankAccount());
    }

    private void assertProfileChangeSnapshotStillMatches(Long workflowId, Long employeeId) {
        Map<String, Object> data = loadWorkflowData(workflowId);
        Integer snapshotVersion = toInteger(data.get("snapshotVersion"));
        String snapshotUpdateTime = trimToNull(data.get("snapshotUpdateTime"));
        if (snapshotVersion == null && !StringUtils.hasText(snapshotUpdateTime)) {
            return;
        }
        Employee current = getById(employeeId);
        if (current == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "员工不存在: " + employeeId);
        }
        if (snapshotVersion != null && current.getVersion() != null && !snapshotVersion.equals(current.getVersion())) {
            throw new BusinessException(ErrorCode.REQUEST_CONFLICT, "员工资料已变更，请重新提交审批");
        }
        String currentUpdateTime = current.getUpdateTime() != null ? current.getUpdateTime().toString() : null;
        if (StringUtils.hasText(snapshotUpdateTime)
                && currentUpdateTime != null
                && !snapshotUpdateTime.equals(currentUpdateTime)) {
            throw new BusinessException(ErrorCode.REQUEST_CONFLICT, "员工资料已变更，请重新提交审批");
        }
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> buildProfileChangeWorkflowData(Employee employee,
                                                               EmployeeProfileChangePayload payload,
                                                               String reason) {
        Map<String, Object> data = new HashMap<>();
        data.put("employeeId", employee.getId());
        data.put("employeeNo", employee.getEmployeeId());
        data.put("employeeName", employee.getName());
        data.put("snapshotVersion", employee.getVersion());
        data.put("snapshotUpdateTime", employee.getUpdateTime() != null ? employee.getUpdateTime().toString() : null);
        data.put("action", BUSINESS_TYPE_EMPLOYEE_PROFILE_CHANGE);
        data.put("changedFields", payload.changedFields());
        if (StringUtils.hasText(reason)) {
            data.put("reason", reason.trim());
        }
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            data.put("changePayloadCipher", encryptionService.encrypt(payloadJson));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "序列化变更内容失败");
        }
        return data;
    }

    private PageResponse<EmployeeApprovalRecordVO> pageEmployeeApprovalsInternal(Long employeeId,
                                                                                 Long initiatorId,
                                                                                 String businessType,
                                                                                 int pageNum,
                                                                                 int pageSize) {
        long current = safePage(pageNum);
        long size = safeSize(pageSize);
        Page<ApprovalWorkflow> page = new Page<>(current, size);
        LambdaQueryWrapper<ApprovalWorkflow> queryWrapper = new LambdaQueryWrapper<ApprovalWorkflow>()
                .eq(ApprovalWorkflow::getEmployeeId, employeeId)
                .orderByDesc(ApprovalWorkflow::getSubmitTime);
        if (initiatorId != null) {
            queryWrapper.eq(ApprovalWorkflow::getInitiatorId, initiatorId);
        }
        if (StringUtils.hasText(businessType)) {
            queryWrapper.eq(ApprovalWorkflow::getBusinessType, businessType);
        }
        Page<ApprovalWorkflow> result = approvalWorkflowMapper.selectPage(page, queryWrapper);

        Set<Long> userIds = new HashSet<>();
        for (ApprovalWorkflow workflow : result.getRecords()) {
            if (workflow.getInitiatorId() != null) {
                userIds.add(workflow.getInitiatorId());
            }
            if (workflow.getCurrentApproverId() != null) {
                userIds.add(workflow.getCurrentApproverId());
            }
        }
        Map<Long, String> userNameMap = buildUserNameMap(userIds);

        List<EmployeeApprovalRecordVO> records = result.getRecords().stream().map(workflow -> {
            EmployeeApprovalRecordVO vo = new EmployeeApprovalRecordVO();
            vo.setId(workflow.getId());
            vo.setEmployeeId(workflow.getEmployeeId());
            vo.setWorkflowName(workflow.getWorkflowName());
            vo.setWorkflowType(workflow.getWorkflowType() != null ? workflow.getWorkflowType().getCode() : null);
            vo.setWorkflowTypeName(workflow.getWorkflowType() != null ? workflow.getWorkflowType().getName() : null);
            vo.setBusinessType(workflow.getBusinessType());
            vo.setBusinessKey(workflow.getBusinessKey());
            vo.setCurrentStep(workflow.getCurrentStep());
            vo.setTotalSteps(workflow.getTotalSteps());
            vo.setStatus(workflow.getStatus() != null ? workflow.getStatus().getCode() : null);
            vo.setStatusName(workflow.getStatus() != null ? workflow.getStatus().getName() : null);
            vo.setInitiatorId(workflow.getInitiatorId());
            vo.setInitiatorName(userNameMap.get(workflow.getInitiatorId()));
            vo.setCurrentApproverId(workflow.getCurrentApproverId());
            vo.setCurrentApproverName(userNameMap.get(workflow.getCurrentApproverId()));
            vo.setSubmitTime(workflow.getSubmitTime());
            vo.setCompleteTime(workflow.getCompleteTime());
            return vo;
        }).toList();

        return PageResponse.of(result, records);
    }

    private Map<Long, String> buildUserNameMap(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return sysUserService.listByIds(userIds).stream()
                .filter(user -> user != null && user.getId() != null)
                .collect(Collectors.toMap(
                        SysUser::getId,
                        user -> StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername()
                ));
    }

    @Override
    @Transactional
    public void updateStatus(Long employeeId, EmployeeStatus status) {
        log.info("更新员工状态: employeeId={}, status={}", employeeId, status);
        Employee employee = getById(employeeId);
        if (employee == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "员工不存在: " + employeeId);
        }
        employee.setStatus(status != null ? status.getCode() : null);
        updateById(employee);
        syncUserStatusAfterEmployeeStatusChange(employeeId, status != null ? status.getCode() : null);
        log.info("员工状态更新成功");
    }

    private void syncUserStatusAfterEmployeeStatusChange(Long employeeId, String employeeStatus) {
        if (employeeId == null || !StringUtils.hasText(employeeStatus)
                || EmployeeStatus.ACTIVE.getCode().equals(employeeStatus)) {
            return;
        }
        List<SysUser> activeUsers = sysUserService.list(new QueryWrapper<SysUser>()
                .select("id")
                .eq("employee_id", employeeId)
                .eq("status", UserStatus.ACTIVE.getCode()));
        if (activeUsers == null || activeUsers.isEmpty()) {
            return;
        }
        Set<Long> userIds = activeUsers.stream()
                .map(SysUser::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return;
        }
        boolean updated = sysUserService.update(new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SysUser>()
                .eq("employee_id", employeeId)
                .eq("status", UserStatus.ACTIVE.getCode())
                .set("status", UserStatus.INACTIVE.getCode()));
        if (updated) {
            sysUserService.batchIncrementPermissionVersion(userIds);
            log.info("员工非在职后已禁用关联用户: employeeId={}, userIds={}", employeeId, userIds);
        }
    }

    @Override
    @Transactional
    public void batchImport(List<Employee> employees) {
        log.info("批量导入员工: count={}", employees.size());
        Map<Employee, PlatformBinding> bindingMap = new HashMap<>();
        Set<String> seenEmployeeIds = new HashSet<>();
        List<Employee> toSave = employees.stream().filter(e -> {
            if (!StringUtils.hasText(e.getEmployeeId()) || !StringUtils.hasText(e.getName())) {
                log.warn("跳过无效数据: 员工工号或姓名为空");
                return false;
            }
            String employeeId = e.getEmployeeId().trim();
            e.setEmployeeId(employeeId);
            if (!seenEmployeeIds.add(employeeId)) {
                log.warn("跳过本次导入内重复员工工号: {}", employeeId);
                return false;
            }
            if (existsByEmployeeId(employeeId)) {
                log.warn("跳过已存在员工工号: {}", employeeId);
                return false;
            }
            return true;
        }).peek(e -> {
            PlatformBinding platformBinding = extractPlatformBinding(e);
            if (platformBinding != null) {
                bindingMap.put(e, platformBinding);
            }
            e.setProvider(null);
            e.setSubjectId(null);
        }).peek(this::normalizeFinancialFields)
                .peek(this::validateEmployeeData)
                .peek(this::normalizeEmploymentType)
                .peek(this::encryptSensitiveData)
                .peek(this::setDefaultValues)
                .toList();
        if (toSave.isEmpty()) {
            log.info("批量导入完成: 无有效员工可导入");
            return;
        }
        saveBatch(toSave);
        for (Employee employee : toSave) {
            PlatformBinding binding = bindingMap.get(employee);
            if (binding != null) {
                upsertPlatformBinding(binding, employee.getId(), null, "sync");
            }
        }
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
        if (employee == null) {
            return "";
        }
        if (StringUtils.hasText(employee.getBankAccount())) {
            return encryptionService.decrypt(employee.getBankAccount());
        }
        if (SettlementAccountType.BANK_CARD.getCode().equals(resolveSettlementType(employee))
                && StringUtils.hasText(employee.getSettlementAccount())) {
            return encryptionService.decrypt(employee.getSettlementAccount());
        }
        return ""; // 返回空字符串而非 null，避免前端处理困难
    }

    @Override
    public String getDecryptedSettlementAccount(Long employeeId) {
        Employee employee = getById(employeeId);
        if (employee == null) {
            return "";
        }
        if (StringUtils.hasText(employee.getSettlementAccount())) {
            return encryptionService.decrypt(employee.getSettlementAccount());
        }
        if (StringUtils.hasText(employee.getBankAccount())) {
            return encryptionService.decrypt(employee.getBankAccount());
        }
        return "";
    }

    @Override
    public Employee getByProviderAndSubjectId(String provider, String subjectId) {
        if (!StringUtils.hasText(provider) || !StringUtils.hasText(subjectId)) {
            return null;
        }
        Long employeeId = externalIdentityService.findBoundEmployeeId(
                provider,
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                subjectId
        );
        if (employeeId != null) {
            Employee employee = getById(employeeId);
            if (employee != null && EmployeeStatus.ACTIVE.getCode().equals(employee.getStatus())) {
                return employee;
            }
        }
        return null;
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
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "员工不存在: " + employeeId);
        }
        if (managerId != null) {
            Employee manager = getById(managerId);
            if (manager == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "负责人不存在: " + managerId);
            }
        }
        employee.setManagerId(managerId);
        updateById(employee);
    }

    private void encryptSensitiveData(Employee employee) {
        if (StringUtils.hasText(employee.getEncryptedIdCard())) {
            employee.setEncryptedIdCard(encryptionService.encryptIdCard(employee.getEncryptedIdCard()));
        }
        if (StringUtils.hasText(employee.getSettlementAccount())) {
            employee.setSettlementAccount(encryptionService.encrypt(employee.getSettlementAccount()));
        }
        if (StringUtils.hasText(employee.getBankAccount())) {
            employee.setBankAccount(encryptionService.encrypt(employee.getBankAccount()));
        }
    }

    private void setDefaultValues(Employee employee) {
        if (!StringUtils.hasText(employee.getStatus())) {
            employee.setStatus(EmployeeStatus.ACTIVE.getCode());
        } else {
            employee.setStatus(parseEmployeeStatus(employee.getStatus()).getCode());
        }
        if (!StringUtils.hasText(employee.getEmploymentType())) {
            employee.setEmploymentType(EmploymentType.FULL_TIME.getCode());
        }
        if (employee.getOffline() == null) {
            employee.setOffline(false);
        }
        if (!StringUtils.hasText(employee.getSettlementAccountType())
                && (StringUtils.hasText(employee.getSettlementAccount())
                || StringUtils.hasText(employee.getBankAccount()))) {
            employee.setSettlementAccountType(SettlementAccountType.BANK_CARD.getCode());
        }
        if (!StringUtils.hasText(employee.getSettlementAccountName())
                && (StringUtils.hasText(employee.getSettlementAccount())
                || StringUtils.hasText(employee.getBankAccount()))) {
            employee.setSettlementAccountName(employee.getName());
        }
    }

    private void normalizeFinancialFields(Employee employee) {
        if (employee == null) {
            return;
        }
        if (StringUtils.hasText(employee.getSettlementAccountType())) {
            employee.setSettlementAccountType(normalizeSettlementAccountType(employee.getSettlementAccountType()));
        }
        if (StringUtils.hasText(employee.getSettlementAccount())) {
            employee.setSettlementAccount(employee.getSettlementAccount().trim());
        }
        if (StringUtils.hasText(employee.getBankAccount())) {
            employee.setBankAccount(employee.getBankAccount().trim());
        }
        if (StringUtils.hasText(employee.getSettlementAccountName())) {
            employee.setSettlementAccountName(employee.getSettlementAccountName().trim());
        }
        if (StringUtils.hasText(employee.getBankName())) {
            employee.setBankName(employee.getBankName().trim());
        }
        if (StringUtils.hasText(employee.getBankBranchName())) {
            employee.setBankBranchName(employee.getBankBranchName().trim());
        }

        if (!StringUtils.hasText(employee.getSettlementAccount()) && StringUtils.hasText(employee.getBankAccount())) {
            employee.setSettlementAccount(employee.getBankAccount());
        }
        if (!StringUtils.hasText(employee.getSettlementAccountType())
                && (StringUtils.hasText(employee.getSettlementAccount()) || StringUtils.hasText(employee.getBankAccount()))) {
            employee.setSettlementAccountType(SettlementAccountType.BANK_CARD.getCode());
        }
        if (SettlementAccountType.BANK_CARD.getCode().equals(resolveSettlementType(employee))
                && !StringUtils.hasText(employee.getBankAccount())
                && StringUtils.hasText(employee.getSettlementAccount())) {
            employee.setBankAccount(employee.getSettlementAccount());
        }
        if (!StringUtils.hasText(employee.getSettlementAccountName())
                && (StringUtils.hasText(employee.getSettlementAccount()) || StringUtils.hasText(employee.getBankAccount()))) {
            employee.setSettlementAccountName(employee.getName());
        }
    }

    private void normalizeEmploymentType(Employee employee) {
        if (employee == null || !StringUtils.hasText(employee.getEmploymentType())) {
            return;
        }
        employee.setEmploymentType(normalizeEmploymentType(employee.getEmploymentType()));
    }

    private String normalizeEmploymentType(String employmentType) {
        if (!StringUtils.hasText(employmentType)) {
            return null;
        }
        EmploymentType type = EmploymentType.fromCode(employmentType.trim());
        if (type == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "无效的用工类型: " + employmentType);
        }
        return type.getCode();
    }

    private void validateFinancialData(String settlementAccountType, String settlementAccount, String bankAccount) {
        String normalizedType = normalizeSettlementAccountType(settlementAccountType);
        if (StringUtils.hasText(normalizedType) && SettlementAccountType.fromCode(normalizedType) == null) {
            throw new BusinessException(ErrorCode.PARAM_FORMAT_ERROR, "不支持的收款账户类型: " + settlementAccountType);
        }
        String account = StringUtils.hasText(settlementAccount) ? settlementAccount.trim() : null;
        String bank = StringUtils.hasText(bankAccount) ? bankAccount.trim() : null;
        if (!StringUtils.hasText(account) && StringUtils.hasText(bank)) {
            account = bank;
            if (!StringUtils.hasText(normalizedType)) {
                normalizedType = SettlementAccountType.BANK_CARD.getCode();
            }
        }
        if (!StringUtils.hasText(account)) {
            return;
        }
        if (!StringUtils.hasText(normalizedType)) {
            normalizedType = SettlementAccountType.BANK_CARD.getCode();
        }
        switch (normalizedType) {
            case "bank_card" -> {
                if (!ValidationUtils.isValidBankCard(account)) {
                    throw new BusinessException(ErrorCode.PARAM_FORMAT_ERROR, "银行卡号格式不正确");
                }
            }
            case "alipay" -> {
                if (!ValidationUtils.isValidPhone(account) && !ValidationUtils.isValidEmail(account)) {
                    throw new BusinessException(ErrorCode.PARAM_FORMAT_ERROR, "支付宝账户格式不正确，应为手机号或邮箱");
                }
            }
            case "wechat", "other" -> {
                if (account.length() > 128) {
                    throw new BusinessException(ErrorCode.PARAM_FORMAT_ERROR, "收款账户长度不能超过128个字符");
                }
            }
            default -> throw new BusinessException(ErrorCode.PARAM_FORMAT_ERROR, "不支持的收款账户类型: " + settlementAccountType);
        }
    }

    private void validateFinancialDataForUpdate(Employee existingEmployee, Employee updateInfo) {
        if (!hasFinancialRoutingUpdate(updateInfo)) {
            return;
        }
        if (settlementTypeChangedWithoutAccount(existingEmployee, updateInfo)) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "修改收款账户类型时请同时提交新的收款账号");
        }
        validateSettlementAccountTypeIfPresent(updateInfo.getSettlementAccountType());
        if (!StringUtils.hasText(updateInfo.getSettlementAccount())
                && !StringUtils.hasText(updateInfo.getBankAccount())) {
            return;
        }
        FinancialUpdateSnapshot snapshot = buildFinancialUpdateSnapshot(existingEmployee, updateInfo);
        validateFinancialData(snapshot.settlementAccountType(), snapshot.settlementAccount(), snapshot.bankAccount());
    }

    private boolean hasFinancialRoutingUpdate(Employee updateInfo) {
        return StringUtils.hasText(updateInfo.getSettlementAccountType())
                || StringUtils.hasText(updateInfo.getSettlementAccount())
                || StringUtils.hasText(updateInfo.getBankAccount());
    }

    private boolean settlementTypeChangedWithoutAccount(Employee existingEmployee, Employee updateInfo) {
        if (!StringUtils.hasText(updateInfo.getSettlementAccountType())) {
            return false;
        }
        String newType = normalizeSettlementAccountType(updateInfo.getSettlementAccountType());
        String oldType = resolveSettlementType(existingEmployee);
        if (!StringUtils.hasText(newType) || !StringUtils.hasText(oldType) || newType.equals(oldType)) {
            return false;
        }
        return !StringUtils.hasText(updateInfo.getSettlementAccount())
                && !StringUtils.hasText(updateInfo.getBankAccount());
    }

    private void validateSettlementAccountTypeIfPresent(String settlementAccountType) {
        String normalizedType = normalizeSettlementAccountType(settlementAccountType);
        if (StringUtils.hasText(normalizedType) && SettlementAccountType.fromCode(normalizedType) == null) {
            throw new BusinessException(ErrorCode.PARAM_FORMAT_ERROR, "不支持的收款账户类型: " + settlementAccountType);
        }
    }

    private FinancialUpdateSnapshot buildFinancialUpdateSnapshot(Employee existingEmployee, Employee updateInfo) {
        String settlementType = StringUtils.hasText(updateInfo.getSettlementAccountType())
                ? updateInfo.getSettlementAccountType()
                : resolveSettlementType(existingEmployee);

        if (StringUtils.hasText(updateInfo.getBankAccount())
                && !StringUtils.hasText(updateInfo.getSettlementAccount())
                && !StringUtils.hasText(updateInfo.getSettlementAccountType())) {
            String bankAccount = updateInfo.getBankAccount().trim();
            return new FinancialUpdateSnapshot(SettlementAccountType.BANK_CARD.getCode(), bankAccount, bankAccount);
        }

        String normalizedType = normalizeSettlementAccountType(settlementType);
        if (StringUtils.hasText(updateInfo.getBankAccount())
                && !StringUtils.hasText(updateInfo.getSettlementAccount())
                && SettlementAccountType.fromCode(normalizedType) != null
                && !SettlementAccountType.BANK_CARD.getCode().equals(normalizedType)) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "非银行卡收款类型请提交收款账号");
        }
        String settlementAccount = StringUtils.hasText(updateInfo.getSettlementAccount())
                ? updateInfo.getSettlementAccount().trim()
                : resolveSettlementAccountFromBankAccount(updateInfo, normalizedType);
        String bankAccount = StringUtils.hasText(updateInfo.getBankAccount())
                ? updateInfo.getBankAccount().trim()
                : null;

        return new FinancialUpdateSnapshot(settlementType, settlementAccount, bankAccount);
    }

    private String resolveSettlementAccountFromBankAccount(Employee updateInfo, String normalizedType) {
        if (SettlementAccountType.BANK_CARD.getCode().equals(normalizedType)
                && StringUtils.hasText(updateInfo.getBankAccount())) {
            return updateInfo.getBankAccount().trim();
        }
        return null;
    }

    private String normalizeSettlementAccountType(String settlementAccountType) {
        if (!StringUtils.hasText(settlementAccountType)) {
            return null;
        }
        String normalized = settlementAccountType.trim().toLowerCase();
        return switch (normalized) {
            case "bank", "bankcard", "bank_card" -> SettlementAccountType.BANK_CARD.getCode();
            case "alipay" -> SettlementAccountType.ALIPAY.getCode();
            case "wechat", "weixin", "wx" -> SettlementAccountType.WECHAT.getCode();
            case "other" -> SettlementAccountType.OTHER.getCode();
            default -> normalized;
        };
    }

    private String resolveSettlementType(Employee employee) {
        if (employee == null) {
            return null;
        }
        if (StringUtils.hasText(employee.getSettlementAccountType())) {
            return normalizeSettlementAccountType(employee.getSettlementAccountType());
        }
        if (StringUtils.hasText(employee.getSettlementAccount()) || StringUtils.hasText(employee.getBankAccount())) {
            return SettlementAccountType.BANK_CARD.getCode();
        }
        return null;
    }

    private record FinancialUpdateSnapshot(String settlementAccountType, String settlementAccount, String bankAccount) {
    }

    private Map<Long, ExternalIdentity> buildPrimaryIdentityMap(List<Employee> employees) {
        if (employees == null || employees.isEmpty()) {
            return Map.of();
        }
        List<Long> employeeIds = employees.stream()
                .map(Employee::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (employeeIds.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<ExternalIdentity> wrapper = new QueryWrapper<>();
        wrapper.select("id", "employee_id", "provider", "subject_id", "last_seen_at");
        wrapper.in("employee_id", employeeIds);
        wrapper.eq("status", ExternalIdentityService.STATUS_ACTIVE);
        wrapper.orderByDesc("is_primary", "last_seen_at", "id");
        List<ExternalIdentity> identities = externalIdentityService.list(wrapper);
        Map<Long, ExternalIdentity> map = new HashMap<>();
        for (ExternalIdentity identity : identities) {
            if (identity.getEmployeeId() != null) {
                map.putIfAbsent(identity.getEmployeeId(), identity);
            }
        }
        return map;
    }

    private String translatePlatformName(String platformType) {
        if (!StringUtils.hasText(platformType)) {
            return null;
        }
        return switch (platformType.trim().toLowerCase()) {
            case "wechat" -> "企业微信";
            case "dingtalk" -> "钉钉";
            case "feishu" -> "飞书";
            default -> platformType;
        };
    }

    private LambdaQueryWrapper<Employee> buildQueryWrapper(String keyword, String department, String status,
                                                           Boolean isOffline, String provider, Long managerId,
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
        if (StringUtils.hasText(status)) {
            queryWrapper.eq(Employee::getStatus, parseEmployeeStatus(status).getCode());
        }
        if (isOffline != null) queryWrapper.eq(Employee::getOffline, isOffline);
        if (StringUtils.hasText(provider)) {
            String normalizedPlatform = normalizePlatform(provider);
            List<Long> employeeIds = externalIdentityService.list(new LambdaQueryWrapper<ExternalIdentity>()
                    .select(ExternalIdentity::getEmployeeId)
                    .eq(ExternalIdentity::getProvider, normalizedPlatform)
                    .eq(ExternalIdentity::getStatus, ExternalIdentityService.STATUS_ACTIVE)
                    .isNotNull(ExternalIdentity::getEmployeeId))
                    .stream()
                    .map(ExternalIdentity::getEmployeeId)
                    .distinct()
                    .toList();
            if (employeeIds.isEmpty()) {
                queryWrapper.eq(Employee::getId, -1L);
            } else {
                queryWrapper.in(Employee::getId, employeeIds);
            }
        }
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

    private EmployeeStatus parseEmployeeStatus(String status) {
        try {
            return EmployeeStatus.fromCode(status.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "无效的员工状态: " + status);
        }
    }

    private int safePage(int pageNum) {
        return pageNum < 1 ? 1 : pageNum;
    }

    private int safeSize(int pageSize) {
        if (pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
