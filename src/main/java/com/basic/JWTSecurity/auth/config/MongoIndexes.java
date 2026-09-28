package com.basic.JWTSecurity.auth.config;

import com.basic.JWTSecurity.auth.model.Profile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

// Sign-up checks username / phone / email before saving, but two requests at the same moment can both pass the
// check. Unique indexes make the database the final judge; without them a duplicate breaks every later login
// for that account (findByX expects one result).
@Component
@RequiredArgsConstructor
@Slf4j
public class MongoIndexes implements ApplicationRunner {

    private static final int BSON_STRING = 2;

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        IndexOperations ops = mongoTemplate.indexOps(Profile.class);
        ensure(ops, new Index().on("username", Sort.Direction.ASC).unique().named("username_unique"));
        // Google accounts have no phone and older accounts no email: only documents that have one are indexed
        ensure(ops, uniqueWhenSet("phone"));
        ensure(ops, uniqueWhenSet("email"));
    }

    private static Index uniqueWhenSet(String field) {
        return new Index().on(field, Sort.Direction.ASC).unique()
                .partial(PartialIndexFilter.of(Criteria.where(field).type(BSON_STRING)))
                .named(field + "_unique");
    }

    private static void ensure(IndexOperations ops, Index index) {
        try {
            ops.ensureIndex(index);
        } catch (Exception e) {
            // e.g. duplicates already stored; the app keeps working with the read-before-save checks
            log.warn("Could not create Mongo index {}: {}", index.getIndexOptions().get("name"), e.getMessage());
        }
    }
}
