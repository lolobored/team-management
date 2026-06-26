package org.lolobored.tm.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthorizationMatrixTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void health_anonymous_ok() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void getData_anonymous_unauthorized() throws Exception {
        mockMvc.perform(get("/api/team-members")).andExpect(status().isUnauthorized());
    }

    @Test
    void getData_viewRole_ok() throws Exception {
        mockMvc.perform(get("/api/team-members").with(user("v@x.com").roles("VIEW")))
                .andExpect(status().isOk());
    }

    @Test
    void writeData_viewRole_forbidden() throws Exception {
        mockMvc.perform(post("/api/team-members").with(user("v@x.com").roles("VIEW")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\",\"lastName\":\"B\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void writeData_viewWriteRole_allowed() throws Exception {
        mockMvc.perform(post("/api/team-members").with(user("w@x.com").roles("VIEW_WRITE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\",\"lastName\":\"B\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void users_viewWriteRole_forbidden() throws Exception {
        mockMvc.perform(get("/api/users").with(user("w@x.com").roles("VIEW_WRITE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void users_adminRole_allowed() throws Exception {
        mockMvc.perform(get("/api/users").with(user("a@x.com").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanReadData_viaHierarchy() throws Exception {
        mockMvc.perform(get("/api/team-members").with(user("a@x.com").roles("ADMIN")))
                .andExpect(status().isOk());
    }
}
