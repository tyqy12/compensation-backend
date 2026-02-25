package com.yiyundao.compensation.common.encrypt;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * 敏感数据 JSON 序列化器
 * <p>
 * 在 JSON 序列化时自动脱敏敏感字段。
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-10
 */
@Slf4j
public class SensitiveJsonSerializer extends JsonSerializer<String> {

    /**
     * 脱敏类型
     */
    private final SensitiveType type;

    /**
     * 调试模式
     */
    private final boolean debugMode;

    public SensitiveJsonSerializer() {
        this(SensitiveType.DEFAULT, false);
    }

    public SensitiveJsonSerializer(SensitiveType type) {
        this(type, false);
    }

    public SensitiveJsonSerializer(SensitiveType type, boolean debugMode) {
        this.type = type;
        this.debugMode = debugMode;
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }

        if (debugMode) {
            // 调试模式不脱敏
            gen.writeString(value);
            return;
        }

        String desensitized = type.desensitize(value);
        gen.writeString(desensitized);
    }
}
