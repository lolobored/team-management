package org.lolobored.tm.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ChangePasswordTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;

    @BeforeEach
    void seedFlaggedUser() {
        users.deleteAll();
        User u = new User();
        u.setEmail("u@example.com");
        u.setPasswordHash(encoder.encode("TempPass1234"));
        u.setRole(Role.VIEW);
        u.setEnabled(true);
        u.setMustChangePassword(true);
        u.setCreatedAt(Instant.now());
        users.save(u);
    }

    @Test
    void validNewPassword_clearsFlag() throws Exception {
        mockMvc.perform(post("/api/auth/change-password").with(user("u@example.com").roles("VIEW")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"TempPass1234\",\"newPassword\":\"NewStrongPass9\"}"))
                .andExpect(status().isNoContent());
        assertThat(users.findByEmail("u@example.com").orElseThrow().isMustChangePassword()).isFalse();
    }

    @Test
    void wrongCurrentPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/change-password").with(user("u@example.com").roles("VIEW")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"nope\",\"newPassword\":\"NewStrongPass9\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void policyViolation_returns422() throws Exception {
        mockMvc.perform(post("/api/auth/change-password").with(user("u@example.com").roles("VIEW")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"TempPass1234\",\"newPassword\":\"short\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
