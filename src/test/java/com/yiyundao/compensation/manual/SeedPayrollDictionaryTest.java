package com.yiyundao.compensation.manual;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yiyundao.compensation.infrastructure.dao.SysResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserResourceMapper;
import com.yiyundao.compensation.modules.payroll.entity.SalaryItem;
import com.yiyundao.compensation.modules.payroll.service.SalaryItemService;
import com.yiyundao.compensation.modules.rbac.entity.SysResource;
import com.yiyundao.compensation.modules.rbac.entity.SysUserResource;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StringUtils;

@SpringBootTest
@ActiveProfiles("dev")
@Disabled("Manual seeding utility for UAT")
class SeedPayrollDictionaryTest {

    @Autowired
    private SalaryItemService salaryItemService;
    @Autowired
    private SysUserService sysUserService;
    @Autowired
    private SysUserResourceMapper userResourceMapper;
    @Autowired
    private SysResourceMapper sysResourceMapper;

    @Test
    void seedBasicSalaryItems() {
        ensureItem("BASE", "基本工资", "earning", true, true, 1);
        ensureItem("BONUS", "绩效奖金", "earning", true, true, 2);
        ensureItem("DEDUCT", "个税扣款", "deduction", false, true, 3);
        ensureItem("SOCIAL", "社保扣款", "deduction", false, true, 4);
        ensureRole("00124", "ROLE_EMPLOYEE");
        ensureRole("yuyu", "ROLE_EMPLOYEE");
        ensureRole("yuyu", "ROLE_MANAGER");
        ensureRole("pengjunhua", "ROLE_EMPLOYEE");
        ensureRole("pengjunhua", "ROLE_FINANCE");
        ensureResource(4L, 35L); // submit approval
        ensureResource(4L, 34L); // lock batch
        ensureResource(4L, 36L); // dry-run
        ensureResource(4L, 37L); // compute
        ensureResource(4L, 33L); // update batch
        bumpPermissionVersion(4L);
        ensureApprovalAccess(2L);
        ensureApprovalAccess(4L);
    }

    private void ensureItem(String code, String name, String type, boolean taxable, boolean showOnPayslip, int orderNum) {
        SalaryItem existing = salaryItemService.lambdaQuery()
                .eq(SalaryItem::getCode, code)
                .one();
        if (existing != null) {
            if (!"enabled".equalsIgnoreCase(existing.getStatus())) {
                existing.setStatus("enabled");
                existing.setName(name);
                existing.setType(type);
                existing.setTaxable(taxable);
                existing.setShowOnPayslip(showOnPayslip);
                existing.setOrderNum(orderNum);
                salaryItemService.updateById(existing);
            }
            return;
        }
        SalaryItem item = new SalaryItem();
        item.setCode(code);
        item.setName(name);
        item.setType(type);
        item.setTaxable(taxable);
        item.setShowOnPayslip(showOnPayslip);
        item.setOrderNum(orderNum);
        item.setStatus("enabled");
        salaryItemService.save(item);
    }

    private void ensureRole(String username, String role) {
        SysUser user = sysUserService.findByUsername(username);
        if (user == null) {
            return;
        }
        String roles = user.getRoles();
        if (roles != null && roles.contains(role)) {
            return;
        }
        String updatedRoles = roles == null || roles.isBlank() ? role : roles + "," + role;
        LambdaUpdateWrapper<SysUser> update = new LambdaUpdateWrapper<>();
        update.eq(SysUser::getId, user.getId())
                .set(SysUser::getRoles, updatedRoles);
        sysUserService.update(update);
    }

    private void ensureResource(Long userId, Long resourceId) {
        if (userId == null || resourceId == null) {
            return;
        }
        Long count = userResourceMapper.selectCount(new LambdaQueryWrapper<SysUserResource>()
                .eq(SysUserResource::getUserId, userId)
                .eq(SysUserResource::getResourceId, resourceId));
        if (count != null && count > 0) {
            return;
        }
        SysUserResource rel = new SysUserResource();
        rel.setUserId(userId);
        rel.setResourceId(resourceId);
        userResourceMapper.insert(rel);
    }

    private void bumpPermissionVersion(Long userId) {
        if (userId == null) {
            return;
        }
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            return;
        }
        Integer current = user.getPermissionVersion();
        int next = (current == null ? 0 : current) + 1;
        LambdaUpdateWrapper<SysUser> update = new LambdaUpdateWrapper<>();
        update.eq(SysUser::getId, userId)
                .set(SysUser::getPermissionVersion, next);
        sysUserService.update(update);
    }

    private void ensureApprovalAccess(Long userId) {
        assignResourceByPath(userId, "/api/approval/workflows/*/approve");
        assignResourceByPath(userId, "/api/approval/workflows/*/reject");
    }

    private void assignResourceByPath(Long userId, String path) {
        if (userId == null || !StringUtils.hasText(path)) {
            return;
        }
        SysResource resource = sysResourceMapper.selectOne(new LambdaQueryWrapper<SysResource>()
                .eq(SysResource::getPath, path)
                .eq(SysResource::getStatus, "enabled")
                .last("limit 1"));
        if (resource == null || resource.getId() == null) {
            return;
        }
        ensureResource(userId, resource.getId());
    }
}
