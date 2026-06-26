package org.lolobored.tm.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MustChangePasswordLockdownTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;

    @BeforeEach
    void seedFlaggedUser() {
        users.deleteAll();
        User u = new User();
        u.setEmail("flagged@example.com");
        u.setPasswordHash(encoder.encode("TempPass1234"));
        u.setRole(Role.VIEW_WRITE);
        u.setEnabled(true);
        u.setMustChangePassword(true);
        u.setCreatedAt(Instant.now());
        users.save(u);
    }

    @Test
    void flaggedUser_blockedFromDataEndpoints() throws Exception {
        mockMvc.perform(get("/api/team-members").with(user("flagged@example.com").roles("VIEW_WRITE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void flaggedUser_allowedOnMe() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(user("flagged@example.com").roles("VIEW_WRITE")))
                .andExpect(status().isOk());
    }
}
