package com.atlas.portfolio;

@org.springframework.boot.autoconfigure.SpringBootApplication(
  scanBasePackages = { "com.atlas.portfolio", "com.atlas.common" }
)
@org.springframework.scheduling.annotation.EnableScheduling
public class Application {

  public static void main(String[] args) {
    org.springframework.boot.SpringApplication.run(Application.class, args);
  }
}
