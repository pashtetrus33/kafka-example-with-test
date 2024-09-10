package com.example.kafka_example_app;

import com.example.kafka_example_app.model.KafkaMessage;
import com.example.kafka_example_app.service.KafkaMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
class KafkaMessageListenerTest {

    private static final DockerImageName KAFKA_IMAGE = DockerImageName.parse("confluentinc/cp-kafka:latest")
            .asCompatibleSubstituteFor("apache/kafka");

    static KafkaContainer kafkaContainer = new KafkaContainer(KAFKA_IMAGE);

    static {
        kafkaContainer.start();
    }

    @DynamicPropertySource
    static void registerKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    }

    @Autowired
    private KafkaMessageService kafkaMessageService;

    @Autowired
    private KafkaTemplate<String, KafkaMessage> kafkaTemplate;

    @Value("${app.kafka.kafkaMessageTopic}")
    private String topicName;

    @Test
    void whenSendKafkaMessage_thenHandleMessageByListener() {
        KafkaMessage event = new KafkaMessage();
        event.setId(1L);
        event.setMessage("Test message from kafka");
        String key = UUID.randomUUID().toString();

        kafkaTemplate.send(topicName, key, event);

        await()
                .pollInterval(Duration.ofSeconds(3))
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Optional<KafkaMessage> mayBeKafkaMessage = kafkaMessageService.getById(1L);

                    assertThat(mayBeKafkaMessage).isPresent();

                    KafkaMessage kafkaMessage = mayBeKafkaMessage.get();

                    assertThat(kafkaMessage.getMessage()).isEqualTo("Test message from kafka");
                    assertThat(kafkaMessage.getId()).isEqualTo(1L);
                });
    }
}