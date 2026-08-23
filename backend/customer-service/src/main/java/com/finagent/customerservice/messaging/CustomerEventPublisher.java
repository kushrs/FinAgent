package com.finagent.customerservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "customer.profile.updated";

    public void publishProfileUpdated(CustomerProfileUpdatedEvent event) {
        log.info("Publishing customer profile update event for user: {}", event.getUserId());
        try {
            kafkaTemplate.send(TOPIC, event.getUserId().toString(), event);
        } catch (Exception e) {
            log.error("Failed to publish customer profile update event for user: {}", event.getUserId(), e);
        }
    }
}
