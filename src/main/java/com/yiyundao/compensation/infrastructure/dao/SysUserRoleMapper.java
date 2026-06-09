package com.yiyundao.compensation.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yiyundao.compensation.modules.rbac.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    @Update("""
            UPDATE sys_user_role
            SET deleted = 0,
                granted_by = #{grantedBy},
                granted_at = #{grantedAt},
                expires_at = #{expiresAt,jdbcType=TIMESTAMP},
                remarks = #{remarks,jdbcType=VARCHAR},
                update_by = #{updateBy},
                update_time = #{updateTime},
                delete_by = NULL,
                delete_time = NULL
            WHERE user_id = #{userId}
              AND role_id = #{roleId}
              AND deleted = 1
            """)
    int restoreDeletedRole(@Param("userId") Long userId,
                           @Param("roleId") Long roleId,
                           @Param("grantedBy") Long grantedBy,
                           @Param("grantedAt") LocalDateTime grantedAt,
                           @Param("expiresAt") LocalDateTime expiresAt,
                           @Param("remarks") String remarks,
                           @Param("updateBy") String updateBy,
                           @Param("updateTime") LocalDateTime updateTime);
}
