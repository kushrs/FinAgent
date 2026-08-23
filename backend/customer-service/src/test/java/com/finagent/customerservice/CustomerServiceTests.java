package com.finagent.customerservice;

import com.finagent.customerservice.dto.ProfileRequest;
import com.finagent.customerservice.model.CustomerProfile;
import com.finagent.customerservice.repository.CustomerProfileRepository;
import com.finagent.customerservice.service.CustomerService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:custdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = { "customer.profile.updated" })
public class CustomerServiceTests {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerProfileRepository profileRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private Consumer<String, String> consumer;

    @BeforeEach
    public void setup() {
        profileRepository.deleteAll();

        // Configure Kafka consumer to listen to embedded Kafka broker
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-group", "true", embeddedKafkaBroker);
        DefaultKafkaConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(
                consumerProps,
                new StringDeserializer(),
                new StringDeserializer()
        );
        consumer = cf.createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, "customer.profile.updated");
    }

    @AfterEach
    public void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    public void testCreateCustomerProfileAndAsyncBureauTrigger() throws Exception {
        UUID userId = UUID.randomUUID();
        String email = "jane.test@example.com";
        ProfileRequest request = new ProfileRequest("123-45-6789", "123 Financial St", new BigDecimal("75000.00"), "EMPLOYED");

        // Act
        CustomerProfile profile = customerService.createOrUpdateProfile(userId, email, request);

        // Assert
        assertNotNull(profile.getId());
        assertEquals(userId, profile.getUserId());
        assertEquals("123-45-6789", profile.getSsn());
        assertEquals("123 Financial St", profile.getAddress());
        assertEquals(new BigDecimal("75000.00"), profile.getAnnualIncome());
        assertEquals("EMPLOYED", profile.getEmploymentStatus());
        assertNull(profile.getCreditScore()); // Intentionally null initially, updated asynchronously

        // Retrieve Kafka record generated asynchronously by BureauService after 2 seconds
        // (Wait up to 5 seconds to account for simulated latency)
        ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer, "customer.profile.updated", java.time.Duration.ofSeconds(5));
        assertNotNull(record);
        assertEquals(userId.toString(), record.key());

        String jsonPayload = record.value();
        assertTrue(jsonPayload.contains("creditScore"));
        assertTrue(jsonPayload.contains("123-45-6789"));
        assertTrue(jsonPayload.contains(userId.toString()));

        // Retrieve updated profile from database and verify creditScore is populated
        CustomerProfile updatedProfile = customerService.getProfileByUserId(userId);
        assertNotNull(updatedProfile.getCreditScore());
        assertTrue(updatedProfile.getCreditScore() >= 300 && updatedProfile.getCreditScore() <= 850);
    }
}
