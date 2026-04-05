package com.sohardh.ustaadbackend.controller;

import com.sohardh.ustaadbackend.service.WikiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Controller
public class WikiController {

  private static final Logger log = LoggerFactory.getLogger(WikiController.class);

  private final WikiService wikiService;

  public WikiController(WikiService wikiService) {
    this.wikiService = wikiService;
  }

  @GetMapping("/")
  public String home(Model model) {
    model.addAttribute("message", "Work LLM Wiki Agent ready (Ollama + llama3.1:70b)");
    return "index";
  }

  @PostMapping("/ingest")
  public String ingest(@RequestParam("files") MultipartFile[] files, RedirectAttributes redirectAttributes) throws IOException {
    log.info("Received request to ingest {} files", files.length);
    String result = wikiService.ingestFiles(java.util.Arrays.asList(files));
    log.info("Bulk ingestion completed.");
    redirectAttributes.addFlashAttribute("message", result);
    return "redirect:/";
  }

  @PostMapping("/query")
  public String query(@RequestParam("question") String question, RedirectAttributes redirectAttributes) {
    log.info("Received wiki query: {}", question);
    String answer = wikiService.query(question);
    log.debug("Query answer: {}", answer);
    redirectAttributes.addFlashAttribute("answer", answer);
    redirectAttributes.addFlashAttribute("question", question);
    return "redirect:/";
  }
}
