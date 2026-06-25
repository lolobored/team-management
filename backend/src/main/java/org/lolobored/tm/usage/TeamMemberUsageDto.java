package org.lolobored.tm.usage;

import java.time.YearMonth;
import java.util.Map;

public record TeamMemberUsageDto(
        Long teamMemberId, String teamMemberName, String country,
        Map<YearMonth, MonthUsageDto> months
) {}
