package org.lolobored.tm.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;
    @Autowired private ObjectMapper objectMapper;

    private void seed(String email, String rawPassword, Role role, boolean enabled, boolean mustChange) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(rawPassword));
        u.setRole(role);
        u.setEnabled(enabled);
        u.setMustChangePassword(mustChange);
        u.setCreatedAt(Instant.now());
        users.save(u);
    }

    @BeforeEach
    void setup() {
        users.deleteAll();
    }

    @Test
    void login_validCredentials_returnsMeAndSetsSession() throws Exception {
        seed("admin@example.com", "TempPass1234", Role.ADMIN, true, true);
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.com\",\"password\":\"TempPass1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void login_badPassword_returns401() throws Exception {
        seed("admin@example.com", "TempPass1234", Role.ADMIN, true, false);
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_disabledUser_returns401() throws Exception {
        seed("dis@example.com", "TempPass1234", Role.VIEW, false, false);
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dis@example.com\",\"password\":\"TempPass1234\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withoutSession_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginThenMe_sharingSession_returnsUser() throws Exception {
        seed("vw@example.com", "TempPass1234", Role.VIEW_WRITE, true, false);
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"vw@example.com\",\"password\":\"TempPass1234\"}"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("VIEW_WRITE"));
    }
}
