package com.basic.JWTSecurity.chat;

import com.basic.JWTSecurity.call.api.CallApi;
import com.basic.JWTSecurity.call.service.AgoraTokens;
import com.basic.JWTSecurity.call.service.CallService;
import com.basic.JWTSecurity.chat.api.ChatApi;
import com.basic.JWTSecurity.chat.config.ChatFirebaseCredentials;
import com.basic.JWTSecurity.chat.service.ChatException;
import com.basic.JWTSecurity.chat.service.ChatService;
import com.basic.JWTSecurity.chat.service.DevicePusher;
import com.basic.JWTSecurity.chat.service.FcmClient;
import com.basic.JWTSecurity.chat.service.FirestoreRestClient;
import com.basic.JWTSecurity.chat.service.GoogleAccessTokenProvider;
import com.basic.JWTSecurity.chat.service.GoogleApiClient;
import com.basic.JWTSecurity.chat.service.JdkChatHttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The chat and call beans wire up with Spring the way the app starts them (the full application context needs Neo4j
// and MongoDB, so it cannot run here), with and without FIREBASE_SERVICE_ACCOUNT_B64 and the Agora keys.
class ChatWiringTest {

    @Configuration
    @Import({ChatFirebaseCredentials.class, JdkChatHttpClient.class, GoogleAccessTokenProvider.class,
            GoogleApiClient.class, FirestoreRestClient.class, FcmClient.class, DevicePusher.class, ChatService.class,
            ChatApi.class, AgoraTokens.class, CallService.class, CallApi.class})
    static class ChatBeans {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class)
            .withUserConfiguration(ChatBeans.class)
            .withPropertyValues("firebase.project-id=test-project");

    @Test
    void startsWithoutAServiceAccountAndAnswers503() {
        runner.run(context -> {
            assertFalse(context.getBean(ChatFirebaseCredentials.class).isConfigured());
            assertNotNull(context.getBean(ChatApi.class));
            ChatException e = assertThrows(ChatException.class, () -> context.getBean(ChatService.class).mintCustomToken("alice"));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
            // no Agora keys either: calls are off, and say so
            assertNotNull(context.getBean(CallApi.class));
            assertFalse(context.getBean(CallService.class).config().enabled());
            ChatException noCalls = assertThrows(ChatException.class,
                    () -> context.getBean(CallService.class).start("alice", "bob", "audio"));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, noCalls.getStatus());
        });
    }

    @Test
    void readsTheServiceAccountFromTheProperty() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        Map<String, String> json = new LinkedHashMap<>();
        json.put("type", "service_account");
        json.put("project_id", "test-project");
        json.put("private_key", pem);
        json.put("client_email", "chat@test-project.iam.gserviceaccount.com");
        String b64 = Base64.getEncoder().encodeToString(new ObjectMapper().writeValueAsBytes(json));
        assertTrue(new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8).contains("service_account"));

        // Agora keys alone are not enough: calls also need the Firebase account
        runner.withPropertyValues("agora.app-id=0123456789abcdef0123456789abcdef",
                "agora.app-certificate=fedcba9876543210fedcba9876543210").run(context -> {
            assertTrue(context.getBean(AgoraTokens.class).isConfigured());
            assertFalse(context.getBean(CallService.class).config().enabled());
        });

        runner.withPropertyValues("firebase.service-account-b64=" + b64,
                "agora.app-id=0123456789abcdef0123456789abcdef",
                "agora.app-certificate=fedcba9876543210fedcba9876543210").run(context -> {
            assertTrue(context.getBean(CallService.class).config().enabled());
            assertEquals("0123456789abcdef0123456789abcdef", context.getBean(CallService.class).config().appId());
        });

        runner.withPropertyValues("firebase.service-account-b64=" + b64).run(context -> {
            assertFalse(context.getBean(CallService.class).config().enabled());
            assertTrue(context.getBean(ChatFirebaseCredentials.class).isConfigured());
            assertEquals("test-project", context.getBean(ChatFirebaseCredentials.class).projectId());
            assertEquals(3, context.getBean(ChatService.class).mintCustomToken("alice").split("\\.").length);
        });
    }
}
