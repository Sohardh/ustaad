package com.sohardh.ustaadbackend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sohardh.ustaadbackend.config.WikiProperties;
import com.sohardh.ustaadbackend.model.FolderWatcher;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class FolderWatcherService {

  private static final Logger log = LoggerFactory.getLogger(FolderWatcherService.class);

  private final WikiEditorService editorService;
  private final WikiProperties wikiProperties;
  private final ObjectMapper objectMapper = new ObjectMapper();

  // watcherId -> filePath -> lastModifiedMillis
  private final ConcurrentMap<String, Map<String, Long>> seenFiles = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, FolderWatcher> watchers = new ConcurrentHashMap<>();

  public FolderWatcherService(WikiEditorService editorService, WikiProperties wikiProperties) {
    this.editorService = editorService;
    this.wikiProperties = wikiProperties;
  }

  @PostConstruct
  public void load() {
    var watchersPath = Paths.get(wikiProperties.watchersPath());
    var statePath = Paths.get(wikiProperties.watchersStatePath());

    try {
      if (Files.exists(watchersPath)) {
        List<FolderWatcher> loaded = objectMapper.readValue(watchersPath.toFile(),
            new TypeReference<>() {
            });
        loaded.forEach(w -> watchers.put(w.id(), w));
        log.info("Loaded {} folder watcher(s) from {}", watchers.size(), watchersPath);
      }
    } catch (IOException e) {
      log.warn("Could not load watchers config: {}", e.getMessage());
    }
    try {
      if (Files.exists(statePath)) {
        Map<String, Map<String, Long>> loaded = objectMapper.readValue(statePath.toFile(),
            new TypeReference<>() {
            });
        seenFiles.putAll(loaded);
      }
    } catch (IOException e) {
      log.warn("Could not load watchers state: {}", e.getMessage());
    }
  }

  private synchronized void saveConfig() {
    var path = Paths.get(wikiProperties.watchersPath());
    try {
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), watchers.values());
    } catch (IOException e) {
      log.error("Could not save watchers config", e);
    }
  }

  private synchronized void saveState() {
    var path = Paths.get(wikiProperties.watchersStatePath());
    try {
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), seenFiles);
    } catch (IOException e) {
      log.error("Could not save watchers state", e);
    }
  }

  public List<FolderWatcher> getWatchers() {
    return watchers.values().stream().sorted(Comparator.comparing(FolderWatcher::path)).toList();
  }

  public FolderWatcher addWatcher(String path, int intervalMinutes) {
    var watcher = new FolderWatcher(UUID.randomUUID().toString(), path, true, intervalMinutes, 0,
        "Not yet scanned");
    watchers.put(watcher.id(), watcher);
    saveConfig();
    log.info("Added folder watcher: {} (every {} min)", path, intervalMinutes);
    return watcher;
  }

  public boolean toggleWatcher(String id) {
    return watchers.computeIfPresent(id, (k, w) -> {
      var updated = new FolderWatcher(w.id(), w.path(), !w.enabled(), w.intervalMinutes(),
          w.lastScannedMillis(), w.lastResult());
      saveConfig();
      return updated;
    }) != null;
  }

  public boolean deleteWatcher(String id) {
    var removed = watchers.remove(id) != null;
    if (removed) {
      seenFiles.remove(id);
      saveConfig();
      saveState();
    }
    return removed;
  }

  public String runWatcher(String id) {
    var watcher = watchers.get(id);
    if (watcher != null) {
      return scanFolder(watcher);
    }
    return "Watcher not found: " + id;
  }

  @Scheduled(fixedDelay = 60_000)
  public void scanAll() {
    var now = System.currentTimeMillis();
    watchers.values().stream().filter(FolderWatcher::enabled)
        .filter(w -> now - w.lastScannedMillis() >= (long) w.intervalMinutes() * 60_000)
        .forEach(w -> {
          log.info("Scheduled scan triggered for: {}", w.path());
          scanFolder(w);
        });
  }

  private synchronized String scanFolder(FolderWatcher watcher) {
    var dir = Paths.get(watcher.path());
    if (!Files.isDirectory(dir)) {
      var msg = "Directory not found: " + watcher.path();
      updateWatcherStatus(watcher, msg);
      return msg;
    }

    var seen = seenFiles.computeIfAbsent(watcher.id(), k -> new ConcurrentHashMap<>());
    var results = new StringBuilder();
    var stats = new int[]{0, 0}; // ingested, skipped

    try (var stream = Files.find(dir, Integer.MAX_VALUE, (p, a) -> a.isRegularFile())) {
      stream.sorted().forEach(file -> {
        var key = file.toAbsolutePath().toString();
        long lastModified;
        try {
          lastModified = Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
          log.warn("Cannot read file metadata: {}", file, e);
          return;
        }

        var previousModified = seen.get(key);
        if (previousModified != null && previousModified >= lastModified) {
          stats[1]++;
          return;
        }

        // New or modified file — ingest it
        try {
          var content = Files.readString(file, StandardCharsets.UTF_8);
          var filename = file.getFileName().toString();
          log.info("Ingesting file from watcher: {}", file);
          var result = editorService.ingest(content, filename);
          results.append(result).append("\n");
          seen.put(key, lastModified);
          stats[0]++;
        } catch (IOException e) {
          log.error("Failed to ingest file: {}", file, e);
          results.append("Error ingesting ").append(file.getFileName()).append(": ")
              .append(e.getMessage()).append("\n");
        }
      });
    } catch (IOException e) {
      log.error("Failed to walk directory: {}", watcher.path(), e);
      var msg = "Error scanning directory: " + e.getMessage();
      updateWatcherStatus(watcher, msg);
      return msg;
    }

    saveState();

    var summary = String.format("Scanned %s — ingested %d file(s), skipped %d unchanged.",
        watcher.path(), stats[0], stats[1]);
    if (!results.isEmpty()) {
      summary = summary + "\n" + results.toString().trim();
    }
    updateWatcherStatus(watcher, summary);
    log.info("Watcher scan complete: {}", summary);
    return summary;
  }

  private void updateWatcherStatus(FolderWatcher watcher, String result) {
    watchers.put(watcher.id(), new FolderWatcher(watcher.id(), watcher.path(), watcher.enabled(),
        watcher.intervalMinutes(), System.currentTimeMillis(), result));
    saveConfig();
  }
}
