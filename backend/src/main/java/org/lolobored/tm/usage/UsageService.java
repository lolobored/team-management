package org.lolobored.tm.usage;

import org.lolobored.tm.teammember.TeamMember;
import org.lolobored.tm.teammember.TeamMemberRepository;
import org.lolobored.tm.assignment.Assignment;
import org.lolobored.tm.assignment.AssignmentRepository;
import org.lolobored.tm.customer.Customer;
import org.lolobored.tm.customer.CustomerRepository;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.*;

@Service
public class UsageService {

    private final TeamMemberRepository teamMemberRepository;
    private final AssignmentRepository assignmentRepository;
    private final CustomerRepository customerRepository;

    public UsageService(TeamMemberRepository teamMemberRepository,
                        AssignmentRepository assignmentRepository,
                        CustomerRepository customerRepository) {
        this.teamMemberRepository = teamMemberRepository;
        this.assignmentRepository = assignmentRepository;
        this.customerRepository = customerRepository;
    }

    public List<TeamMemberUsageDto> computeUsage(YearMonth from, YearMonth to,
                                                 String country, Long teamMemberId) {
        List<TeamMember> teamMembers;
        if (teamMemberId != null) {
            teamMembers = teamMemberRepository.findById(teamMemberId)
                    .map(List::of).orElse(List.of());
        } else {
            teamMembers = teamMemberRepository.findAll();
        }

        if (country != null && !country.isBlank()) {
            teamMembers = teamMembers.stream()
                    .filter(a -> country.equals(a.getCountry())).toList();
        }

        Map<Long, Customer> customerCache = new HashMap<>();
        customerRepository.findAll().forEach(c -> customerCache.put(c.getId(), c));

        List<TeamMemberUsageDto> result = new ArrayList<>();

        for (TeamMember teamMember : teamMembers) {
            List<Assignment> assignments = assignmentRepository.findByTeamMemberId(teamMember.getId());
            Map<YearMonth, MonthUsageDto> months = new LinkedHashMap<>();

            for (YearMonth month = from; !month.isAfter(to); month = month.plusMonths(1)) {
                String monthStr = month.toString();
                List<AssignmentUsageDto> monthAssignments = new ArrayList<>();

                for (Assignment assignment : assignments) {
                    if (!monthStr.equals(assignment.getMonth())) continue;

                    Customer customer = customerCache.get(assignment.getCustomerId());
                    String customerName = customer != null ? customer.getName() : "";

                    monthAssignments.add(new AssignmentUsageDto(
                            assignment.getId(), assignment.getCustomerId(), customerName,
                            assignment.getUsagePercent(), assignment.getStatus()));
                }

                monthAssignments.sort(Comparator.comparing(AssignmentUsageDto::customerName));
                int total = monthAssignments.stream().mapToInt(AssignmentUsageDto::usage).sum();
                months.put(month, new MonthUsageDto(total, monthAssignments));
            }

            String teamMemberCountry = teamMember.getCountry() != null ? teamMember.getCountry() : "";
            result.add(new TeamMemberUsageDto(
                    teamMember.getId(), teamMember.getFirstName() + " " + teamMember.getLastName(),
                    teamMemberCountry, months));
        }

        return result;
    }
}
