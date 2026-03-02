package com.yiyundao.compensation.modules.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyundao.compensation.enums.EmploymentType;
import com.yiyundao.compensation.infrastructure.dao.EmployeeTypeProviderMappingMapper;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.payment.entity.EmployeeTypeProviderMapping;
import com.yiyundao.compensation.modules.payment.entity.SettlementProviderConfig;
import com.yiyundao.compensation.modules.payment.service.SettlementProviderConfigService;
import com.yiyundao.compensation.modules.payment.service.SettlementProviderRoutingService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 结算渠道路由服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementProviderRoutingServiceImpl implements SettlementProviderRoutingService {

    private final EmployeeTypeProviderMappingMapper mappingMapper;
    private final SettlementProviderConfigService configService;

    @Override
    public String determineProvider(Employee employee, PayrollBatch batch) {
        if (employee == null) {
            throw new IllegalArgumentException("员工信息不能为空");
        }

        // 优先级1：员工配置的渠道
        if (StringUtils.hasText(employee.getSettlementProviderCode())) {
            String providerCode = employee.getSettlementProviderCode();
            if (isProviderEnabled(providerCode)) {
                log.debug("使用员工配置的渠道: employeeId={}, provider={}", employee.getId(), providerCode);
                return providerCode;
            } else {
                log.warn("员工配置的渠道已禁用: employeeId={}, provider={}", employee.getId(), providerCode);
            }
        }

        // 优先级2：批次配置的渠道
        if (batch != null && StringUtils.hasText(batch.getSettlementProviderCode())) {
            String providerCode = batch.getSettlementProviderCode();
            if (isProviderEnabled(providerCode)) {
                log.debug("使用批次配置的渠道: batchId={}, provider={}", batch.getId(), providerCode);
                return providerCode;
            } else {
                log.warn("批次配置的渠道已禁用: batchId={}, provider={}", batch.getId(), providerCode);
            }
        }

        // 优先级3：根据员工类型映射
        EmploymentType employmentType = EmploymentType.fromCode(employee.getEmploymentType());
        if (employmentType == null) {
            throw new IllegalStateException(
                String.format("员工用工类型无效，无法路由: employeeId=%s, employmentType=%s",
                    employee.getId(), employee.getEmploymentType()));
        }
        List<EmployeeTypeProviderMapping> mappings = getMappingsByEmploymentType(employmentType);
        
        for (EmployeeTypeProviderMapping mapping : mappings) {
            if (mapping.getEnabled() && isProviderEnabled(mapping.getProviderCode())) {
                log.debug("使用员工类型映射的渠道: employeeId={}, type={}, provider={}", 
                    employee.getId(), employmentType, mapping.getProviderCode());
                return mapping.getProviderCode();
            }
        }

        // 如果没有找到合适的渠道，抛出异常
        throw new IllegalStateException(
            String.format("无法为员工确定结算渠道: employeeId=%s, type=%s",
                employee.getId(), employmentType));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EmployeeTypeProviderMapping createMapping(EmploymentType employmentType, String providerCode, Integer priority) {
        // 验证渠道是否存在
        SettlementProviderConfig config = configService.getConfigByCode(providerCode);
        if (config == null) {
            throw new IllegalArgumentException("渠道不存在: " + providerCode);
        }

        EmployeeTypeProviderMapping mapping = new EmployeeTypeProviderMapping();
        mapping.setEmploymentType(employmentType);
        mapping.setProviderCode(providerCode);
        mapping.setPriority(priority != null ? priority : 100);
        mapping.setEnabled(true);

        mappingMapper.insert(mapping);
        log.info("创建员工类型映射: type={}, provider={}, priority={}",
            employmentType, providerCode, mapping.getPriority());
        return mapping;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EmployeeTypeProviderMapping updateMapping(Long id, String providerCode, Integer priority) {
        EmployeeTypeProviderMapping mapping = mappingMapper.selectById(id);
        if (mapping == null) {
            throw new IllegalArgumentException("映射不存在: " + id);
        }

        // 验证渠道是否存在
        SettlementProviderConfig config = configService.getConfigByCode(providerCode);
        if (config == null) {
            throw new IllegalArgumentException("渠道不存在: " + providerCode);
        }

        mapping.setProviderCode(providerCode);
        if (priority != null) {
            mapping.setPriority(priority);
        }

        mappingMapper.updateById(mapping);
        log.info("更新员工类型映射: id={}, type={}, provider={}, priority={}",
            id, mapping.getEmploymentType(), providerCode, mapping.getPriority());
        return mapping;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMapping(Long id) {
        EmployeeTypeProviderMapping mapping = mappingMapper.selectById(id);
        if (mapping == null) {
            throw new IllegalArgumentException("映射不存在: " + id);
        }

        mappingMapper.deleteById(id);
        log.info("删除员工类型映射: id={}, type={}, provider={}", 
            id, mapping.getEmploymentType(), mapping.getProviderCode());
    }

    @Override
    public List<EmployeeTypeProviderMapping> getMappingsByEmploymentType(EmploymentType employmentType) {
        LambdaQueryWrapper<EmployeeTypeProviderMapping> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmployeeTypeProviderMapping::getEmploymentType, employmentType)
               .eq(EmployeeTypeProviderMapping::getEnabled, true)
               .orderByDesc(EmployeeTypeProviderMapping::getPriority);
        return mappingMapper.selectList(wrapper);
    }

    @Override
    public List<EmployeeTypeProviderMapping> getAllMappings() {
        LambdaQueryWrapper<EmployeeTypeProviderMapping> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(EmployeeTypeProviderMapping::getEmploymentType)
               .orderByDesc(EmployeeTypeProviderMapping::getPriority);
        return mappingMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleMappingStatus(Long id, Boolean enabled) {
        EmployeeTypeProviderMapping mapping = mappingMapper.selectById(id);
        if (mapping == null) {
            throw new IllegalArgumentException("映射不存在: " + id);
        }

        mapping.setEnabled(enabled);
        mappingMapper.updateById(mapping);
        log.info("切换映射状态: id={}, type={}, provider={}, enabled={}",
            id, mapping.getEmploymentType(), mapping.getProviderCode(), enabled);
    }

    /**
     * 检查渠道是否启用
     */
    private boolean isProviderEnabled(String providerCode) {
        SettlementProviderConfig config = configService.getConfigByCode(providerCode);
        return config != null && config.getEnabled();
    }
}
