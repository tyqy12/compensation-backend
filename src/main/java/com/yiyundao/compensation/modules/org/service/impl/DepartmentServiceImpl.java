package com.yiyundao.compensation.modules.org.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.infrastructure.dao.DepartmentMapper;
import com.yiyundao.compensation.modules.org.entity.Department;
import com.yiyundao.compensation.modules.org.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl extends ServiceImpl<DepartmentMapper, Department> implements DepartmentService {

    @Override
    @Transactional
    public void upsert(String platformType, String platformDeptId, String name, String parentPlatformDeptId, Integer orderNum) {
        Department exist = getOne(new LambdaQueryWrapper<Department>()
                .eq(Department::getPlatformType, platformType)
                .eq(Department::getPlatformDeptId, String.valueOf(platformDeptId))
                .last("limit 1"));
        if (exist == null) {
            Department d = new Department();
            d.setPlatformType(platformType);
            d.setPlatformDeptId(String.valueOf(platformDeptId));
            d.setName(name);
            d.setParentPlatformDeptId(parentPlatformDeptId == null ? null : String.valueOf(parentPlatformDeptId));
            d.setOrderNum(orderNum);
            save(d);
        } else {
            boolean changed = false;
            if (name != null && !name.equals(exist.getName())) { exist.setName(name); changed = true; }
            String parentStr = parentPlatformDeptId == null ? null : String.valueOf(parentPlatformDeptId);
            if ((parentStr != null && !parentStr.equals(exist.getParentPlatformDeptId())) || (parentStr == null && exist.getParentPlatformDeptId() != null)) {
                exist.setParentPlatformDeptId(parentStr);
                changed = true;
            }
            if (orderNum != null && (exist.getOrderNum() == null || !orderNum.equals(exist.getOrderNum()))) { exist.setOrderNum(orderNum); changed = true; }
            if (changed) updateById(exist);
        }
    }

    @Override
    public java.util.List<com.yiyundao.compensation.dto.org.DepartmentNodeDto> getTree(String platformType) {
        java.util.List<Department> list = list(new LambdaQueryWrapper<Department>()
                .eq(Department::getPlatformType, platformType)
                .orderByAsc(Department::getOrderNum)
                .orderByAsc(Department::getId));
        java.util.Map<String, com.yiyundao.compensation.dto.org.DepartmentNodeDto> map = new java.util.HashMap<>();
        for (Department d : list) {
            com.yiyundao.compensation.dto.org.DepartmentNodeDto node = new com.yiyundao.compensation.dto.org.DepartmentNodeDto();
            node.setId(d.getId());
            node.setPlatformType(d.getPlatformType());
            node.setPlatformDeptId(d.getPlatformDeptId());
            node.setParentPlatformDeptId(d.getParentPlatformDeptId());
            node.setName(d.getName());
            map.put(d.getPlatformDeptId(), node);
        }
        java.util.List<com.yiyundao.compensation.dto.org.DepartmentNodeDto> roots = new java.util.ArrayList<>();
        for (Department d : list) {
            com.yiyundao.compensation.dto.org.DepartmentNodeDto node = map.get(d.getPlatformDeptId());
            String parentPid = d.getParentPlatformDeptId();
            if (parentPid == null || !map.containsKey(parentPid)) {
                roots.add(node);
            } else {
                map.get(parentPid).getChildren().add(node);
            }
        }
        return roots;
    }
}
