package com.myexampleproject.orderservice.config;

// Import thêm ObjectMapper
import com.fasterxml.jackson.databind.ObjectMapper;

import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    // Bean này cần để chuyển đổi Map -> POJO
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    // --- Tạo MỘT ConsumerFactory chung ---
    @Bean
    public ConsumerFactory<String, Object> genericConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaJsonSchemaDeserializer.class); // Vẫn dùng Schema Deserializer
        props.put("schema.registry.url", "http://localhost:8081");
        props.put("json.ignore.unknown", true);

        // QUAN TRỌNG: KHÔNG chỉ định "json.value.type".
        // Điều này sẽ khiến nó deserialize thành Map<String, Object>.

        props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-updater-group"); // Vẫn dùng chung group

        return new DefaultKafkaConsumerFactory<>(props);
    }

    // --- Tạo MỘT ContainerFactory chung ---
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(genericConsumerFactory());

        // Set concurrency = 1 (mặc định)
        // Khi chạy 3 instance, bạn sẽ có 3 consumer thread.
        // Thread 1 (Instance 0) sẽ lấy TẤT CẢ các partition 0 (của cả 4 topic)
        // Thread 2 (Instance 1) sẽ lấy TẤT CẢ các partition 1 (của cả 4 topic)
        // Thread 3 (Instance 2) sẽ lấy TẤT CẢ các partition 2 (của cả 4 topic)
        // Điều này đảm bảo tất cả event cho 1 order (cùng key) sẽ vào CÙNG 1 instance
        // Tăng số thread consumer trên MỖI instance (ví dụ: 10)
        // Tổng thread = 10 * 3 instance = 30 consumer threads
        factory.setConcurrency(10);

        // *** BẬT CHẾ ĐỘ BATCH ***
        // Đây là tối ưu CỰC LỚN
        factory.setBatchListener(true);

        return factory;
    }

    // --- XÓA TẤT CẢ 8 BEAN CŨ BÊN DƯỚI ---
    // (Xóa orderPlacedConsumerFactory, orderPlacedKafkaListenerContainerFactory,
    //  orderFailedConsumerFactory, orderFailedKafkaListenerContainerFactory,
    //  paymentProcessedConsumerFactory, paymentProcessedKafkaListenerContainerFactory,
    //  paymentFailedConsumerFactory, paymentFailedKafkaListenerContainerFactory)
}