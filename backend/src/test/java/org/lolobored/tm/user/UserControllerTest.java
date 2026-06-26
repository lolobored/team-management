package org.lolobored.tm.user;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UserControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;
    @Autowired private ObjectMapper objectMapper;

    private Long seedAdmin(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode("TempPass1234"));
        u.setRole(Role.ADMIN);
        u.setEnabled(true);
        u.setMustChangePassword(false);
        u.setCreatedAt(Instant.now());
        return users.save(u).getId();
    }

    @BeforeEach
    void clean() { users.deleteAll(); }

    @Test
    void create_thenList() throws Exception {
        seedAdmin("admin@example.com");
        mockMvc.perform(post("/api/users").with(user("admin@example.com").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"role\":\"VIEW\",\"initialPassword\":\"temp1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.role").value("VIEW"))
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        mockMvc.perform(get("/api/users").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void create_duplicateEmail_returns409() throws Exception {
        seedAdmin("admin@example.com");
        String body = "{\"email\":\"dup@example.com\",\"role\":\"VIEW\",\"initialPassword\":\"temp1\"}";
        mockMvc.perform(post("/api/users").with(user("admin@example.com").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/users").with(user("admin@example.com").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void create_blankPassword_returns400() throws Exception {
        seedAdmin("admin@example.com");
        mockMvc.perform(post("/api/users").with(user("admin@example.com").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@example.com\",\"role\":\"VIEW\",\"initialPassword\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_setsMustChange() throws Exception {
        seedAdmin("admin@example.com");
        User target = new User();
        target.setEmail("t@example.com");
        target.setPasswordHash(encoder.encode("old"));
        target.setRole(Role.VIEW);
        target.setEnabled(true);
        target.setMustChangePassword(false);
        target.setCreatedAt(Instant.now());
        Long id = users.save(target).getId();

        mockMvc.perform(post("/api/users/" + id + "/reset-password")
                        .with(user("admin@example.com").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initialPassword\":\"fresh1\"}"))
                .andExpect(status().isNoContent());
        User after = users.findById(id).orElseThrow();
        assertThat(after.isMustChangePassword()).isTrue();
        assertThat(encoder.matches("fresh1", after.getPasswordHash())).isTrue();
    }

    @Test
    void changeRole_updates() throws Exception {
        seedAdmin("admin@example.com");
        User t = new User();
        t.setEmail("r@example.com"); t.setPasswordHash("x"); t.setRole(Role.VIEW);
        t.setEnabled(true); t.setMustChangePassword(false); t.setCreatedAt(Instant.now());
        Long id = users.save(t).getId();
        mockMvc.perform(patch("/api/users/" + id + "/role")
                        .with(user("admin@example.com").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"VIEW_WRITE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("VIEW_WRITE"));
    }

    @Test
    void cannotDeleteOwnAccount() throws Exception {
        Long adminId = seedAdmin("admin@example.com");
        mockMvc.perform(delete("/api/users/" + adminId)
                        .with(user("admin@example.com").roles("ADMIN")).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void cannotDisableOwnAccount() throws Exception {
        Long adminId = seedAdmin("admin@example.com");
        mockMvc.perform(patch("/api/users/" + adminId + "/enabled")
                        .with(user("admin@example.com").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isConflict());
    }

    @Test
    void cannotDemoteOwnAccount() throws Exception {
        Long adminId = seedAdmin("admin@example.com");
        mockMvc.perform(patch("/api/users/" + adminId + "/role")
                        .with(user("admin@example.com").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"VIEW\"}"))
                .andExpect(status().isConflict());
    }
}
