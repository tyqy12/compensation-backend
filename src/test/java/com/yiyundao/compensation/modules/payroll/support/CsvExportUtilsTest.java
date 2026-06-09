package com.yiyundao.compensation.modules.payroll.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvExportUtilsTest {

    @Test
    void escapeCellShouldQuoteCommasQuotesAndLineBreaks() {
        assertThat(CsvExportUtils.escapeCell("Finance, \"HQ\"\nNorth"))
                .isEqualTo("\"Finance, \"\"HQ\"\"\nNorth\"");
    }

    @Test
    void escapeCellShouldProtectFormulaLikeValues() {
        assertThat(CsvExportUtils.escapeCell("=cmd|calc")).isEqualTo("'=cmd|calc");
        assertThat(CsvExportUtils.escapeCell(" +SUM(A1:A2)")).isEqualTo(" '+SUM(A1:A2)");
        assertThat(CsvExportUtils.escapeCell("-10")).isEqualTo("'-10");
        assertThat(CsvExportUtils.escapeCell("@name")).isEqualTo("'@name");
    }
}
