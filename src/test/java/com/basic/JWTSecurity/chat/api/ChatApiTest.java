package com.basic.JWTSecurity.chat.api;

import com.basic.JWTSecurity.JwtSecurityApplication;
import com.basic.JWTSecurity.auth.config.SecurityConfig;
import com.basic.JWTSecurity.auth.security.AccessDeniedHandlerJwt;
import com.basic.JWTSecurity.auth.security.AuthEntryPointJwt;
import com.basic.JWTSecurity.auth.security.JwtUtils;
import com.basic.JWTSecurity.auth.service.ProfileServiceImpl;
import com.basic.JWTSecurity.chat.service.ChatException;
import com.basic.JWTSecurity.chat.service.ChatService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// /chat/** behind the real SecurityConfig and JWT filter: a valid app JWT is required and the endpoints act only as
// its subject. ChatService is mocked (its behaviour is covered by ChatServiceTest).
@WebMvcTest(controllers = ChatApi.class)
@Import({SecurityConfig.class, JwtUtils.class, AuthEntryPointJwt.class, AccessDeniedHandlerJwt.class})
@TestPropertySource(properties = {
        "spring.app.jwtSecret=" + ChatApiTest.SECRET,
        "spring.app.jwtExpirationMs=2592000000"
})
class ChatApiTest {

    // Same slice as SecurityRulesTest: JwtSecurityApplication itself needs Neo4j
    @SpringBootConfiguration
    @ComponentScan(basePackages = "com.basic.JWTSecurity", excludeFilters = {
            @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
            @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtSecurityApplication.class)})
    static class SliceConfiguration {
    }

    static final String SECRET = "dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtMTIzNDU2Nzg=";
    private static final String NOTIFY_BODY = "{\"conversationId\":\"alice__bob\",\"messageId\":\"m1\"}";

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwtUtils;

    @MockBean private ProfileServiceImpl profileService;
    @MockBean private ChatService chatService;

    private String alice;

    @BeforeEach
    void setUp() {
        when(profileService.loadUserByUsername("alice")).thenReturn(user("alice"));
        alice = jwtUtils.generateTokenFromUsername(user("alice"));
    }

    private static UserDetails user(String name) {
        return User.withUsername(name).password("x").roles("USER").build();
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder notifyRequest(String body) {
        return post("/chat/notify").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // ---- authentication

    @Test
    void noTokenIs401Json() throws Exception {
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[]{
                post("/chat/token"), notifyRequest(NOTIFY_BODY), get("/chat/token"), post("/chat/other")}) {
            mvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Unauthorized"));
        }
        verifyNoInteractions(chatService);
    }

    @Test
    void expiredOrForgedTokenIs401() throws Exception {
        String expired = Jwts.builder().subject("alice")
                .issuedAt(new Date(System.currentTimeMillis() - 7_200_000))
                .expiration(new Date(System.currentTimeMillis() - 3_600_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .compact();
        String otherKey = Jwts.builder().subject("alice").expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode("b3RoZXItc2VjcmV0LW90aGVyLXNlY3JldC1vdGhlci1zZWNyZXQ=")))
                .compact();
        for (String token : new String[]{expired, otherKey, alice + "x", "garbage"}) {
            mvc.perform(as(token, post("/chat/token"))).andExpect(status().isUnauthorized());
            mvc.perform(as(token, notifyRequest(NOTIFY_BODY))).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(chatService);
    }

    // ---- acting as the JWT subject

    @Test
    void tokenIsMintedForTheJwtSubject() throws Exception {
        when(chatService.mintCustomToken("alice")).thenReturn("firebase-custom-token");

        mvc.perform(as(alice, post("/chat/token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("firebase-custom-token"))
                .andExpect(jsonPath("$.uid").value("alice"))
                .andExpect(header().string("Cache-Control", "no-store"));
        // a body cannot pick another uid
        mvc.perform(as(alice, post("/chat/token").contentType(MediaType.APPLICATION_JSON).content("{\"uid\":\"bob\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uid").value("alice"));
    }

    @Test
    void notifyActsAsTheJwtSubject() throws Exception {
        when(chatService.notifyRecipient("alice", "alice__bob", "m1")).thenReturn(2);

        // an extra "sender" in the body is ignored
        mvc.perform(as(alice, notifyRequest("{\"conversationId\":\"alice__bob\",\"messageId\":\"m1\",\"sender\":\"bob\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(2));
        verify(chatService).notifyRecipient("alice", "alice__bob", "m1");
    }

    // ---- errors

    @Test
    void notConfiguredIs503Json() throws Exception {
        when(chatService.mintCustomToken(anyString())).thenThrow(ChatException.notConfigured());
        when(chatService.notifyRecipient(anyString(), any(), any())).thenThrow(ChatException.notConfigured());

        mvc.perform(as(alice, post("/chat/token")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Chat is not configured yet"))
                .andExpect(jsonPath("$.status").value(false));
        mvc.perform(as(alice, notifyRequest(NOTIFY_BODY)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Chat is not configured yet"))
                .andExpect(jsonPath("$.status").value(false));
    }

    @Test
    void refusedNotifyIs403Json() throws Exception {
        when(chatService.notifyRecipient("alice", "bob__carol", "m1"))
                .thenThrow(ChatException.forbidden("You are not a member of this conversation"));

        mvc.perform(as(alice, notifyRequest("{\"conversationId\":\"bob__carol\",\"messageId\":\"m1\"}")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You are not a member of this conversation"))
                .andExpect(jsonPath("$.status").value(false));
    }

    @Test
    void unreadableBodyIs400Json() throws Exception {
        mvc.perform(as(alice, post("/chat/notify").contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(false));
        mvc.perform(as(alice, notifyRequest("{not json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(false));
        verifyNoInteractions(chatService);
    }
}
