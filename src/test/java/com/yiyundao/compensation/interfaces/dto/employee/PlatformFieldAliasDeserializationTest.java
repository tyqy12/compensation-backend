package com.yiyundao.compensation.interfaces.dto.employee;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.modules.employee.dto.BindPlatformRequest;
import com.yiyundao.compensation.interfaces.dto.org.OrgImportRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlatformFieldAliasDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void shouldDeserializeBindPlatformRequestWithProviderAndSubjectId() throws Exception {
        String json = """
                {
                  "provider": "wechat",
                  "subjectId": "wx_user_001",
                  "forceBind": true
                }
                """;

        BindPlatformRequest request = objectMapper.readValue(json, BindPlatformRequest.class);

        assertEquals("wechat", request.getProvider());
        assertEquals("wx_user_001", request.getSubjectId());
    }

    @Test
    void shouldDeserializeEmployeeCreateRequestWithProviderAndSubjectId() throws Exception {
        String json = """
                {
                  "employeeId": "E1001",
                  "name": "张三",
                  "provider": "dingtalk",
                  "subjectId": "ding_user_1001"
                }
                """;

        EmployeeCreateRequest request = objectMapper.readValue(json, EmployeeCreateRequest.class);

        assertEquals("dingtalk", request.getProvider());
        assertEquals("ding_user_1001", request.getSubjectId());
    }

    @Test
    void shouldDeserializeOrgImportRequestEmployeeItemWithProviderAndSubjectId() throws Exception {
        String json = """
                {
                  "provider": "feishu",
                  "items": [
                    {
                      "employeeId": "E2001",
                      "name": "李四",
                      "provider": "feishu",
                      "subjectId": "fs_user_2001"
                    }
                  ]
                }
                """;

        OrgImportRequest request = objectMapper.readValue(json, OrgImportRequest.class);

        assertEquals("feishu", request.getProvider());
        assertEquals("feishu", request.getItems().get(0).getProvider());
        assertEquals("fs_user_2001", request.getItems().get(0).getSubjectId());
    }

    @Test
    void shouldIgnoreLegacyPlatformFieldsForWriteDtos() throws Exception {
        String employeeJson = """
                {
                  "employeeId": "E1002",
                  "name": "王五",
                  "platformType": "wechat",
                  "platformUserId": "wx_legacy_1002"
                }
                """;
        EmployeeCreateRequest employeeCreateRequest = objectMapper.readValue(employeeJson, EmployeeCreateRequest.class);
        assertNull(employeeCreateRequest.getProvider());
        assertNull(employeeCreateRequest.getSubjectId());
        assertEquals("wechat", employeeCreateRequest.getLegacyPlatformType());
        assertEquals("wx_legacy_1002", employeeCreateRequest.getLegacyPlatformUserId());

        String bindJson = """
                {
                  "platformType": "dingtalk",
                  "platformUserId": "ding_legacy_2002"
                }
                """;
        BindPlatformRequest bindPlatformRequest = objectMapper.readValue(bindJson, BindPlatformRequest.class);
        assertNull(bindPlatformRequest.getProvider());
        assertNull(bindPlatformRequest.getSubjectId());
        assertEquals("dingtalk", bindPlatformRequest.getLegacyPlatformType());
        assertEquals("ding_legacy_2002", bindPlatformRequest.getLegacyPlatformUserId());

        String orgJson = """
                {
                  "platformType": "wechat",
                  "items": [
                    {
                      "employeeId": "E3001",
                      "name": "赵六",
                      "platformType": "wechat",
                      "platformUserId": "wx_legacy_3001"
                    }
                  ]
                }
                """;
        OrgImportRequest orgImportRequest = objectMapper.readValue(orgJson, OrgImportRequest.class);
        assertNull(orgImportRequest.getProvider());
        assertNull(orgImportRequest.getItems().get(0).getProvider());
        assertNull(orgImportRequest.getItems().get(0).getSubjectId());
        assertEquals("wechat", orgImportRequest.getLegacyPlatformType());
        assertEquals("wechat", orgImportRequest.getItems().get(0).getLegacyPlatformType());
        assertEquals("wx_legacy_3001", orgImportRequest.getItems().get(0).getLegacyPlatformUserId());
    }
}
