package com.sohardh.ustaadbackend.model;

public record FolderWatcher(
    String id,
    String path,
    boolean enabled,
    int intervalMinutes,
    long lastScannedMillis,
    String lastResult
) {}
