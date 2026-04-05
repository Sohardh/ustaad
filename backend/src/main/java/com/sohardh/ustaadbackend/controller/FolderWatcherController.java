package com.sohardh.ustaadbackend.controller;

import com.sohardh.ustaadbackend.model.FolderWatcher;
import com.sohardh.ustaadbackend.service.FolderWatcherService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/watchers")
public class FolderWatcherController {

  public record AddWatcherRequest(String path, int intervalMinutes) {}

  private final FolderWatcherService watcherService;

  public FolderWatcherController(FolderWatcherService watcherService) {
    this.watcherService = watcherService;
  }

  @GetMapping
  public List<FolderWatcher> list() {
    return watcherService.getWatchers();
  }

  @PostMapping
  public ResponseEntity<FolderWatcher> add(@RequestBody AddWatcherRequest body) {
    if (body.path() == null || body.path().isBlank()) {
      return ResponseEntity.badRequest().build();
    }
    int intervalMinutes = body.intervalMinutes() > 0 ? body.intervalMinutes() : 60;
    FolderWatcher watcher = watcherService.addWatcher(body.path().trim(), intervalMinutes);
    return ResponseEntity.ok(watcher);
  }

  @PutMapping("/{id}/toggle")
  public ResponseEntity<Void> toggle(@PathVariable String id) {
    boolean found = watcherService.toggleWatcher(id);
    return found ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    boolean removed = watcherService.deleteWatcher(id);
    return removed ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
  }

  @PostMapping("/{id}/run")
  public ResponseEntity<Map<String, String>> run(@PathVariable String id) {
    String result = watcherService.runWatcher(id);
    return ResponseEntity.ok(Map.of("result", result));
  }
}
