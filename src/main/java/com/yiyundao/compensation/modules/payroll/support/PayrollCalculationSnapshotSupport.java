package com.yiyundao.compensation.modules.payroll.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollImportItem;
import com.yiyundao.compensation.modules.payroll.entity.SalaryTemplate;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 生成薪资计算所依赖事实的稳定摘要。
 * <p>
 * 摘要不包含数据库主键、时间戳等存储噪声，避免同一业务输入因重新导入而产生不同版本。
 * </p>
 */
public final class PayrollCalculationSnapshotSupport {

    public static final String LEGACY_BASIC_ENGINE_VERSION = "legacy-basic-v1";

    private PayrollCalculationSnapshotSupport() {
    }

    public static String inputHash(ObjectMapper objectMapper, List<PayrollImportItem> items) {
        return hashJson(inputSnapshotJson(objectMapper, items));
    }

    public static String inputSnapshotJson(ObjectMapper objectMapper, List<PayrollImportItem> items) {
        return writeJson(objectMapper, inputSnapshot(items));
    }

    public static String ruleHash(ObjectMapper objectMapper, SalaryTemplate template) {
        String snapshotJson = ruleSnapshotJson(objectMapper, template);
        return snapshotJson == null ? null : hashJson(snapshotJson);
    }

    public static String ruleSnapshotJson(ObjectMapper objectMapper, SalaryTemplate template) {
        if (template == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("templateId", template.getId());
        snapshot.put("type", template.getType());
        snapshot.put("dataVersion", template.getDataVersion());
        snapshot.put("itemsJson", template.getItemsJson());
        snapshot.put("taxRuleJson", template.getTaxRuleJson());
        snapshot.put("status", template.getStatus());
        return writeJson(objectMapper, snapshot);
    }

    private static List<Map<String, Object>> inputSnapshot(List<PayrollImportItem> items) {
        List<Map<String, Object>> snapshot = new ArrayList<>();
        if (items != null) {
            items.stream()
                    .filter(item -> item != null)
                    .sorted(Comparator
                            .comparing(PayrollImportItem::getEmployeeId, Comparator.nullsFirst(Comparator.naturalOrder()))
                            .thenComparing(PayrollImportItem::getItemCode, Comparator.nullsFirst(Comparator.naturalOrder()))
                            .thenComparing(PayrollImportItem::getRowNo, Comparator.nullsFirst(Comparator.naturalOrder()))
                            .thenComparing(PayrollImportItem::getSourceName, Comparator.nullsFirst(Comparator.naturalOrder()))
                            .thenComparing(item -> normalizeAmount(item.getAmount()),
                                    Comparator.nullsFirst(Comparator.naturalOrder()))
                            .thenComparing(PayrollImportItem::getNote, Comparator.nullsFirst(Comparator.naturalOrder()))
                            .thenComparing(PayrollImportItem::getStatus, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .forEach(item -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("employeeId", item.getEmployeeId());
                        row.put("itemCode", item.getItemCode());
                        row.put("amount", normalizeAmount(item.getAmount()));
                        row.put("note", item.getNote());
                        row.put("sourceName", item.getSourceName());
                        row.put("rowNo", item.getRowNo());
                        row.put("status", item.getStatus());
                        snapshot.add(row);
                    });
        }
        return snapshot;
    }

    private static String normalizeAmount(BigDecimal amount) {
        return amount == null ? null : amount.stripTrailingZeros().toPlainString();
    }

    private static String writeJson(ObjectMapper objectMapper, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("生成薪资计算快照失败", e);
        }
    }

    private static String hashJson(String json) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
