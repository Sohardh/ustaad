package com.sohardh.ustaadbackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class WikiService {

  private static final Logger log = LoggerFactory.getLogger(WikiService.class);

  private final WikiEditorService editorService;
  private final ChatClient chatClient;

  public WikiService(WikiEditorService editorService, ChatClient chatClient) {
    this.editorService = editorService;
    this.chatClient = chatClient;
  }

  public String ingestFiles(List<MultipartFile> files) throws IOException {
    log.info("Starting bulk ingestion for {} files", files.size());
    StringBuilder results = new StringBuilder();
    for (MultipartFile file : files) {
      if (file.isEmpty()) continue;
      String filename = file.getOriginalFilename();
      String content = new String(file.getBytes(), StandardCharsets.UTF_8);
      String result = editorService.ingest(content, filename);
      results.append(result).append("\n");
    }
    return results.toString();
  }

  public String query(String question) {
    log.info("Querying wiki with question: {}", question);
    String prompt = """
        Answer using ONLY the knowledge in my work wiki.
        Question: %s
        If not enough info, say "Not in knowledge base."
        """.formatted(question);

    String response = chatClient.prompt(prompt).call().content();
    log.debug("Wiki response: {}", response);
    return response;
  }
}
