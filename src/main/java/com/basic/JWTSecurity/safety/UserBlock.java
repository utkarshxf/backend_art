package com.basic.JWTSecurity.safety;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** blockerId has blocked blockedId. Both are usernames (the JWT subject and the Neo4j User id). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_block")
public class UserBlock {
    @Id
    private String id;
    private String blockerId;
    private String blockedId;
    private Instant createdAt;
}
