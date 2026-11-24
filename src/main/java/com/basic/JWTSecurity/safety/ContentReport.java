package com.basic.JWTSecurity.safety;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** One user's report about one artwork, comment, user, chat message or call. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "content_report")
public class ContentReport {
    @Id
    private String id;
    private String reporterId;
    /** artwork | comment | user | message | call */
    private String targetType;
    private String targetId;
    private String targetOwnerId;
    private String reason;
    private String note;
    /** open | actioned | dismissed */
    private String status;
    private String action;
    private Instant createdAt;
    private Instant resolvedAt;
}
