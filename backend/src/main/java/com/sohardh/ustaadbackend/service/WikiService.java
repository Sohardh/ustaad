package com.sohardh.ustaadbackend.service;

import com.sohardh.ustaadbackend.config.WikiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

@Service
public class WikiService {

  private static final Logger log = LoggerFactory.getLogger(WikiService.class);

  private final WikiEditorService editorService;
  private final ChatClient chatClient;
  private final WikiProperties properties;

  public WikiService(WikiEditorService editorService, ChatClient chatClient,
      @Qualifier("wikiProperties") WikiProperties properties) {
    this.editorService = editorService;
    this.chatClient = chatClient;
    this.properties = properties;

  }

  public String ingestFiles(List<MultipartFile> files) throws IOException {
    log.info("Starting bulk ingestion for {} files", files.size());
    StringBuilder results = new StringBuilder();
    for (MultipartFile file : files) {
      if (file.isEmpty()) {
        continue;
      }
      String filename = file.getOriginalFilename();
      String content = new String(file.getBytes(), StandardCharsets.UTF_8);
      String result = editorService.ingest(content, filename);
      results.append(result).append("\n");
    }
    return results.toString();
  }

  public String query(String question) throws IOException {
    log.info("Querying wiki with question: {}", question);
    String wikiContext = loadWikiContext();

    String prompt = """
        You have access to the following wiki knowledge base:
        
        %s
        
        Answer using ONLY the knowledge above.
        Question: %s
        If not enough info, say "Not in knowledge base."
        """.formatted(wikiContext, question);

    String response = chatClient.prompt(prompt).call().content();
    log.debug("Wiki response: {}", response);
    return response;
  }

  private String loadWikiContext() throws IOException {
    Path wikiDir = Paths.get(properties.getBasePath());
    if (!Files.isDirectory(wikiDir)) {
      return "(wiki directory not found)";
    }
    StringBuilder context = new StringBuilder();
    try (Stream<Path> paths = Files.walk(wikiDir)) {
      paths.filter(p -> p.toString().endsWith(".md") && Files.isRegularFile(p))
          .sorted()
          .forEach(p -> {
            try {
              String relativePath = wikiDir.relativize(p).toString();
              context.append("### ").append(relativePath).append("\n");
              context.append(Files.readString(p)).append("\n\n");
            } catch (IOException e) {
              log.warn("Could not read wiki file: {}", p, e);
            }
          });
    }
    return context.toString();
  }
}
