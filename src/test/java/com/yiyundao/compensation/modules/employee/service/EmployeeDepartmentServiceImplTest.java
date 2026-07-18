package com.yiyundao.compensation.modules.employee.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yiyundao.compensation.infrastructure.dao.EmployeeDepartmentMapper;
import com.yiyundao.compensation.modules.employee.entity.EmployeeDepartment;
import com.yiyundao.compensation.modules.employee.service.impl.EmployeeDepartmentServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmployeeDepartmentServiceImplTest {

    @Test
    void replaceDepartmentsShouldOnlyRemoveRelationsFromTheSelectedPlatform() {
        initTableInfo(EmployeeDepartment.class);
        TestableEmployeeDepartmentService service = new TestableEmployeeDepartmentService();

        service.replaceDepartments(7L, "DINGTALK", List.of("技术部/平台研发部", "技术部"));

        assertThat(service.removed).hasSize(1);
        assertThat(service.removed.get(0).getExpression().getNormal().getSqlSegment())
                .contains("employee_id", "platform_type");
        assertThat(service.saved).extracting(EmployeeDepartment::getPlatformType)
                .containsExactly("dingtalk", "dingtalk");
        assertThat(service.saved).extracting(EmployeeDepartment::getDeptName)
                .containsExactly("技术部", "平台研发部");
        assertThat(service.saved).extracting(EmployeeDepartment::getPrimary)
                .containsExactly(true, false);
    }

    @Test
    void tableSelectMustNotUseTheMysqlPrimaryReservedWordAsAnAlias() {
        initTableInfo(EmployeeDepartment.class);

        String selectSql = TableInfoHelper.getTableInfo(EmployeeDepartment.class).getAllSqlSelect();

        assertThat(selectSql).contains("is_primary AS primaryFlag");
        assertThat(selectSql).doesNotContain("AS primary,");
    }

    @Test
    void replaceDepartmentsShouldClearOnlyTheSelectedPlatformWhenInputIsEmpty() {
        initTableInfo(EmployeeDepartment.class);
        TestableEmployeeDepartmentService service = new TestableEmployeeDepartmentService();

        service.replaceDepartments(7L, "wechat", List.of());

        assertThat(service.removed).hasSize(1);
        assertThat(service.saved).isEmpty();
    }

    @Test
    void departmentReadsShouldUsePhysicalColumnNamesForPrimaryOrdering() {
        initTableInfo(EmployeeDepartment.class);
        TestableEmployeeDepartmentService service = new TestableEmployeeDepartmentService();
        EmployeeDepartment relation = new EmployeeDepartment();
        relation.setEmployeeId(7L);
        relation.setDeptName("技术部");
        service.relations = List.of(relation);

        assertThat(service.findDepartmentNames(7L)).containsExactly("技术部");
        assertThat(service.findDepartmentNamesByEmployeeIds(List.of(7L)))
                .containsEntry(7L, List.of("技术部"));
        assertThat(service.lastListWrapper.getSqlSegment()).contains("is_primary", "order_num", "id");
    }

    private static void initTableInfo(Class<?> entityType) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, entityType.getName());
        assistant.setCurrentNamespace(entityType.getName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }

    private static class TestableEmployeeDepartmentService extends EmployeeDepartmentServiceImpl {
        private final List<Wrapper<EmployeeDepartment>> removed = new ArrayList<>();
        private final List<EmployeeDepartment> saved = new ArrayList<>();
        private List<EmployeeDepartment> relations = List.of();
        private Wrapper<EmployeeDepartment> lastListWrapper;

        @Override
        public boolean remove(Wrapper<EmployeeDepartment> queryWrapper) {
            removed.add(queryWrapper);
            return true;
        }

        @Override
        public boolean saveBatch(Collection<EmployeeDepartment> entityList) {
            saved.addAll(entityList);
            return true;
        }

        @Override
        public List<EmployeeDepartment> list(Wrapper<EmployeeDepartment> queryWrapper) {
            lastListWrapper = queryWrapper;
            return relations;
        }
    }
}
