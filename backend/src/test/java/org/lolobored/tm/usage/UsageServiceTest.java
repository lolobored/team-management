package org.lolobored.tm.usage;

import org.lolobored.tm.teammember.TeamMember;
import org.lolobored.tm.teammember.TeamMemberRepository;
import org.lolobored.tm.assignment.Assignment;
import org.lolobored.tm.assignment.AssignmentRepository;
import org.lolobored.tm.customer.Customer;
import org.lolobored.tm.customer.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class UsageServiceTest {

    @Autowired private UsageService usageService;
    @Autowired private TeamMemberRepository teamMemberRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AssignmentRepository assignmentRepository;

    private Long teamMemberId;
    private Long customerId;

    @BeforeEach
    void setUp() {
        assignmentRepository.deleteAll();
        customerRepository.deleteAll();
        teamMemberRepository.deleteAll();

        TeamMember teamMember = new TeamMember();
        teamMember.setFirstName("Alice");
        teamMember.setLastName("Smith");
        teamMember.setCountry("Australia");
        teamMemberId = teamMemberRepository.save(teamMember).getId();

        Customer customer = new Customer();
        customer.setName("Acme");
        customerId = customerRepository.save(customer).getId();

        Assignment a1 = new Assignment();
        a1.setTeamMemberId(teamMemberId);
        a1.setCustomerId(customerId);
        a1.setUsagePercent(25);
        a1.setStatus(org.lolobored.tm.assignment.AssignmentStatus.CONFIRMED);
        a1.setMonth("2026-06");
        assignmentRepository.save(a1);

        Assignment a1b = new Assignment();
        a1b.setTeamMemberId(teamMemberId);
        a1b.setCustomerId(customerId);
        a1b.setUsagePercent(25);
        a1b.setStatus(org.lolobored.tm.assignment.AssignmentStatus.CONFIRMED);
        a1b.setMonth("2026-07");
        assignmentRepository.save(a1b);
    }

    @Test
    void computeUsage_perMonthAssignments() {
        YearMonth from = YearMonth.of(2026, 6);
        YearMonth to = YearMonth.of(2026, 7);

        List<TeamMemberUsageDto> result = usageService.computeUsage(from, to, null, null);

        assertEquals(1, result.size());
        TeamMemberUsageDto dto = result.get(0);
        assertEquals("Alice Smith", dto.teamMemberName());
        assertEquals("Australia", dto.country());

        MonthUsageDto june = dto.months().get(YearMonth.of(2026, 6));
        assertNotNull(june);
        assertEquals(25, june.total());
        assertEquals(1, june.assignments().size());
        assertEquals(org.lolobored.tm.assignment.AssignmentStatus.CONFIRMED, june.assignments().get(0).status());

        MonthUsageDto july = dto.months().get(YearMonth.of(2026, 7));
        assertNotNull(july);
        assertEquals(25, july.total());
    }

    @Test
    void computeUsage_monthOutsideRange_excluded() {
        YearMonth from = YearMonth.of(2026, 8);
        YearMonth to = YearMonth.of(2026, 8);

        List<TeamMemberUsageDto> result = usageService.computeUsage(from, to, null, null);

        TeamMemberUsageDto dto = result.get(0);
        MonthUsageDto aug = dto.months().get(YearMonth.of(2026, 8));
        assertNotNull(aug);
        assertEquals(0, aug.total());
        assertTrue(aug.assignments().isEmpty());
    }

    @Test
    void computeUsage_overlapMonth_sumsUsage() {
        Customer c = new Customer();
        c.setName("Beta");
        Long cId = customerRepository.save(c).getId();

        Assignment a = new Assignment();
        a.setTeamMemberId(teamMemberId);
        a.setCustomerId(cId);
        a.setUsagePercent(30);
        a.setStatus(org.lolobored.tm.assignment.AssignmentStatus.CONFIRMED);
        a.setMonth("2026-07");
        assignmentRepository.save(a);

        YearMonth from = YearMonth.of(2026, 7);
        YearMonth to = YearMonth.of(2026, 7);

        List<TeamMemberUsageDto> result = usageService.computeUsage(from, to, null, null);
        MonthUsageDto july = result.get(0).months().get(YearMonth.of(2026, 7));

        assertEquals(55, july.total());
        assertEquals(2, july.assignments().size());
    }

    @Test
    void computeUsage_filterByCountry() {
        TeamMember nzTeamMember = new TeamMember();
        nzTeamMember.setFirstName("Bob");
        nzTeamMember.setLastName("Jones");
        nzTeamMember.setCountry("New Zealand");
        teamMemberRepository.save(nzTeamMember);

        YearMonth from = YearMonth.of(2026, 6);
        YearMonth to = YearMonth.of(2026, 6);

        List<TeamMemberUsageDto> auOnly = usageService.computeUsage(from, to, "Australia", null);
        assertEquals(1, auOnly.size());
        assertEquals("Alice Smith", auOnly.get(0).teamMemberName());

        List<TeamMemberUsageDto> nzOnly = usageService.computeUsage(from, to, "New Zealand", null);
        assertEquals(1, nzOnly.size());
        assertEquals("Bob Jones", nzOnly.get(0).teamMemberName());
    }
}
