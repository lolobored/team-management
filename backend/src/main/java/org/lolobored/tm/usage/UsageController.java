package org.lolobored.tm.usage;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/usage")
public class UsageController {
    private final UsageService usageService;
    private final UsageExportService usageExportService;

    public UsageController(UsageService usageService, UsageExportService usageExportService) {
        this.usageService = usageService;
        this.usageExportService = usageExportService;
    }

    @GetMapping
    public List<TeamMemberUsageDto> getUsage(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Long teamMemberId) {
        try {
            return usageService.computeUsage(
                    YearMonth.parse(from), YearMonth.parse(to), country, teamMemberId);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format. Use YYYY-MM.");
        }
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Long teamMemberId) {
        try {
            YearMonth fromYm = YearMonth.parse(from);
            YearMonth toYm = YearMonth.parse(to);
            List<TeamMemberUsageDto> data = usageService.computeUsage(fromYm, toYm, country, teamMemberId);
            byte[] excel = usageExportService.exportToExcel(data, fromYm, toYm);

            String filename = "usage-" + from + "-to-" + to + ".xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excel);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format. Use YYYY-MM.");
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate Excel file.");
        }
    }
}
