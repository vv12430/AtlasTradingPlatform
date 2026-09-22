package com.atlas.common;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(
  name = "atlas.messaging.enabled",
  havingValue = "true",
  matchIfMissing = true
)
public class MessagingConfig {

  @Bean
  NewTopic submitted() {
    return TopicBuilder.name("trade.submitted.v1")
      .partitions(3)
      .replicas(1)
      .build();
  }

  @Bean
  NewTopic assessed() {
    return TopicBuilder.name("risk.assessed.v1")
      .partitions(3)
      .replicas(1)
      .build();
  }

  @Bean
  NewTopic executed() {
    return TopicBuilder.name("trade.executed.v1")
      .partitions(3)
      .replicas(1)
      .build();
  }

  @Bean
  NewTopic submittedDlt() {
    return TopicBuilder.name("trade.submitted.v1.DLT")
      .partitions(3)
      .replicas(1)
      .build();
  }

  @Bean
  NewTopic assessedDlt() {
    return TopicBuilder.name("risk.assessed.v1.DLT")
      .partitions(3)
      .replicas(1)
      .build();
  }

  @Bean
  NewTopic executedDlt() {
    return TopicBuilder.name("trade.executed.v1.DLT")
      .partitions(3)
      .replicas(1)
      .build();
  }

  @Bean
  DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafka) {
    var recoverer = new DeadLetterPublishingRecoverer(kafka);
    recoverer.setFailIfSendResultIsError(true);
    return new DefaultErrorHandler(recoverer, new FixedBackOff(1000, 4));
  }
}
