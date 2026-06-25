package org.lolobored.tm.usage;

import org.lolobored.tm.assignment.AssignmentStatus;

public record AssignmentUsageDto(
        Long assignmentId, Long customerId, String customerName,
        int usage, AssignmentStatus status
) {}
