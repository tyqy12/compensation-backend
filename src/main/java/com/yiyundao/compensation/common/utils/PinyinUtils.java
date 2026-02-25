package com.yiyundao.compensation.common.utils;

import net.sourceforge.pinyin4j.PinyinHelper;

public final class PinyinUtils {
    private PinyinUtils() {}

    // Convert Chinese name to simple lowercase pinyin (no tone), remove spaces and non a-z chars.
    public static String toPinyinSlug(String name) {
        if (name == null || name.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : name.trim().toCharArray()) {
            if (isChinese(c)) {
                String[] arr = PinyinHelper.toHanyuPinyinStringArray(c);
                if (arr != null && arr.length > 0) {
                    // take first reading, drop tone digits
                    String py = arr[0].replaceAll("[0-9]", "");
                    sb.append(py);
                }
            } else if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        // keep only a-z0-9
        return sb.toString().replaceAll("[^a-z0-9]", "");
    }

    private static boolean isChinese(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_E
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.GENERAL_PUNCTUATION
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }
}

