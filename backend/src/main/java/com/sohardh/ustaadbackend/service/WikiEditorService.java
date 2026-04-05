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
import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class WikiEditorService {

  private static final Logger log = LoggerFactory.getLogger(WikiEditorService.class);
  private static final Pattern NEWLINE_IN_STRING = Pattern.compile("(?<!\\\\)\\\\n|(?<!\\\\)\\n");

  private final ChatClient chatClient;
  private final WikiProperties properties;
  private final ObjectMapper objectMapper = new ObjectMapper();

  // Regex to escape any remaining unescaped newlines inside JSON strings
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
    log.info("Raw source saved: {}", rawPath);

    // 2. Load current context
    String schema = readFile(Paths.get(wikiDir, "schema-work.md"));
    String currentIndex = readFile(Paths.get(wikiDir, "index.md"));
    String currentLog = readFile(Paths.get(wikiDir, "log.md"));

    // 3. prompt
    String prompt = """
        You are a strict LLM Wiki editor following Karpathy's rules.
        
        Current schema:
        %s
        
        Current index.md:
        %s
        
        Current log.md:
        %s
        
        NEW SOURCE: %s
        
        Source content:
        %s
        
        TASK:
        1. Deeply integrate this source (create/update any relevant pages).
        2. ALWAYS include TWO edits in your JSON:
           - Update index.md (full new content)
           - Append a new entry to log.md (return the FULL updated log.md content)
        3. Use strong [[backlinks]] and "Related:" sections.
        4. For code/SQL keep clean runnable blocks.
        
        RETURN ONLY valid JSON (no explanations, no markdown, no ```json, no format):
        [{"page": "relative/path.md", "content": "full markdown here"}, ...]
        """.formatted(schema, currentIndex, currentLog, filename, sourceText);

    // 4. Call LLM
    String rawResponse = chatClient.prompt()
        .system(
            "You are a precise wiki editor. Output ONLY valid JSON array of edits. Never add any extra text.")
        .user(prompt)
        .call()
        .content();

    log.info("Raw LLM response for ingest of {}:\n{}", filename, rawResponse);

    // 5. Sanitize response (fix any remaining unescaped newlines)

    // 6. Parse JSON edits
    List<Map<String, String>> edits;
    try {
      edits = objectMapper.readValue(Objects.requireNonNull(rawResponse).trim(),
          new TypeReference<>() {
          });
    } catch (Exception e) {
      log.error("JSON parse failed for {}", filename, e);
      return "✅ Raw file saved, but LLM did not return valid JSON. Check logs.";
    }

    // 7. Apply all edits (this now reliably updates index + log)
    int updated = 0;
    for (Map<String, String> edit : edits) {
      String page = edit.get("page");
      String content = edit.get("content");
      if (page != null && content != null) {
        Path fullPath = Paths.get(wikiDir, page);
        Files.createDirectories(fullPath.getParent());
        Files.writeString(fullPath, content);
        log.info("Updated wiki page: {}", page);
        updated++;
      }
    }

    return "✅ Ingested " + filename + " → Updated " + updated
        + " wiki page(s) (including index.md and log.md).";
  }

  private String readFile(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException e) {
      return "(file not created yet)";
    }
  }
}