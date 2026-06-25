package org.lolobored.tm.usage;

import java.util.List;

public record MonthUsageDto(int total, List<AssignmentUsageDto> assignments) {}
