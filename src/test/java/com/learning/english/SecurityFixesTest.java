package com.learning.english;

import com.learning.english.dto.AiSettingsDto;
import com.learning.english.model.User;
import com.learning.english.model.UserAiSettings;
import com.learning.english.service.AiSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
public class SecurityFixesTest {

    @Autowired
    private AiSettingsService aiSettingsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Test
    void testSsrfProtection_BlocksMaliciousUrls() {
        // Attack 1: Subdomain extension bypass (prefix match exploit)
        assertThrows(IllegalArgumentException.class, () ->
                aiSettingsService.validateBaseUrlAllowList("https://api.openai.com.attacker.com/v1")
        );

        // Attack 2: User-info @ confusion bypass (URI parser confusion)
        assertThrows(IllegalArgumentException.class, () ->
                aiSettingsService.validateBaseUrlAllowList("https://api.openai.com@attacker.com/anything")
        );

        // Attack 3: Cloud metadata IP (AWS/GCP/Azure)
        assertThrows(IllegalArgumentException.class, () ->
                aiSettingsService.validateBaseUrlAllowList("http://169.254.169.254/latest/meta-data")
        );

        // Attack 4: Internal port scanning on localhost
        assertThrows(IllegalArgumentException.class, () ->
                aiSettingsService.validateBaseUrlAllowList("http://localhost:5432")
        );
        assertThrows(IllegalArgumentException.class, () ->
                aiSettingsService.validateBaseUrlAllowList("http://127.0.0.1:22")
        );

        // Attack 5: Protocol confusion
        assertThrows(IllegalArgumentException.class, () ->
                aiSettingsService.validateBaseUrlAllowList("file:///etc/passwd")
        );
        assertThrows(IllegalArgumentException.class, () ->
                aiSettingsService.validateBaseUrlAllowList("gopher://127.0.0.1:6379")
        );
    }

    @Test
    void testSsrfProtection_AllowsLegitimateUrls() {
        assertDoesNotThrow(() ->
                aiSettingsService.validateBaseUrlAllowList("http://localhost:20128/v1")
        );
        assertDoesNotThrow(() ->
                aiSettingsService.validateBaseUrlAllowList("https://api.deepseek.com")
        );
        assertDoesNotThrow(() ->
                aiSettingsService.validateBaseUrlAllowList("https://generativelanguage.googleapis.com")
        );
        assertDoesNotThrow(() ->
                aiSettingsService.validateBaseUrlAllowList("https://api.openai.com/v1")
        );
    }

    @Test
    void testApiKeyMasking_DoesNotLeakPrefixOrSuffix() throws Exception {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("test_user")
                .email("test@example.com")
                .password("hash")
                .build();

        UserAiSettings settings = UserAiSettings.builder()
                .user(user)
                .encryptedApiKey("someEncryptedValue")
                .build();

        Method toSafeDtoMethod = AiSettingsService.class.getDeclaredMethod("toSafeDto", UserAiSettings.class);
        toSafeDtoMethod.setAccessible(true);
        AiSettingsDto dto = (AiSettingsDto) toSafeDtoMethod.invoke(aiSettingsService, settings);

        // If decrypted key is null or masked, hint should either be null or "••••••••••••"
        // Under no circumstances should hint reveal cleartext characters like prefix or suffix
        if (dto.apiKeyHint() != null) {
            assertEquals("••••••••••••", dto.apiKeyHint());
            assertFalse(dto.apiKeyHint().contains("sk-"));
            assertFalse(dto.apiKeyHint().contains("..."));
        }
    }

    @Test
    void testStrictPasswordMatching() {
        String encoded = passwordEncoder.encode("user1");

        // Exact match succeeds
        assertTrue(passwordEncoder.matches("user1", encoded));

        // Case-sensitive enforcement: incorrect case fails
        assertFalse(passwordEncoder.matches("User1", encoded));
        assertFalse(passwordEncoder.matches("USER1", encoded));

        // Variations with space fail
        assertFalse(passwordEncoder.matches("user 1", encoded));
    }

    @Test
    void testSwaggerUi_RestrictedFromAnonymousAccess() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        // Anonymous access to Swagger UI must NOT be permitted (should return 401 Unauthorized or 403 Forbidden)
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403, "Expected 401 or 403 but got: " + status);
                });

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403, "Expected 401 or 403 but got: " + status);
                });
    }
}
