package com.yiyundao.compensation.modules.payroll.support;

public final class CsvExportUtils {

    private CsvExportUtils() {
    }

    public static void appendRow(StringBuilder target, Object... values) {
        if (target == null) {
            return;
        }
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                target.append(',');
            }
            target.append(escapeCell(values[i]));
        }
        target.append('\n');
    }

    public static String escapeCell(Object value) {
        String text = value == null ? "" : value.toString();
        text = protectFormula(text);
        boolean quote = text.indexOf(',') >= 0
                || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0
                || text.indexOf('\r') >= 0;
        if (!quote) {
            return text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static String protectFormula(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int index = 0;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        if (index >= value.length()) {
            return value;
        }
        char first = value.charAt(index);
        if (first == '=' || first == '+' || first == '-' || first == '@') {
            return value.substring(0, index) + "'" + value.substring(index);
        }
        return value;
    }
}
