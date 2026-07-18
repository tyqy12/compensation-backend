package com.yiyundao.compensation.modules.employee.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("employee_department")
public class EmployeeDepartment extends BaseEntity {
    @TableField("employee_id")
    private Long employeeId;

    @TableField("platform_type")
    private String platformType; // wechat/dingtalk/feishu

    @TableField("platform_dept_id")
    private String platformDeptId; // 可选

    @TableField("local_dept_id")
    private Long localDeptId; // 可选，关联 org_department.id

    @TableField("dept_name")
    private String deptName;

    @TableField("is_primary")
    private Boolean primaryFlag; // 是否主部门

    @TableField("order_num")
    private Integer orderNum; // 展示顺序

    /**
     * 业务层保留 primary 语义；持久化属性使用 primaryFlag，避免 MyBatis-Plus 生成保留字 SQL 别名。
     */
    public Boolean getPrimary() {
        return primaryFlag;
    }

    public void setPrimary(Boolean primary) {
        this.primaryFlag = primary;
    }
}
