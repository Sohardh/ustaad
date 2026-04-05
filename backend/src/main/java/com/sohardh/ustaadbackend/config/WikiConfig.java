package com.sohardh.ustaadbackend.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WikiProperties.class)
public class WikiConfig {

  @Bean
  @ConditionalOnProperty(name = "wiki.llm-provider", havingValue = "gemini")
  public ChatClient geminiChatClient(GoogleGenAiChatModel geminiChatModel) {
    return ChatClient.builder(geminiChatModel).build();
  }

  @Bean
  @ConditionalOnProperty(name = "wiki.llm-provider", matchIfMissing = true, havingValue = "ollama")
  public ChatClient ollamaChatClient(OllamaChatModel ollamaChatModel) {
    return ChatClient.builder(ollamaChatModel).build();
  }
}
