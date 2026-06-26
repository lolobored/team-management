package org.lolobored.tm.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.lolobored.tm.teammember.TeamMember;
import org.lolobored.tm.teammember.TeamMemberRepository;
import org.lolobored.tm.customer.Customer;
import org.lolobored.tm.customer.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AssignmentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TeamMemberRepository teamMemberRepository;
    @Autowired private CustomerRepository customerRepository;

    private Long teamMemberId;
    private Long customerId;

    @BeforeEach
    void setUp() {
        TeamMember teamMember = new TeamMember();
        teamMember.setFirstName("Alice");
        teamMember.setLastName("Smith");
        teamMember.setCountry("Australia");
        teamMemberId = teamMemberRepository.save(teamMember).getId();

        Customer customer = new Customer();
        customer.setName("Acme");
        customerId = customerRepository.save(customer).getId();
    }

    @Test
    void createAndGetAssignment() throws Exception {
        String json = """
                {"teamMemberId": %d, "customerId": %d, "usagePercent": 25, "status": "CONFIRMED", "month": "2026-06"}
                """.formatted(teamMemberId, customerId);

        String response = mockMvc.perform(post("/api/assignments")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teamMemberId").value(teamMemberId))
                .andExpect(jsonPath("$.customerId").value(customerId))
                .andExpect(jsonPath("$.usagePercent").value(25))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();
        mockMvc.perform(get("/api/assignments/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usagePercent").value(25));
    }

    @Test
    void listAssignments_filterByTeamMember() throws Exception {
        String json = """
                {"teamMemberId": %d, "customerId": %d, "usagePercent": 20, "status": "CONFIRMED", "month": "2026-06"}
                """.formatted(teamMemberId, customerId);
        mockMvc.perform(post("/api/assignments").contentType(MediaType.APPLICATION_JSON).content(json));

        mockMvc.perform(get("/api/assignments?teamMemberId=" + teamMemberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/assignments?teamMemberId=9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void deleteAssignment() throws Exception {
        String json = """
                {"teamMemberId": %d, "customerId": %d, "usagePercent": 20, "status": "CONFIRMED", "month": "2026-06"}
                """.formatted(teamMemberId, customerId);

        String response = mockMvc.perform(post("/api/assignments")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();
        mockMvc.perform(delete("/api/assignments/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/assignments/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void patchAssignment_updatesUsageOnly() throws Exception {
        String json = """
                {"teamMemberId": %d, "customerId": %d, "usagePercent": 25, "status": "CONFIRMED", "month": "2026-06"}
                """.formatted(teamMemberId, customerId);

        String response = mockMvc.perform(post("/api/assignments")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(patch("/api/assignments/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usagePercent\": 5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usagePercent").value(5))
                .andExpect(jsonPath("$.teamMemberId").value(teamMemberId))
                .andExpect(jsonPath("$.customerId").value(customerId))
                .andExpect(jsonPath("$.month").value("2026-06"));
    }

    @Test
    void createAssignment_duplicateMonthReturns409() throws Exception {
        String json = """
                {"teamMemberId": %d, "customerId": %d, "usagePercent": 20, "status": "CONFIRMED", "month": "2026-06"}
                """.formatted(teamMemberId, customerId);

        mockMvc.perform(post("/api/assignments")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/assignments")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict());
    }

    @Test
    void createDefaultsToConfirmedWhenStatusOmitted() throws Exception {
        String body = """
                {"teamMemberId": %d, "customerId": %d, "usagePercent": 25, "month": "2026-06"}
                """.formatted(teamMemberId, customerId);
        mockMvc.perform(post("/api/assignments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void patchUpdatesStatus() throws Exception {
        String body = """
                {"teamMemberId": %d, "customerId": %d, "usagePercent": 25, "status": "CONFIRMED", "month": "2026-06"}
                """.formatted(teamMemberId, customerId);
        String created = mockMvc.perform(post("/api/assignments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(patch("/api/assignments/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"PROBABLE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROBABLE"));
    }

    @Test
    void patchRejectsInvalidStatus() throws Exception {
        String body = """
                {"teamMemberId": %d, "customerId": %d, "usagePercent": 25, "status": "CONFIRMED", "month": "2026-06"}
                """.formatted(teamMemberId, customerId);
        String created = mockMvc.perform(post("/api/assignments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(patch("/api/assignments/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"BOGUS\"}"))
                .andExpect(status().isBadRequest());
    }
}
