package com.yiyundao.compensation.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yiyundao.compensation.modules.audit.dto.AuditLogQueryRequest;
import com.yiyundao.compensation.modules.audit.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    /**
     * 根据用户名查询最近操作记录
     */
    @Select("SELECT * FROM audit_log WHERE username = #{username} ORDER BY create_time DESC LIMIT #{limit}")
    List<AuditLog> findRecentByUsername(@Param("username") String username, @Param("limit") int limit);

    /**
     * 统计用户登录失败次数
     */
    @Select("SELECT COUNT(*) FROM audit_log WHERE username = #{username} " +
            "AND operation LIKE CONCAT('%', '登录', '%') " +
            "AND response_result = 'FAILED' " +
            "AND create_time >= #{startTime}")
    int countLoginFailures(@Param("username") String username, @Param("startTime") LocalDateTime startTime);

    /**
     * 获取今日登录统计
     */
    @Select("SELECT response_result, COUNT(*) as count FROM audit_log " +
            "WHERE operation LIKE CONCAT('%', '登录', '%') " +
            "AND DATE(create_time) = CURDATE() " +
            "GROUP BY response_result")
    List<Map<String, Object>> getTodayLoginStats();

    /**
     * 获取操作类型统计
     */
    @Select("SELECT operation, COUNT(*) as count FROM audit_log " +
            "WHERE create_time >= #{startTime} AND create_time <= #{endTime} " +
            "GROUP BY operation ORDER BY count DESC")
    List<Map<String, Object>> getOperationStats(@Param("startTime") LocalDateTime startTime,
                                                @Param("endTime") LocalDateTime endTime);

    /**
     * 根据时间范围查询
     */
    List<AuditLog> findByTimeRange(@Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime);

    /**
     * 分页查询
     */
    List<AuditLog> queryByPage(@Param("request") AuditLogQueryRequest request);
}

