package com.basic.JWTSecurity.call.api;

import com.basic.JWTSecurity.JwtSecurityApplication;
import com.basic.JWTSecurity.auth.config.SecurityConfig;
import com.basic.JWTSecurity.auth.security.AccessDeniedHandlerJwt;
import com.basic.JWTSecurity.auth.security.AuthEntryPointJwt;
import com.basic.JWTSecurity.auth.security.JwtUtils;
import com.basic.JWTSecurity.auth.service.ProfileServiceImpl;
import com.basic.JWTSecurity.call.service.CallConfig;
import com.basic.JWTSecurity.call.service.CallException;
import com.basic.JWTSecurity.call.service.CallService;
import com.basic.JWTSecurity.call.service.CallSession;
import com.basic.JWTSecurity.chat.service.ChatException;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// /chat/call/** behind the real SecurityConfig and JWT filter: a valid app JWT is required and the endpoints act
// only as its subject. CallService is mocked (its behaviour is covered by CallServiceTest).
@WebMvcTest(controllers = CallApi.class)
@Import({SecurityConfig.class, JwtUtils.class, AuthEntryPointJwt.class, AccessDeniedHandlerJwt.class})
@TestPropertySource(properties = {
        "spring.app.jwtSecret=" + CallApiTest.SECRET,
        "spring.app.jwtExpirationMs=2592000000"
})
class CallApiTest {

