package com.yiyundao.compensation.modules.rbac.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.modules.rbac.entity.SysResource;

import java.util.List;
import java.util.Map;

public interface ResourceService extends IService<SysResource> {
    List<SysResource> getResourceTree(String type);
    List<SysResource> getUserResources(Long userId);
    Map<Long, List<String>> getUserActions(Long userId);

    /**
     * 批量更新资源排序号
     *
     * @param items 排序项列表 (id 和 orderNum)
     */
    void batchUpdateOrderNum(List<SortItem> items);

    /**
     * 排序项
     */
    record SortItem(Long id, Integer orderNum) {}
}

