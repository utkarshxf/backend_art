package com.basic.JWTSecurity.call.service;

import com.basic.JWTSecurity.chat.service.ChatException;
import io.agora.media.RtcTokenBuilder2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * The Agora project calls run on, read once at startup from {@code agora.app-id} / {@code agora.app-certificate}
 * (env AGORA_APP_ID, AGORA_APP_CERTIFICATE). The App ID is public: the app receives it with every token. The App
 * Certificate signs the tokens and never leaves this class: it is not logged, returned or put into an exception.
 * A missing or malformed value only disables calls (/chat/call/** then answers 503).
 */
@Component
public class AgoraTokens {

    private static final Pattern KEY = Pattern.compile("[0-9a-fA-F]{32}");

    private static final Logger logger = LoggerFactory.getLogger(AgoraTokens.class);

    /** Both empty when calls are not configured. */
    private final String appId;
    private final String certificate;

    public AgoraTokens(@Value("${agora.app-id:}") String appId,
                       @Value("${agora.app-certificate:}") String certificate) {
        String id = appId == null ? "" : appId.trim();
        String secret = certificate == null ? "" : certificate.trim();
        boolean usable = false;
        if (id.isEmpty() || secret.isEmpty()) {
            logger.info("Calls are disabled: AGORA_APP_ID / AGORA_APP_CERTIFICATE are not set");
        } else if (!KEY.matcher(id).matches() || !KEY.matcher(secret).matches()) {
            logger.error("Calls are disabled: AGORA_APP_ID and AGORA_APP_CERTIFICATE must be 32 hexadecimal characters each");
        } else {
            usable = true;
            logger.info("Calls are enabled (Agora app {}...)", id.substring(0, 6));
        }
        this.appId = usable ? id : "";
        this.certificate = usable ? secret : "";
    }

    public boolean isConfigured() {
        return !appId.isEmpty();
    }

    /** The public App ID the app creates its Agora engine with; empty when calls are not configured. */
    public String appId() {
        return appId;
    }

    /**
     * A token that lets {@code uid} join {@code channel} and publish audio and video there for {@code validity}
     * from now. It works for that channel and uid only.
     */
    public String rtcToken(String channel, int uid, Duration validity) {
        if (!isConfigured()) {
            throw CallException.notConfigured();
        }
        int seconds = (int) Math.min(Integer.MAX_VALUE, validity.getSeconds());
        String token;
        try {
            token = new RtcTokenBuilder2().buildTokenWithUid(appId, certificate, channel, uid,
                    RtcTokenBuilder2.Role.ROLE_PUBLISHER, seconds, seconds);
        } catch (RuntimeException e) {
            // only the exception's type: a library message could quote its inputs
            logger.error("Could not build an Agora token: {}", e.getClass().getSimpleName());
            throw ChatException.upstream();
        }
        if (token == null || token.isEmpty()) {
            logger.error("Could not build an Agora token: the builder returned nothing");
            throw ChatException.upstream();
        }
        return token;
    }
}
