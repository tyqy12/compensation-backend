package com.yiyundao.compensation.common.util;

import com.yiyundao.compensation.common.annotation.SensitiveType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SensitiveUtil {

    @Value("${security.sensitive.enabled:true}")
    private boolean enabled;

    public String desensitize(String value, SensitiveType type) {
        if (!enabled) {
            return value;
        }
        if (value == null || value.isEmpty()) {
            return value;
        }
        return type.desensitize(value);
    }

    public String desensitizeIdCard(String idCard) {
        return desensitize(idCard, SensitiveType.ID_CARD);
    }

    public String desensitizePhone(String phone) {
        return desensitize(phone, SensitiveType.PHONE);
    }

    public String desensitizeBankCard(String bankCard) {
        return desensitize(bankCard, SensitiveType.BANK_CARD);
    }

    public String desensitizeName(String name) {
        return desensitize(name, SensitiveType.NAME);
    }

    public String desensitizeEmail(String email) {
        return desensitize(email, SensitiveType.EMAIL);
    }

    public String desensitizeAddress(String address) {
        return desensitize(address, SensitiveType.ADDRESS);
    }
}
