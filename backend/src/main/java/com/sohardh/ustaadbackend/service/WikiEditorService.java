package com.sohardh.ustaadbackend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sohardh.ustaadbackend.config.WikiProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class WikiEditorService {

  private static final Logger log = LoggerFactory.getLogger(WikiEditorService.class);

  private final ChatClient chatClient;
  private final WikiProperties properties;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public WikiEditorService(ChatClient chatClient, WikiProperties properties) {
    this.chatClient = chatClient;
    this.properties = properties;
  }

  public String ingest(String sourceText, String filename) throws IOException {
    log.info("Starting ingestion for file: {}", filename);
    String wikiDir = properties.basePath();
    String sourcesDir = properties.sourcesPath();

    // 1. Save raw immutable source — flat directory, timestamp suffix on name collision
    String baseName = Paths.get(filename).getFileName().toString();
    Path sourcesPath = Paths.get(sourcesDir);
    Files.createDirectories(sourcesPath);
    Path rawPath = sourcesPath.resolve(baseName);
    if (Files.exists(rawPath)) {
      String timestamp = LocalDateTime.now()
          .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
      int dot = baseName.lastIndexOf('.');
      String stem = dot > 0 ? baseName.substring(0, dot) : baseName;
      String ext = dot > 0 ? baseName.substring(dot) : "";
      rawPath = sourcesPath.resolve(stem + "_" + timestamp + ext);
    }
    log.debug("Saving raw source to: {}", rawPath);
    Files.write(rawPath, sourceText.getBytes());

    // 2. Load current wiki context (schema + index + log)
    String schema = readFileIfExists(Paths.get(wikiDir, "schema-work.md").toAbsolutePath());
    String index = readFileIfExists(Paths.get(wikiDir, "index.md").toAbsolutePath());
    String logContent = readFileIfExists(Paths.get(wikiDir, "log.md").toAbsolutePath());

    // 3. Build rich prompt for Karpathy-style editing
    String prompt = """
        You are an expert LLM Wiki editor following Andrej Karpathy's LLM Wiki rules exactly.
        
        Current schema:
        %s
        
        Current index.md:
        %s
        
        Last entries in log.md:
        %s
        
        New source filename: %s
        
        Source content:
        %s
        
        Instructions:
        1. Deeply integrate this source into the wiki (create new pages or update existing ones).
        2. Use strong [[wiki links]] and "Related:" sections.
        3. For code/SQL: keep clean runnable blocks with explanation.
        4. Update index.md and append a new entry to log.md.
        5. Return ONLY valid unformatted JSON: an array of edits.
           Example: [{"page": "topics/ProjectX.md", "content": "full markdown here"}, ...]
        6. There can be code snippets etc. Escape the characters properly, so that it can be read by com.fasterxml.jackson.databind.ObjectMapper
        
        Be professional, concise, and engineering-focused.
        """.formatted(schema, index, logContent, filename, sourceText);

    // 4. Call LLM (streaming, collected to a single string for JSONL parsing)
    log.debug("Calling LLM for wiki edits...");
    String rawResponse = chatClient.prompt()
        .system(
            "You are a precise wiki editor. Output only valid unformatted JSON, no explanations.")
        .user(prompt)
        .stream()
        .content()
        .collect(Collectors.joining())
        .block();
    log.debug("Raw LLM response: {}", rawResponse);

    // 5. Parse JSON edits and apply them
    List<Map<String, String>> edits;
    try {
      edits = objectMapper.readValue(rawResponse, new TypeReference<>() {
      });
    } catch (Exception e) {
      log.error("Failed to parse LLM response as JSON. Raw response: {}", rawResponse, e);
      // Fallback if LLM didn't return clean JSON
      return "Ingested " + filename
          + " (raw saved). LLM response was not valid JSON. Manual review needed.";
    }

    int updated = 0;
    for (Map<String, String> edit : edits) {
      String pagePath = edit.get("page");
      String content = edit.get("content");
      if (pagePath != null && content != null) {
        Path fullPath = Paths.get(wikiDir, pagePath);
        log.info("Updating/creating wiki page: {}", pagePath);
        Files.createDirectories(fullPath.getParent());
        Files.writeString(fullPath, content);
        updated++;
      }
    }

    log.info("Finished ingestion for file: {}. Updated/created {} wiki page(s).", filename,
        updated);
    return "✅ Ingested " + filename + ". Updated/created " + updated + " wiki page(s).";
  }

  private String readFileIfExists(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException e) {
      return "(file not found yet)";
    }
  }
}