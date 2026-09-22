package com.atlas.accounting;

@org.springframework.boot.autoconfigure.SpringBootApplication(
  scanBasePackages = { "com.atlas.accounting", "com.atlas.common" }
)
@org.springframework.scheduling.annotation.EnableScheduling
public class Application {

  public static void main(String[] args) {
    org.springframework.boot.SpringApplication.run(Application.class, args);
  }
}
