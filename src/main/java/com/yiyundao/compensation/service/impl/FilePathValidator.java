package com.yiyundao.compensation.service.impl;

final class FilePathValidator {

    private static final int MAX_CATEGORY_LENGTH = 128;
    private static final int MAX_FILE_NAME_LENGTH = 160;
    private static final int MAX_FILE_KEY_LENGTH = 512;
    private static final java.util.regex.Pattern SAFE_SEGMENT =
            java.util.regex.Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]*");
    private static final java.util.regex.Pattern SAFE_FILE_NAME =
            java.util.regex.Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private FilePathValidator() {
    }

    static void validateCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category 不能为空");
        }
        if (category.length() > MAX_CATEGORY_LENGTH || !isSafeSegmentPath(category, false)) {
            throw new IllegalArgumentException("非法 category");
        }
    }

    static void validateCustomFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return;
        }
        if (fileName.length() > MAX_FILE_NAME_LENGTH
                || !SAFE_FILE_NAME.matcher(fileName).matches()
                || ".".equals(fileName)
                || fileName.contains("..")) {
            throw new IllegalArgumentException("非法 fileName");
        }
    }

    static void validateFileKey(String fileKey) {
        if (fileKey == null || fileKey.isBlank()) {
            throw new IllegalArgumentException("fileKey 不能为空");
        }
        if (fileKey.length() > MAX_FILE_KEY_LENGTH || !isSafeSegmentPath(fileKey, true)) {
            throw new IllegalArgumentException("非法 fileKey");
        }
    }

    private static boolean isSafeSegmentPath(String value, boolean allowFileNameInLastSegment) {
        if (value.startsWith("/") || value.endsWith("/") || value.contains("\\") || value.contains("..")) {
            return false;
        }
        String[] segments = value.split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.isBlank() || ".".equals(segment)) {
                return false;
            }
            boolean last = i == segments.length - 1;
            if (allowFileNameInLastSegment && last) {
                if (!SAFE_FILE_NAME.matcher(segment).matches()) {
                    return false;
                }
            } else if (!SAFE_SEGMENT.matcher(segment).matches()) {
                return false;
            }
        }
        return true;
    }
}