    // Same slice as SecurityRulesTest: JwtSecurityApplication itself needs Neo4j
    @SpringBootConfiguration
    @ComponentScan(basePackages = "com.basic.JWTSecurity", excludeFilters = {
            @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
            @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtSecurityApplication.class)})
    static class SliceConfiguration {
    }

    static final String SECRET = "dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtMTIzNDU2Nzg=";
    private static final String START_BODY = "{\"callee\":\"bob\",\"kind\":\"video\"}";

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwtUtils;

    @MockBean private ProfileServiceImpl profileService;
    @MockBean private CallService callService;

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

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static CallSession joinable(String status, int uid, Integer ringTimeoutSec) {
        return new CallSession("c1", status, "video", "alice", "bob", "alice__bob", 0, "c1", "app-id", "agora-token",
                uid, 3600, ringTimeoutSec);
    }

    private static CallSession summary(String status, long durationSec) {
        return new CallSession("c1", status, "video", "alice", "bob", "alice__bob", durationSec,
                null, null, null, null, null, null);
    }

    @Test
    void noTokenIs401AndNothingHappens() throws Exception {
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[]{
                get("/chat/call/config"), json(post("/chat/call/start"), START_BODY), post("/chat/call/c1/accept"),
                post("/chat/call/c1/decline"), post("/chat/call/c1/cancel"), post("/chat/call/c1/end"),
                post("/chat/call/c1/token")}) {
            mvc.perform(request).andExpect(status().isUnauthorized());
            mvc.perform(as(alice + "x", request)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(callService);
    }

    @Test
    void configTellsWhetherCallsWork() throws Exception {
        when(callService.config()).thenReturn(new CallConfig(true, "app-id", 45));
        mvc.perform(as(alice, get("/chat/call/config")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.appId").value("app-id"))
                .andExpect(jsonPath("$.ringTimeoutSec").value(45));

        when(callService.config()).thenReturn(new CallConfig(false, null, 45));
        mvc.perform(as(alice, get("/chat/call/config")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.appId").doesNotExist());
    }

    @Test
    void startRingsAsTheJwtSubjectAndAnswersTheSession() throws Exception {
        when(callService.start("alice", "bob", "video")).thenReturn(joinable("ringing", 1, 45));

        // a "caller" in the body cannot place the call for someone else
        mvc.perform(as(alice, json(post("/chat/call/start"), "{\"callee\":\"bob\",\"kind\":\"video\",\"caller\":\"carol\"}")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.callId").value("c1"))
                .andExpect(jsonPath("$.status").value("ringing"))
                .andExpect(jsonPath("$.kind").value("video"))
                .andExpect(jsonPath("$.caller").value("alice"))
                .andExpect(jsonPath("$.callee").value("bob"))
                .andExpect(jsonPath("$.conversationId").value("alice__bob"))
                .andExpect(jsonPath("$.durationSec").value(0))
                .andExpect(jsonPath("$.channel").value("c1"))
                .andExpect(jsonPath("$.appId").value("app-id"))
                .andExpect(jsonPath("$.token").value("agora-token"))
                .andExpect(jsonPath("$.uid").value(1))
                .andExpect(jsonPath("$.expiresInSec").value(3600))
                .andExpect(jsonPath("$.ringTimeoutSec").value(45));
        verify(callService).start("alice", "bob", "video");
    }

    @Test
    void acceptAndTokenAnswerAJoinableSession() throws Exception {
        when(callService.accept("alice", "c1")).thenReturn(joinable("accepted", 2, null));
        when(callService.token("alice", "c1")).thenReturn(joinable("accepted", 2, null));

        for (String step : new String[]{"accept", "token"}) {
            mvc.perform(as(alice, post("/chat/call/c1/" + step)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.status").value("accepted"))
                    .andExpect(jsonPath("$.token").value("agora-token"))
                    .andExpect(jsonPath("$.uid").value(2))
                    .andExpect(jsonPath("$.ringTimeoutSec").doesNotExist());
        }
    }

    @Test
    void declineCancelAndEndTakeAnOptionalBody() throws Exception {
        when(callService.decline(anyString(), anyString(), anyBoolean())).thenReturn(summary("declined", 0));
        when(callService.cancel(anyString(), anyString(), anyBoolean())).thenReturn(summary("cancelled", 0));
        when(callService.end("alice", "c1")).thenReturn(summary("ended", 125));

        // no body at all, as the app sends it for a plain decline / cancel
        mvc.perform(as(alice, post("/chat/call/c1/decline")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("declined"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.channel").doesNotExist())
                .andExpect(jsonPath("$.uid").doesNotExist());
        verify(callService).decline("alice", "c1", false);
        mvc.perform(as(alice, json(post("/chat/call/c1/decline"), "{\"busy\":true}"))).andExpect(status().isOk());
        verify(callService).decline("alice", "c1", true);
        mvc.perform(as(alice, json(post("/chat/call/c1/decline"), "{}"))).andExpect(status().isOk());

        mvc.perform(as(alice, post("/chat/call/c1/cancel")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"));
        verify(callService).cancel("alice", "c1", false);
        mvc.perform(as(alice, json(post("/chat/call/c1/cancel"), "{\"timeout\":true}"))).andExpect(status().isOk());
        verify(callService).cancel("alice", "c1", true);

        mvc.perform(as(alice, post("/chat/call/c1/end")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ended"))
                .andExpect(jsonPath("$.durationSec").value(125));
    }

    @Test
    void aStepTheCallCannotTakeIs409WithItsStatus() throws Exception {
        when(callService.accept("alice", "c1")).thenThrow(CallException.conflict("This call has ended", "cancelled"));

        mvc.perform(as(alice, post("/chat/call/c1/accept")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This call has ended"))
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.callStatus").value("cancelled"));
    }

    @Test
    void errorsAreJsonWithAMessage() throws Exception {
        when(callService.start(anyString(), any(), any())).thenThrow(CallException.notConfigured());
        mvc.perform(as(alice, json(post("/chat/call/start"), START_BODY)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Calls are not configured yet"))
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.callStatus").doesNotExist());

        when(callService.end("alice", "c2")).thenThrow(ChatException.forbidden("You are not part of this call"));
        mvc.perform(as(alice, post("/chat/call/c2/end")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You are not part of this call"))
                .andExpect(jsonPath("$.status").value(false));

        when(callService.end("alice", "c3")).thenThrow(ChatException.notFound("Call not found"));
        mvc.perform(as(alice, post("/chat/call/c3/end")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Call not found"));

        // Google being unreachable is reported as a problem with calls, not with chat
        when(callService.end("alice", "c4")).thenThrow(ChatException.upstream());
        mvc.perform(as(alice, post("/chat/call/c4/end")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Calls are temporarily unavailable, please try again"))
                .andExpect(jsonPath("$.status").value(false));
    }

    @Test
    void startNeedsAReadableBody() throws Exception {
        mvc.perform(as(alice, post("/chat/call/start").contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(false));
        mvc.perform(as(alice, json(post("/chat/call/start"), "{not json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(false));
        verifyNoInteractions(callService);
    }
}
