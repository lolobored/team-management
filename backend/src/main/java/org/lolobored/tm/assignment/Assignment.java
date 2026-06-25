package org.lolobored.tm.assignment;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_assignment_team_member_customer_month",
        columnNames = {"team_member_id", "customer_id", "month"}))
public class Assignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "team_member_id", nullable = false)
    private Long teamMemberId;

    @NotNull
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @NotNull
    @Column(name = "usage_percent", nullable = false)
    private int usagePercent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssignmentStatus status = AssignmentStatus.CONFIRMED;

    @NotNull
    @Column(nullable = false, length = 7)
    private String month;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTeamMemberId() { return teamMemberId; }
    public void setTeamMemberId(Long teamMemberId) { this.teamMemberId = teamMemberId; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public int getUsagePercent() { return usagePercent; }
    public void setUsagePercent(int usagePercent) { this.usagePercent = usagePercent; }
    public AssignmentStatus getStatus() { return status; }
    public void setStatus(AssignmentStatus status) { this.status = status; }
    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }
}
