package com.basic.JWTSecurity.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;

/**
 * Full logical backup of the Neo4j graph and the app's MongoDB database, written as gzipped JSON lines to the
 * App Service's persistent storage (download it from there; nothing is returned over HTTP).
 * <p>
 * Off by default: these endpoints only exist while the app setting BACKUP_ENABLED=true (backup.enabled) is set.
 * Neo4j lines use APOC's export format ({"type":"node"|"relationship", ...}), so apoc.import.json can load them;
 * MongoDB lines are Extended JSON, one file per collection, loadable with mongoimport.
 */
@RestController
@RequestMapping("/admin/backup")
@ConditionalOnProperty(name = "backup.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DatabaseBackupApi {

    private final Driver driver;
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @org.springframework.beans.factory.annotation.Value("${backup.dir:/home/LogFiles/artly-backups}")
    private String backupDir;

    private volatile Map<String, Object> state = Map.of("state", "idle");

    @PostMapping
    public synchronized Map<String, Object> start() {
        if ("running".equals(state.get("state"))) return state;
        String stamp = LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path dir = Path.of(backupDir, stamp);
        state = Map.of("state", "running", "dir", dir.toString());
        executor.submit(() -> run(dir, stamp));
        return state;
    }

    @GetMapping
    public Map<String, Object> status() {
        return state;
    }

    private void run(Path dir, String stamp) {
        try {
            Files.createDirectories(dir);
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("startedAtUtc", stamp);
            manifest.put("neo4j", exportNeo4j(dir.resolve("neo4j.jsonl.gz")));
            manifest.put("mongodb", exportMongo(dir));
            manifest.put("finishedAtUtc", LocalDateTime.now(ZoneOffset.UTC).toString());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve("manifest.json").toFile(), manifest);
            Map<String, Object> done = new LinkedHashMap<>(manifest);
            done.put("state", "done");
            done.put("dir", dir.toString());
            state = done;
            log.info("Backup written to {}", dir);
        } catch (Exception e) {
            log.error("Backup failed", e);
            state = Map.of("state", "failed", "dir", dir.toString(), "error", String.valueOf(e));
        }
    }

    private Map<String, Object> exportNeo4j(Path file) throws IOException {
        Map<String, Long> nodes = new TreeMap<>();
        Map<String, Long> relationships = new TreeMap<>();
        try (Writer out = gzipWriter(file); Session session = driver.session()) {
            Result result = session.run("MATCH (n) RETURN elementId(n) AS id, labels(n) AS labels, properties(n) AS props");
            while (result.hasNext()) {
                Record r = result.next();
                List<String> labels = r.get("labels").asList(Value::asString);
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("type", "node");
                line.put("id", r.get("id").asString());
                line.put("labels", labels);
                line.put("properties", plain(r.get("props").asMap()));
                writeLine(out, line);
                labels.forEach(l -> nodes.merge(l, 1L, Long::sum));
            }
            result = session.run("MATCH (a)-[r]->(b) RETURN elementId(r) AS id, type(r) AS type, properties(r) AS props, "
                    + "elementId(a) AS start, labels(a) AS startLabels, elementId(b) AS end, labels(b) AS endLabels");
            while (result.hasNext()) {
                Record r = result.next();
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("type", "relationship");
                line.put("id", r.get("id").asString());
                line.put("label", r.get("type").asString());
                line.put("properties", plain(r.get("props").asMap()));
                line.put("start", Map.of("id", r.get("start").asString(), "labels", r.get("startLabels").asList(Value::asString)));
                line.put("end", Map.of("id", r.get("end").asString(), "labels", r.get("endLabels").asList(Value::asString)));
                writeLine(out, line);
                relationships.merge(r.get("type").asString(), 1L, Long::sum);
            }
        }
        return Map.of("file", file.getFileName().toString(), "nodesByLabel", nodes, "relationshipsByType", relationships);
    }

    private Map<String, Object> exportMongo(Path dir) throws IOException {
        JsonWriterSettings settings = JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build();
        Map<String, Long> collections = new TreeMap<>();
        for (String name : mongoTemplate.getCollectionNames()) {
            long count = 0;
            try (Writer out = gzipWriter(dir.resolve("mongo-" + name + ".jsonl.gz"))) {
                for (Document doc : mongoTemplate.getCollection(name).find()) {
                    out.write(doc.toJson(settings));
                    out.write('\n');
                    count++;
                }
            }
            collections.put(name, count);
        }
        return Map.of("database", mongoTemplate.getDb().getName(), "documentsByCollection", collections);
    }

    // Neo4j property values -> plain JSON types (temporal, spatial and other driver types become their string form)
    private static Object plain(Object v) {
        if (v == null || v instanceof String || v instanceof Number || v instanceof Boolean) return v;
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> copy = new LinkedHashMap<>();
            m.forEach((k, val) -> copy.put(String.valueOf(k), plain(val)));
            return copy;
        }
        if (v instanceof List<?> l) return l.stream().map(DatabaseBackupApi::plain).toList();
        return String.valueOf(v);
    }

    private void writeLine(Writer out, Map<String, Object> line) throws IOException {
        out.write(objectMapper.writeValueAsString(line));
        out.write('\n');
    }

    private static Writer gzipWriter(Path file) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(file)), StandardCharsets.UTF_8));
    }
}
