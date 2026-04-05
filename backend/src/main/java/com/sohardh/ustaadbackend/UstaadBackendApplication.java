package com.sohardh.ustaadbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UstaadBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(UstaadBackendApplication.class, args);
  }

}
