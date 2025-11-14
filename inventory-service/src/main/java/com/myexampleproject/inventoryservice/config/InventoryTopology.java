package com.myexampleproject.inventoryservice.config;

import com.myexampleproject.common.dto.OrderLineItemsDto;
import com.myexampleproject.common.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp; // <-- THÊM IMPORT NÀY

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryTopology {

    public static final String INVENTORY_STORE = "inventory-store";

    private final SerdeConfig serdeConfig;

    @Autowired
    public void buildTopology(StreamsBuilder builder) {

        var stringSerde = Serdes.String();
        var intSerde = Serdes.Integer();

        var productSerde = serdeConfig.jsonSchemaSerde(ProductCreatedEvent.class);
        var adjustSerde = serdeConfig.jsonSchemaSerde(InventoryAdjustmentEvent.class);
        var checkRequestSerde = serdeConfig.jsonSchemaSerde(InventoryCheckRequest.class);
        var checkResultSerde = serdeConfig.jsonSchemaSerde(InventoryCheckResult.class);

        // ==========================================================
        // BUILDER A: Xây dựng KTable (Kho)
        // (Không thay đổi)
        // ==========================================================

        KStream<String, Integer> productStream = builder
                .stream("product-created-topic", Consumed.with(stringSerde, productSerde))
                .map((key, event) -> KeyValue.pair(event.getSkuCode(), Math.max(0, event.getInitialQuantity())))
                .repartition(Repartitioned.with(stringSerde, intSerde).withName("product-repartition-by-sku"));

        KStream<String, Integer> adjustStream = builder
                .stream("inventory-adjustment-topic", Consumed.with(stringSerde, adjustSerde))
                .mapValues(InventoryAdjustmentEvent::getAdjustmentQuantity)
                .repartition(Repartitioned.with(stringSerde, intSerde).withName("adjust-repartition-by-sku"));

        KStream<String, Integer> inventoryChanges = productStream.merge(adjustStream);

        inventoryChanges
                .groupByKey(Grouped.with(stringSerde, intSerde))
                .aggregate(
                        () -> 0,
                        (sku, change, currentStock) -> {
                            long newStock = (long) currentStock + change;
                            int finalStock = (int) Math.max(0, Math.min(newStock, Integer.MAX_VALUE));
                            log.info("AGGREGATE STOCK → {} ({} + {}) = {}", sku, currentStock, change, finalStock);
                            return finalStock;
                        },
                        // QUAN TRỌNG: Chúng ta phải dùng Serde<Integer> cho KTable
                        Materialized.<String, Integer, KeyValueStore<Bytes, byte[]>>as(INVENTORY_STORE)
                                .withKeySerde(stringSerde)
                                .withValueSerde(intSerde) // <-- Value là Integer
                );

        // ==========================================================
        // BUILDER B: Xử lý Đơn hàng (SAGA)
        // (SỬA LỖI ClassCastException TẠI ĐÂY)
        // ==========================================================

        builder.stream("inventory-check-request-topic", Consumed.with(stringSerde, checkRequestSerde))
                .transform(
                        () -> new Transformer<String, InventoryCheckRequest, KeyValue<String, InventoryCheckResult>>() {

                            // SỬA LỖI 1: Store phải là <String, ValueAndTimestamp<Integer>>
                            private KeyValueStore<String, ValueAndTimestamp<Integer>> store;
                            private ProcessorContext context;

                            @Override
                            public void init(ProcessorContext context) {
                                this.context = context;
                                // Kafka tự động cast về đúng kiểu
                                this.store = context.getStateStore(INVENTORY_STORE);
                            }

                            @Override
                            public KeyValue<String, InventoryCheckResult> transform(String skuCode, InventoryCheckRequest request) {
                                OrderLineItemsDto item = request.getItem();
                                String orderNumber = request.getOrderNumber();
                                String reason = null;
                                boolean success = false;

                                // SỬA LỖI 2: Đọc 'Value' từ 'ValueAndTimestamp'
                                ValueAndTimestamp<Integer> stockWithTimestamp = store.get(skuCode);
                                Integer currentStock = (stockWithTimestamp != null) ? stockWithTimestamp.value() : 0;
                                // (Thêm 1 lần check null an toàn)
                                if (currentStock == null) currentStock = 0;


                                if (currentStock < item.getQuantity()) {
                                    // Thất bại
                                    reason = "Not enough stock for " + skuCode + " (need " + item.getQuantity() + ", have " + currentStock + ")";
                                    log.warn("INVENTORY CHECK FAILED → Order {}: {}", orderNumber, reason);
                                    success = false;
                                } else {
                                    // Thành công -> TRỪ KHO NGAY
                                    int newStock = currentStock - item.getQuantity();

                                    // SỬA LỖI 3: Ghi lại (put) cũng phải dùng ValueAndTimestamp
                                    store.put(skuCode, ValueAndTimestamp.make(newStock, context.timestamp()));

                                    log.info("INVENTORY COMMIT (SAGA) → {} ({} → {})", skuCode, currentStock, newStock);
                                    success = true;
                                }

                                // Gửi kết quả (key=orderNumber)
                                return KeyValue.pair(
                                        orderNumber,
                                        new InventoryCheckResult(orderNumber, item, success, reason)
                                );
                            }

                            @Override
                            public void close() {}
                        },
                        INVENTORY_STORE
                )
                .to("inventory-check-result-topic", Produced.with(stringSerde, checkResultSerde));


        log.info("=== INVENTORY TOPOLOGY (SAGA - Repartitioned - TS Fixed) LOADED OK ===");
    }
}