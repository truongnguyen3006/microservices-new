package com.myexampleproject.inventoryservice.service;

import com.myexampleproject.inventoryservice.event.*;
import com.myexampleproject.inventoryservice.dto.OrderLineItemsDto;
import lombok.extern.slf4j.Slf4j;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.streams.serdes.json.KafkaJsonSchemaSerde;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;

// ========================================================================
// === IMPORT CHÍNH XÁC (SỬ DỤNG 100% API MỚI) ===
// ========================================================================
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
// === BỎ CÁC IMPORT CŨ (nếu còn): ===
// BỎ: import org.apache.kafka.streams.processor.Processor;
// BỎ: import org.apache.kafka.streams.processor.ProcessorContext;
// BỎ: import org.apache.kafka.streams.processor.ProcessorSupplier;
// ========================================================================

import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;

import java.util.Collections;
import java.util.Map;

@Configuration
@Slf4j
@EnableKafkaStreams
public class InventoryService {

    public static final String INVENTORY_STORE_NAME = "inventory-store";

    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    private <T> Serde<T> createJsonSchemaSerde(Class<T> dtoClass) {
        final Serde<T> serde = new KafkaJsonSchemaSerde<>(dtoClass);
        Map<String, String> config =
                Map.of(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        serde.configure(config, false);
        return serde;
    }

    @Bean
    public KStream<String, OrderValidatedEvent> inventoryTopology(StreamsBuilder builder) {

        Serde<ProductCreatedEvent> productCreatedSerde =
                createJsonSchemaSerde(ProductCreatedEvent.class);
        Serde<OrderProcessingEvent> orderProcessingSerde =
                createJsonSchemaSerde(OrderProcessingEvent.class);
        Serde<OrderValidatedEvent> orderValidatedSerde =
                createJsonSchemaSerde(OrderValidatedEvent.class);
        Serde<OrderFailedEvent> orderFailedSerde =
                createJsonSchemaSerde(OrderFailedEvent.class);

        // --- BƯỚC 1: ĐỊNH NGHĨA STATE STORE ---
        StoreBuilder<KeyValueStore<String, Integer>> inventoryStoreBuilder =
                Stores.keyValueStoreBuilder(
                        Stores.persistentKeyValueStore(INVENTORY_STORE_NAME),
                        Serdes.String(),
                        Serdes.Integer()
                );
        builder.addStateStore(inventoryStoreBuilder);


        // --- BƯỚC 2: XỬ LÝ "product-created-topic" (Dùng API Mới) ---
        KStream<String, ProductCreatedEvent> productCreationStream = builder.stream(
                "product-created-topic",
                Consumed.with(Serdes.String(), productCreatedSerde)
        );

        productCreationStream.process(
                () -> new Processor<String, ProductCreatedEvent, Void, Void>() {

                    private KeyValueStore<String, Integer> store;
                    private ProcessorContext<Void, Void> context;

                    @Override
                    public void init(ProcessorContext<Void, Void> context) {
                        this.context = context;
                        this.store = context.getStateStore(INVENTORY_STORE_NAME);
                    }

                    @Override
                    public void process(Record<String, ProductCreatedEvent> record) {
                        String skuCode = record.key();
                        if (skuCode == null) return;

                        int initialQuantity = record.value().getInitialQuantity();

                        if (this.store.get(skuCode) == null) {
                            this.store.put(skuCode, initialQuantity);
                            log.info("[Streams] Initialized stock for {}: {}", skuCode, initialQuantity);
                        } else {
                            log.warn("[Streams] SKU {} already exists. Ignoring.", skuCode);
                        }
                    }

                    @Override
                    public void close() {}
                },
                INVENTORY_STORE_NAME
        );

        // --- BƯỚC 3: XỬ LÝ "order-processing-topic" (Dùng API Mới) ---
        KStream<String, OrderProcessingEvent> orderProcessingStream = builder.stream(
                "order-processing-topic",
                Consumed.with(Serdes.String(), orderProcessingSerde)
        );

        KStream<String, Object> validationResultStream = orderProcessingStream.process(
                () -> new Processor<String, OrderProcessingEvent, String, Object>() {

                    private KeyValueStore<String, Integer> store;
                    private ProcessorContext<String, Object> context;

                    @Override
                    public void init(ProcessorContext<String, Object> context) {
                        this.context = context;
                        this.store = context.getStateStore(INVENTORY_STORE_NAME);
                    }

                    @Override
                    public void process(Record<String, OrderProcessingEvent> record) {
                        String skuCode = record.key();
                        OrderProcessingEvent orderEvent = record.value();

                        if (orderEvent == null || orderEvent.getOrderLineItemsDtoList() == null || orderEvent.getOrderLineItemsDtoList().isEmpty()) {
                            log.warn("[Streams] Received empty order event for SKU {}. Skipping.", skuCode);
                            return;
                        }

                        OrderLineItemsDto item = orderEvent.getOrderLineItemsDtoList().get(0);
                        int requestedQuantity = item.getQuantity();
                        String orderNumber = orderEvent.getOrderNumber();
                        Integer currentStock = store.get(skuCode);

                        Object resultEvent;

                        if (currentStock == null) {
                            log.warn("[Streams] FAILED: SKU {} not found for Order {}.", skuCode, orderNumber);
                            resultEvent = new OrderFailedEvent(orderNumber, "SKU " + skuCode + " not found.");

                        } else if (currentStock >= requestedQuantity) {
                            store.put(skuCode, currentStock - requestedQuantity);
                            log.info("[Streams] SUCCESS: Stock reduced for {}. Order {}. New stock: {}",
                                    skuCode, orderNumber, currentStock - requestedQuantity);
                            resultEvent = new OrderValidatedEvent(orderNumber);

                        } else {
                            log.warn("[Streams] FAILED: Not enough stock for {}. Order {}. Requested: {}, Stock: {}",
                                    skuCode, orderNumber, requestedQuantity, currentStock);
                            resultEvent = new OrderFailedEvent(orderNumber, "Not enough stock for " + skuCode);
                        }

                        // Gửi kết quả (thành công hoặc thất bại) ra luồng tiếp theo
                        context.forward(record.withValue(resultEvent));
                    }

                    @Override
                    public void close() {}
                },
                INVENTORY_STORE_NAME
        );

        // --- BƯỚC 4: CHIA TÁCH VÀ GỬI KẾT QUẢ ---
        Map<String, KStream<String, Object>> branchedStreams = validationResultStream
                .split(Named.as("validation-branch-"))
                .branch((key, value) -> value instanceof OrderValidatedEvent, Branched.as("success"))
                .branch((key, value) -> value instanceof OrderFailedEvent, Branched.as("failed"))
                .noDefaultBranch();

        branchedStreams.get("validation-branch-success")
                .mapValues(value -> (OrderValidatedEvent) value)
                .to("order-validated-topic", Produced.with(Serdes.String(), orderValidatedSerde));

        branchedStreams.get("validation-branch-failed")
                .mapValues(value -> (OrderFailedEvent) value)
                .to("order-failed-topic", Produced.with(Serdes.String(), orderFailedSerde));

        return null;
    }
}