package com.basic.JWTSecurity.safety;

import com.basic.JWTSecurity.auth.config.SecurityConfig;
import com.basic.JWTSecurity.auth.security.*;
import com.basic.JWTSecurity.auth.service.ProfileServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@ContextConfiguration(classes=SafetyApiSecurityTest.Config.class)
@TestPropertySource(properties={
    "spring.app.jwtSecret=dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtMTIzNDU2Nzg=",
    "spring.app.jwtExpirationMs=2592000000", "spring.app.adminUsernames=test-admin"})
class SafetyApiSecurityTest {
    @Configuration
    @Import({SafetyApi.class, SecurityConfig.class, JwtUtils.class, AuthEntryPointJwt.class, AccessDeniedHandlerJwt.class})
    static class Config {}
    @Autowired MockMvc mvc;
    @Autowired JwtUtils jwt;
    @MockBean SafetyService safety;
    @MockBean ProfileServiceImpl profiles;
    @MockBean FirebaseTokenVerifier firebase;

    private String token(String name) {
        var user=User.withUsername(name).password("unused").roles("USER").build();
        when(profiles.loadUserByUsername(name)).thenReturn(user);
        return jwt.generateTokenFromUsername(user);
    }
    @Test void unauthenticatedDenied() throws Exception {
        mvc.perform(get("/admin/reports")).andExpect(status().isUnauthorized());
        mvc.perform(post("/admin/reports/id/resolve").contentType("application/json").content("{\"action\":\"hide\"}"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(safety);
    }
    @Test void signedInNonAdminDeniedOnBothRoutes() throws Exception {
        String t=token("regular-user");
        mvc.perform(get("/admin/reports").header("Authorization","Bearer "+t)).andExpect(status().isForbidden());
        mvc.perform(post("/admin/reports/id/resolve").header("Authorization","Bearer "+t)
            .contentType("application/json").content("{\"action\":\"hide\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(safety);
    }
    @Test void configuredAdminAllowedOnBothRoutes() throws Exception {
        String t=token("test-admin");
        mvc.perform(get("/admin/reports").header("Authorization","Bearer "+t)).andExpect(status().isOk());
        mvc.perform(post("/admin/reports/id/resolve").header("Authorization","Bearer "+t)
            .contentType("application/json").content("{\"action\":\"hide\"}")).andExpect(status().isOk());
        verify(safety).openReports(null);
        verify(safety).resolve("id","hide");
    }
}
