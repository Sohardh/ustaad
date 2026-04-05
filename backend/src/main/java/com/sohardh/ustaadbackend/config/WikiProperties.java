package com.sohardh.ustaadbackend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wiki")
public record WikiProperties(
    String basePath,
    String sourcesPath,
    String watchersPath,
    String watchersStatePath
) {}
