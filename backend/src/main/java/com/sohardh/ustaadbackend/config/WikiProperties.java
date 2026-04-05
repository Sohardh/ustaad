package com.sohardh.ustaadbackend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "wiki")
public class WikiProperties {

  private String basePath;
  private String sourcesPath;

  public String getBasePath() {
    return basePath;
  }

  public void setBasePath(String basePath) {
    this.basePath = basePath;
  }

  public String getSourcesPath() {
    return sourcesPath;
  }

  public void setSourcesPath(String sourcesPath) {
    this.sourcesPath = sourcesPath;
  }
}