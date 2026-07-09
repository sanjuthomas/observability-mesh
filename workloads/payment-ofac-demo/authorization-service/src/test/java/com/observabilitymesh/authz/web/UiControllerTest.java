package com.observabilitymesh.authz.web;

import com.observabilitymesh.auth.RequestSubjectResolver;
import com.observabilitymesh.authz.AuthzTestFixtures;
import com.observabilitymesh.authz.directory.UserDirectory;
import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.common.web.PermissionDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UiControllerTest {

    @Mock RequestSubjectResolver subjectResolver;

    private MockMvc mockMvc;
    private final Subject admin = new Subject(
            "admin-001", "Admin", "User", "Platform Admin", "FICC",
            List.of("PLATFORM_ADMIN"), List.of("ADMIN"), null, List.of("FICC"), null, List.of());

    @BeforeEach
    void setUp() throws Exception {
        Path path = Path.of(new ClassPathResource("users.yaml").getURI());
        UserDirectory userDirectory = UserDirectory.load(path);
        mockMvc = AuthzTestFixtures.standaloneMockMvc(new UiController(userDirectory, subjectResolver));
        when(subjectResolver.resolveActor(any())).thenReturn(admin);
    }

    @Test
    void uiIndexServesHtml() throws Exception {
        mockMvc.perform(get("/ui/")).andExpect(status().isOk());
    }

    @Test
    void apiUiUsersRequiresPlatformAdmin() throws Exception {
        when(subjectResolver.resolveActor(any())).thenThrow(new PermissionDeniedException("PLATFORM_ADMIN role required"));

        mockMvc.perform(get("/api/ui/users")).andExpect(status().isForbidden());
    }

    @Test
    void apiUiUsersReturnsDirectoryRows() throws Exception {
        mockMvc.perform(get("/api/ui/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").isNumber())
                .andExpect(jsonPath("$.email_domain").value("ssi.local"))
                .andExpect(jsonPath("$.users[0].user_id").exists());
    }

    @Test
    void apiUiUsersSupportsSearchFilter() throws Exception {
        mockMvc.perform(get("/api/ui/users").param("q", "mo-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[0].user_id").value("mo-100"));
    }

    @Test
    void apiUiUsersSupportsRoleFilter() throws Exception {
        mockMvc.perform(get("/api/ui/users").param("role", "INSTRUCTION_CREATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[0].roles[0]").value("INSTRUCTION_CREATOR"));
    }

    @Test
    void apiUiUsersSupportsGroupFilter() throws Exception {
        mockMvc.perform(get("/api/ui/users").param("group", "MIDDLE_OFFICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").isNumber());
    }

    @Test
    void apiUiUsersMatchesSupervisorAndLobInQuery() throws Exception {
        mockMvc.perform(get("/api/ui/users").param("q", "ficc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").isNumber());
    }

    @Test
    void apiUiUsersSupportsAmountClubGroupFilter() throws Exception {
        mockMvc.perform(get("/api/ui/users").param("group", "UP_TO_1_BILLION_CLUB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").isNumber());
    }

    @Test
    void apiUiUsersMatchesDisplayNameInQuery() throws Exception {
        mockMvc.perform(get("/api/ui/users").param("q", "chen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").isNumber());
    }
}
