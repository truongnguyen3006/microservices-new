package com.myexampleproject.inventoryservice.config; // (Hoặc package config của bạn)

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    private final int NUM_PARTITIONS = 3; // Đồng bộ TẤT CẢ về 3
    private final short REPLICAS = 1;     // Dùng 1 cho local

    // --- Các topic của Inventory (Đã có) ---
    @Bean
    public NewTopic productCreatedTopic() {
        return TopicBuilder.name("product-created-topic")
                .partitions(NUM_PARTITIONS).replicas(REPLICAS).build();
    }
    @Bean
    public NewTopic orderProcessingTopic() {
        return TopicBuilder.name("order-processing-topic")
                .partitions(NUM_PARTITIONS).replicas(REPLICAS).build();
    }
    @Bean
    public NewTopic orderValidatedTopic() {
        return TopicBuilder.name("order-validated-topic")
                .partitions(NUM_PARTITIONS).replicas(REPLICAS).build();
    }
    @Bean
    public NewTopic orderFailedTopic() {
        return TopicBuilder.name("order-failed-topic")
                .partitions(NUM_PARTITIONS).replicas(REPLICAS).build();
    }

    // --- Topic của OrderService (Bạn đã thêm) ---
    @Bean
    public NewTopic orderPlacedTopic() {
        return TopicBuilder.name("order-placed-topic")
                .partitions(NUM_PARTITIONS).replicas(REPLICAS).build();
    }

    // ==========================================================
    // === THÊM 2 TOPIC CỦA PAYMENTSERVICE VÀO ĐÂY ===
    // ==========================================================

    @Bean
    public NewTopic paymentProcessedTopic() {
        // Topic mà PaymentService GỬI (thành công)
        return TopicBuilder.name("payment-processed-topic")
                .partitions(NUM_PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }

    @Bean
    public NewTopic paymentFailedTopic() {
        // Topic mà PaymentService GỬI (thất bại)
        return TopicBuilder.name("payment-failed-topic")
                .partitions(NUM_PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }
}