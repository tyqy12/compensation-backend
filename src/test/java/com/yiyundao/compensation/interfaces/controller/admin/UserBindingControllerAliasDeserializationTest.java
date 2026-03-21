package com.yiyundao.compensation.interfaces.controller.admin;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserBindingControllerAliasDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void shouldDeserializeBindingFormWithProviderAndSubjectId() throws Exception {
        String json = """
                {
                  "provider": "wechat",
                  "subjectId": "wx_user_9527"
                }
                """;

        UserBindingController.BindingForm form = objectMapper.readValue(json, UserBindingController.BindingForm.class);

        assertEquals("wechat", form.getProvider());
        assertEquals("wx_user_9527", form.getSubjectId());
    }

    @Test
    void shouldIgnoreBindingFormWithLegacyPlatformFields() throws Exception {
        String json = """
                {
                  "platformType": "dingtalk",
                  "platformUserId": "ding_user_9527"
                }
                """;

        UserBindingController.BindingForm form = objectMapper.readValue(json, UserBindingController.BindingForm.class);

        assertNull(form.getProvider());
        assertNull(form.getSubjectId());
        assertEquals("dingtalk", form.getLegacyPlatformType());
        assertEquals("ding_user_9527", form.getLegacyPlatformUserId());
    }
}
