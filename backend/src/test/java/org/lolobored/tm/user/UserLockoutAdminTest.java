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
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UserLockoutAdminTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;

    private Long seedLocked(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode("temp"));
        u.setRole(Role.VIEW);
        u.setEnabled(true);
        u.setMustChangePassword(false);
        u.setCreatedAt(Instant.now());
        u.setFailedAttempts(5);
        u.setLockedUntil(Instant.now().plus(15, ChronoUnit.MINUTES));
        return users.save(u).getId();
    }

    @BeforeEach
    void clean() { users.deleteAll(); }

    @Test
    void unlock_clearsLockAndCounter() throws Exception {
        Long id = seedLocked("u@example.com");
        mockMvc.perform(post("/api/users/" + id + "/unlock")
                        .with(user("admin@example.com").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockedUntil").doesNotExist());
        User after = users.findById(id).orElseThrow();
        assertThat(after.getFailedAttempts()).isZero();
        assertThat(after.getLockedUntil()).isNull();
    }

    @Test
    void unlock_isAdminOnly() throws Exception {
        Long id = seedLocked("u@example.com");
        mockMvc.perform(post("/api/users/" + id + "/unlock")
                        .with(user("w@example.com").roles("VIEW_WRITE")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void resetPassword_clearsLock() throws Exception {
        Long id = seedLocked("u@example.com");
        mockMvc.perform(post("/api/users/" + id + "/reset-password")
                        .with(user("admin@example.com").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initialPassword\":\"fresh1\"}"))
                .andExpect(status().isNoContent());
        User after = users.findById(id).orElseThrow();
        assertThat(after.getLockedUntil()).isNull();
        assertThat(after.getFailedAttempts()).isZero();
    }

    @Test
    void userDto_exposesLockedUntil() throws Exception {
        seedLocked("u@example.com");
        mockMvc.perform(get("/api/users").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lockedUntil").exists());
    }
}
