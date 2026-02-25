package com.yiyundao.compensation.common.utils;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

@Component
public class ValidationUtils {
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("^\\d{17}[\\dXx]$");
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile("^\\d{16,19}$");

    public static boolean isValidPhone(String phone) {
        return phone != null && PHONE_PATTERN.matcher(phone).matches();
    }

    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    public static boolean isValidIdCard(String idCard) {
        return idCard != null && ID_CARD_PATTERN.matcher(idCard).matches();
    }

    public static boolean isValidBankCard(String bankCard) {
        return bankCard != null && BANK_CARD_PATTERN.matcher(bankCard).matches();
    }

    public static boolean isValidDate(String dateStr) {
        if (dateStr == null) return false;
        try {
            LocalDate.parse(dateStr, DateTimeFormatter.ISO_DATE);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isValidDateTime(String dateTimeStr) {
        if (dateTimeStr == null) return false;
        try {
            LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_DATE_TIME);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
