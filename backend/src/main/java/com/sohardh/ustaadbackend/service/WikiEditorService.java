package com.sohardh.ustaadbackend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sohardh.ustaadbackend.config.WikiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class WikiEditorService {

  private static final Logger log = LoggerFactory.getLogger(WikiEditorService.class);

  private final ChatClient chatClient;
  private final WikiProperties properties;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public WikiEditorService(ChatClient chatClient,
      @Qualifier("wikiProperties") WikiProperties properties) {
    this.chatClient = chatClient;
    this.properties = properties;
  }

  public String ingest(String sourceText, String filename) throws IOException {
    log.info("Starting ingestion for file: {}", filename);
    String wikiDir = properties.getBasePath();
    String sourcesDir = properties.getSourcesPath();

    // 1. Save raw immutable source
    String timestamp = LocalDateTime.now()
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
    Path rawPath = Paths.get(sourcesDir, timestamp + "-" + filename);
    log.debug("Saving raw source to: {}", rawPath);
    Files.createDirectories(rawPath.getParent());
    Files.write(rawPath, sourceText.getBytes());

    // 2. Load current wiki context (schema + index + log)
    String schema = readFileIfExists(Paths.get(wikiDir, "schema-work.md"));
    String index = readFileIfExists(Paths.get(wikiDir, "index.md"));
    String logContent = readFileIfExists(Paths.get(wikiDir, "log.md"));

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
        5. Return ONLY valid JSON: an array of edits.
        6. Return an array of objects with "page" and "content" keys.
        
        Be professional, concise, and engineering-focused.
        """.formatted(schema, index, logContent, filename, sourceText);

    // 4. Call LLM
    log.debug("Calling LLM for wiki edits...");
    String rawResponse = chatClient.prompt()
        .system("You are a precise wiki editor. Output only valid JSON, no explanations.")
        .user(prompt)
        .call()
        .content();
    log.trace("Raw LLM response: {}", rawResponse);

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

    log.info("Finished ingestion for file: {}. Updated/created {} wiki page(s).", filename, updated);
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