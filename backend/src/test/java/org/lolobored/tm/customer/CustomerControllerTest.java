package org.lolobored.tm.customer;

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
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CustomerControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void listCustomers_empty() throws Exception {
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void createAndGetCustomer() throws Exception {
        String json = """
                {"name": "Acme Corp", "country": "Australia", "city": "Melbourne"}
                """;
        String response = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Acme Corp"))
                .andExpect(jsonPath("$.country").value("Australia"))
                .andExpect(jsonPath("$.city").value("Melbourne"))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(response).get("id").asLong();
        mockMvc.perform(get("/api/customers/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Corp"));
    }

    @Test
    void updateCustomer() throws Exception {
        String json = """
                {"name": "Acme Corp"}
                """;
        String response = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(response).get("id").asLong();
        String updateJson = """
                {"name": "Acme Holdings", "country": "New Zealand"}
                """;
        mockMvc.perform(put("/api/customers/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Holdings"))
                .andExpect(jsonPath("$.country").value("New Zealand"));
    }

    @Test
    void deleteCustomer() throws Exception {
        String json = """
                {"name": "Acme Corp"}
                """;
        String response = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(response).get("id").asLong();
        mockMvc.perform(delete("/api/customers/" + id))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/customers/" + id))
                .andExpect(status().isNotFound());
    }
}
