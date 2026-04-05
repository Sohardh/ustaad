package com.sohardh.ustaadbackend.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WikiProperties.class)
public class WikiConfig {

  @Bean
  public ChatClient chatClient(OllamaChatModel ollamaChatModel) {
    return ChatClient.builder(ollamaChatModel).build();
  }
}