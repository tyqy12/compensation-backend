package com.yiyundao.compensation.interfaces.dto.role;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoleResourceAssignRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldValidateNestedResourceAssignmentResourceId() {
        RoleResourceAssignRequest request = new RoleResourceAssignRequest();
        RoleResourceAssignRequest.ResourceAssignment assignment =
                new RoleResourceAssignRequest.ResourceAssignment();
        assignment.setActions(List.of("read"));
        request.setResources(List.of(assignment));

        var violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).contains("resources[0].resourceId");
                    assertThat(violation.getMessage()).isEqualTo("资源ID不能为空");
                });
    }
}
