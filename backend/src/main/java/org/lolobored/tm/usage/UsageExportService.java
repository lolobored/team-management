package org.lolobored.tm.usage;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class UsageExportService {

    public byte[] exportToExcel(List<TeamMemberUsageDto> usageData,
                                YearMonth from, YearMonth to) throws IOException {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Usage Timeline");

            var headerStyle = createHeaderStyle(workbook);
            var teamMemberStyle = createTeamMemberStyle(workbook);
            var greenStyle = createUsageStyle(workbook, new byte[]{(byte) 220, (byte) 252, (byte) 231});
            var amberStyle = createUsageStyle(workbook, new byte[]{(byte) 254, (byte) 243, (byte) 199});
            var redStyle = createUsageStyle(workbook, new byte[]{(byte) 254, (byte) 202, (byte) 202});
            var defaultCellStyle = createUsageStyle(workbook, null);

            List<YearMonth> months = buildMonthList(from, to);

            Row headerRow = sheet.createRow(0);
            Cell nameHeader = headerRow.createCell(0);
            nameHeader.setCellValue("Team Member");
            nameHeader.setCellStyle(headerStyle);
            Cell countryHeader = headerRow.createCell(1);
            countryHeader.setCellValue("Country");
            countryHeader.setCellStyle(headerStyle);

            for (int i = 0; i < months.size(); i++) {
                Cell cell = headerRow.createCell(i + 2);
                YearMonth ym = months.get(i);
                cell.setCellValue(ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                        + " " + (ym.getYear() % 100));
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (var teamMember : usageData) {
                Row row = sheet.createRow(rowIdx++);

                Cell nameCell = row.createCell(0);
                nameCell.setCellValue(teamMember.teamMemberName());
                nameCell.setCellStyle(teamMemberStyle);

                Cell countryCell = row.createCell(1);
                countryCell.setCellValue(teamMember.country());

                for (int i = 0; i < months.size(); i++) {
                    Cell cell = row.createCell(i + 2);
                    MonthUsageDto monthData = teamMember.months().get(months.get(i));

                    if (monthData == null || monthData.assignments().isEmpty()) {
                        continue;
                    }

                    String content = monthData.assignments().stream()
                            .map(a -> {
                                String prefix = switch (a.status()) {
                                    case PROBABLE -> "(P) ";
                                    case POTENTIAL -> "(T) ";
                                    case CONFIRMED -> "";
                                };
                                return prefix + a.customerName() + " " + a.usage() + "%";
                            })
                            .collect(Collectors.joining("\n"));

                    int total = monthData.total();
                    CellStyle style;
                    if (total >= 50 && total <= 70) {
                        style = greenStyle;
                    } else if (total > 30 && total < 50) {
                        style = amberStyle;
                    } else if (total > 0) {
                        style = redStyle;
                    } else {
                        style = defaultCellStyle;
                    }
                    cell.setCellStyle(style);
                    cell.setCellValue(content);
                }
            }

            sheet.setColumnWidth(0, 25 * 256);
            sheet.setColumnWidth(1, 15 * 256);
            for (int i = 0; i < months.size(); i++) {
                sheet.setColumnWidth(i + 2, 30 * 256);
            }

            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, months.size() + 1));
            sheet.createFreezePane(2, 1);

            try (var out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            }
        }
    }

    private List<YearMonth> buildMonthList(YearMonth from, YearMonth to) {
        List<YearMonth> months = new java.util.ArrayList<>();
        for (YearMonth ym = from; !ym.isAfter(to); ym = ym.plusMonths(1)) {
            months.add(ym);
        }
        return months;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        var style = workbook.createCellStyle();
        var font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle createTeamMemberStyle(Workbook workbook) {
        var style = workbook.createCellStyle();
        var font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        return style;
    }

    private CellStyle createUsageStyle(Workbook workbook, byte[] rgb) {
        var style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        var font = workbook.createFont();
        font.setFontHeightInPoints((short) 9);
        style.setFont(font);
        if (rgb != null) {
            var xssfStyle = (org.apache.poi.xssf.usermodel.XSSFCellStyle) style;
            xssfStyle.setFillForegroundColor(
                    new org.apache.poi.xssf.usermodel.XSSFColor(rgb, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return style;
    }
}
