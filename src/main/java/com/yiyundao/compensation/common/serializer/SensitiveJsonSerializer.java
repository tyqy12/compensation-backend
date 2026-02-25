package com.yiyundao.compensation.common.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.yiyundao.compensation.common.annotation.Sensitive;
import com.yiyundao.compensation.common.annotation.SensitiveType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class SensitiveJsonSerializer extends JsonSerializer<String> {

    @Value("${security.sensitive.enabled:true}")
    private boolean enabled;

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        try {
            var context = gen.getOutputContext();
            if (context != null && context.getCurrentValue() != null) {
                var field = context.getCurrentValue().getClass()
                        .getDeclaredField(context.getCurrentName());
                Sensitive sensitive = field.getAnnotation(Sensitive.class);
                
                if (sensitive != null && sensitive.enabled() && !enabled) {
                    gen.writeString(value);
                    return;
                }

                if (sensitive != null && value != null) {
                    SensitiveType type = sensitive.value();
                    String desensitized = type.desensitize(value);
                    gen.writeString(desensitized);
                    return;
                }
            }
        } catch (Exception e) {
            log.debug("获取脱敏注解失败: {}", e.getMessage());
        }
        gen.writeString(value);
    }
}
