package com.yiyundao.compensation.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yiyundao.compensation.modules.rbac.entity.SysResource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SysResourceMapper extends BaseMapper<SysResource> {

    /**
     * 批量更新排序号
     *
     * @param items 排序项列表 (id 和 orderNum)
     * @return 更新的记录数
     */
    @Update("<script>" +
            "UPDATE sys_resource SET order_num = CASE id " +
            "<foreach collection='items' item='item' separator=' '>" +
            "WHEN #{item.id} THEN #{item.orderNum} " +
            "</foreach>" +
            "END WHERE id IN " +
            "<foreach collection='items' item='item' open='(' separator=',' close=')'>#{item.id}</foreach>" +
            "</script>")
    int batchUpdateOrderNum(@Param("items") List<SortItemDto> items);

    /**
     * 排序项 DTO
     */
    record SortItemDto(Long id, Integer orderNum) {}
}

