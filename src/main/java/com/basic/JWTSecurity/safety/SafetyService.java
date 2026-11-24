package com.basic.JWTSecurity.safety;

import com.basic.JWTSecurity.chat.service.ChatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Blocking between users and reports of content, the user-generated-content safeguards Google Play requires. */
@Service
public class SafetyService {

    private static final Logger log = LoggerFactory.getLogger(SafetyService.class);

    public static final Set<String> TARGETS = Set.of("artwork", "comment", "user", "message", "call");
    public static final Set<String> REASONS = Set.of("spam", "harassment", "sexual", "hate", "violence",
            "child_safety", "stolen_art", "other");
    /** Different people reporting the same item hide it until the developer has reviewed it. */
    public static final int AUTO_HIDE_REPORTERS = 3;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final MongoTemplate mongo;

    public SafetyService(MongoTemplate mongo) {
        this.mongo = mongo;
        try {
            mongo.indexOps(UserBlock.class).ensureIndex(new Index().on("blockerId", Sort.Direction.ASC)
                    .on("blockedId", Sort.Direction.ASC).unique().named("block_pair_unique"));
            mongo.indexOps(UserBlock.class).ensureIndex(new Index().on("blockedId", Sort.Direction.ASC).named("blocked_by"));
            mongo.indexOps(ContentReport.class).ensureIndex(new Index().on("reporterId", Sort.Direction.ASC)
                    .on("targetType", Sort.Direction.ASC).on("targetId", Sort.Direction.ASC).unique()
                    .named("report_once_per_reporter"));
            mongo.indexOps(ContentReport.class).ensureIndex(new Index().on("targetType", Sort.Direction.ASC)
                    .on("targetId", Sort.Direction.ASC).named("report_target"));
        } catch (RuntimeException e) {
            log.warn("Could not create safety indexes: {}", e.getMessage());
        }
    }

    public void block(String me, String other) {
        String target = requireUser(other);
        if (target.equals(me)) {
            throw ChatException.badRequest("You can't block yourself");
        }
        Query q = Query.query(Criteria.where("blockerId").is(me).and("blockedId").is(target));
        mongo.upsert(q, new Update().setOnInsert("createdAt", Instant.now()), UserBlock.class);
    }

    public void unblock(String me, String other) {
        mongo.remove(Query.query(Criteria.where("blockerId").is(me).and("blockedId").is(other)), UserBlock.class);
    }

    public List<String> blockedBy(String me) {
        return mongo.find(Query.query(Criteria.where("blockerId").is(me)).with(Sort.by("createdAt").descending()),
                UserBlock.class).stream().map(UserBlock::getBlockedId).toList();
    }

    /** Everyone I blocked plus everyone who blocked me: blocks hide both sides from each other. */
    public Set<String> hiddenUsers(String me) {
        if (me == null || me.isBlank()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (UserBlock b : mongo.find(Query.query(new Criteria().orOperator(
                Criteria.where("blockerId").is(me), Criteria.where("blockedId").is(me))), UserBlock.class)) {
            out.add(me.equals(b.getBlockerId()) ? b.getBlockedId() : b.getBlockerId());
        }
        return out;
    }

    public boolean eitherBlocked(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return mongo.exists(Query.query(new Criteria().orOperator(
                Criteria.where("blockerId").is(a).and("blockedId").is(b),
                Criteria.where("blockerId").is(b).and("blockedId").is(a))), UserBlock.class);
    }

    public void report(String me, String type, String targetId, String ownerId, String reason, String note) {
        if (type == null || !TARGETS.contains(type)) {
            throw ChatException.badRequest("Unknown report type");
        }
        if (reason == null || !REASONS.contains(reason)) {
            throw ChatException.badRequest("Choose a reason");
        }
        if (targetId == null || targetId.isBlank() || targetId.length() > 200) {
            throw ChatException.badRequest("Nothing to report");
        }
        String cleanNote = note == null ? "" : note.trim();
        if (cleanNote.length() > 500) {
            cleanNote = cleanNote.substring(0, 500);
        }
        ContentReport r = new ContentReport(null, me, type, targetId, ownerId == null ? "" : ownerId, reason, cleanNote,
                "open", "", Instant.now(), null);
        try {
            mongo.insert(r);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return; // the same person reporting the same thing twice is one report
        }
        log.warn("CONTENT REPORT {} {} {} reason={} by {}", type, targetId, ownerId, reason, me);
    }

    /** Ids among {@code ids} of this type that must not be shown: enough different reporters, or a child-safety report. */
    public Set<String> hiddenContent(String type, Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        Map<String, Set<String>> reporters = new HashMap<>();
        Set<String> urgent = new HashSet<>();
        for (ContentReport r : mongo.find(Query.query(Criteria.where("targetType").is(type)
                .and("targetId").in(ids).and("status").ne("dismissed")), ContentReport.class)) {
            reporters.computeIfAbsent(r.getTargetId(), k -> new HashSet<>()).add(r.getReporterId());
            if ("child_safety".equals(r.getReason())) {
                urgent.add(r.getTargetId());
            }
        }
        Set<String> hidden = reporters.entrySet().stream()
                .filter(e -> e.getValue().size() >= AUTO_HIDE_REPORTERS).map(Map.Entry::getKey)
                .collect(Collectors.toCollection(HashSet::new));
        hidden.addAll(urgent);
        for (ContentReport r : mongo.find(Query.query(Criteria.where("targetType").is(type)
                .and("targetId").in(ids).and("action").is("hide")), ContentReport.class)) {
            hidden.add(r.getTargetId());
        }
        return hidden;
    }

    public List<ContentReport> openReports(String status) {
        Query q = Query.query(Criteria.where("status").is(status == null || status.isBlank() ? "open" : status))
                .with(Sort.by("createdAt").ascending()).limit(200);
        return mongo.find(q, ContentReport.class);
    }

    /** action: hide | dismiss. Hiding keeps the content out of the app for everyone. */
    public void resolve(String reportId, String action) {
        if (!"hide".equals(action) && !"dismiss".equals(action)) {
            throw ChatException.badRequest("Action must be hide or dismiss");
        }
        ContentReport r = mongo.findById(reportId, ContentReport.class);
        if (r == null) {
            throw ChatException.notFound("Report not found");
        }
        Query same = Query.query(Criteria.where("targetType").is(r.getTargetType()).and("targetId").is(r.getTargetId()));
        mongo.updateMulti(same, new Update().set("status", "hide".equals(action) ? "actioned" : "dismissed")
                .set("action", action).set("resolvedAt", Instant.now()), ContentReport.class);
    }

    private static String requireUser(String username) {
        String u = username == null ? "" : username.trim();
        if (!USERNAME.matcher(u).matches()) {
            throw ChatException.badRequest("Choose a person");
        }
        return u;
    }
}
