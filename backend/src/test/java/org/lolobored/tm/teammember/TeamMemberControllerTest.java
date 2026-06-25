package org.lolobored.tm.teammember;

import com.fasterxml.jackson.databind.ObjectMapper;
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
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TeamMemberControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void listTeamMembers_empty() throws Exception {
        mockMvc.perform(get("/api/team-members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void createAndGetTeamMember() throws Exception {
        String json = """
                {"firstName": "Alice", "lastName": "Smith", "email": "alice@example.com", "country": "Australia", "city": "Sydney"}
                """;

        String response = mockMvc.perform(post("/api/team-members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.lastName").value("Smith"))
                .andExpect(jsonPath("$.country").value("Australia"))
                .andExpect(jsonPath("$.city").value("Sydney"))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/team-members/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.lastName").value("Smith"));
    }

    @Test
    void updateTeamMember() throws Exception {
        String json = """
                {"firstName": "Alice", "lastName": "Smith", "country": "Australia"}
                """;
        String response = mockMvc.perform(post("/api/team-members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        String updateJson = """
                {"firstName": "Alice", "lastName": "Updated", "country": "New Zealand"}
                """;
        mockMvc.perform(put("/api/team-members/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.lastName").value("Updated"))
                .andExpect(jsonPath("$.country").value("New Zealand"));
    }

    @Test
    void deleteTeamMember() throws Exception {
        String json = """
                {"firstName": "Alice", "lastName": "Smith"}
                """;
        String response = mockMvc.perform(post("/api/team-members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(delete("/api/team-members/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/team-members/" + id))
                .andExpect(status().isNotFound());
    }
}
