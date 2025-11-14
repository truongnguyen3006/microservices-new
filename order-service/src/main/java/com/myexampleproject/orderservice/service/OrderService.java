package com.myexampleproject.orderservice.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myexampleproject.common.event.*;
import com.myexampleproject.orderservice.config.CartMapper;
import com.myexampleproject.orderservice.dto.OrderResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.myexampleproject.common.event.InventoryCheckRequest;
import com.myexampleproject.common.event.InventoryCheckResult;
import org.springframework.data.redis.core.RedisTemplate; // <-- Bạn sẽ cần Redis
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.myexampleproject.common.dto.OrderLineItemsDto;
import com.myexampleproject.orderservice.dto.OrderRequest;
import com.myexampleproject.orderservice.model.Order;
import com.myexampleproject.orderservice.model.OrderLineItems;
import com.myexampleproject.orderservice.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // THÊM: Cần Redis để quản lý state của Saga
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String SAGA_PREFIX = "saga:order:";

    public void placeOrder(OrderRequest orderRequest) {
        String orderNumber = UUID.randomUUID().toString();
        log.info("Order {} received. Starting Inventory SAGA...", orderNumber);

        List<OrderLineItemsDto> items = orderRequest.getOrderLineItemsDtoList();

        // 1. Ghi lại "ý định" (intent) của Saga vào Redis
        // Chúng ta cần biết mình đang chờ bao nhiêu phản hồi
        Map<String, Object> sagaState = Map.of(
                "totalItems", items.size(),
                "receivedItems", 0,
                "failed", false,
                "request", orderRequest // Lưu lại request gốc
        );
        redisTemplate.opsForHash().putAll(SAGA_PREFIX + orderNumber, sagaState);
        redisTemplate.expire(SAGA_PREFIX + orderNumber, Duration.ofMinutes(10));

        // 2. Gửi sự kiện "OrderPlaced" (để lưu vào DB)
        // (Chúng ta vẫn cần làm việc này)
        OrderPlacedEvent placedEvent = new OrderPlacedEvent(orderNumber, items);
        kafkaTemplate.send("order-placed-topic", orderNumber, placedEvent);

        // 3. Gửi N tin nhắn "Kiểm tra kho" (Key-by-SKU)
        for (OrderLineItemsDto item : items) {
            InventoryCheckRequest checkRequest = new InventoryCheckRequest(orderNumber, item);

            // GỬI VỚI KEY LÀ SKUCODE
            kafkaTemplate.send("inventory-check-request-topic", item.getSkuCode(), checkRequest);
        }

        log.info("SAGA for Order {} started. {} check requests sent.", orderNumber, items.size());
        // Hàm này trả về HTTP 200 OK ngay lập tức.
    }

    // SAGA LISTENER: Lắng nghe kết quả từ Inventory
    // ==========================================================

    @KafkaListener(topics = "inventory-check-result-topic", groupId = "order-saga-group")
    public void handleInventoryCheckResult(List<ConsumerRecord<String, Object>> records) { // <-- SỬA 1: Nhận List
        log.info("SAGA: Received batch of {} inventory results", records.size());

        for (ConsumerRecord<String, Object> record : records) { // <-- SỬA 2: Thêm vòng lặp
            try {
                // SỬA 3: Deserialization thủ công
                Object payload = record.value();
                InventoryCheckResult result = objectMapper.convertValue(payload, InventoryCheckResult.class);

                // --- (Logic cũ của bạn bắt đầu từ đây) ---
                String orderNumber = result.getOrderNumber();
                String sagaKey = SAGA_PREFIX + orderNumber;

                log.info("SAGA: Processing inventory result for Order {}: SKU {} -> {}",
                        orderNumber, result.getItem().getSkuCode(), result.isSuccess());

                // Lấy state (Lưu ý: opsForHash() là an toàn, không cần @Transactional)
                long receivedCount = redisTemplate.opsForHash().increment(sagaKey, "receivedItems", 1);

                // Lấy trạng thái FAILED (nếu có)
                Object failedState = redisTemplate.opsForHash().get(sagaKey, "failed");
                boolean alreadyFailed = (failedState != null) && (boolean) failedState;

                // Nếu saga đã thất bại, chỉ cần bỏ qua các tin nhắn thành công còn lại
                if (alreadyFailed) {
                    log.warn("SAGA: Ignoring result for already failed order {}", orderNumber);
                    continue; // <-- SỬA 4: Dùng 'continue'
                }

                // Nếu item này thất bại
                if (!result.isSuccess()) {
                    redisTemplate.opsForHash().put(sagaKey, "failed", true);
                    // Gửi sự kiện Order FAILED (lý do đầu tiên)
                    kafkaTemplate.send("order-failed-topic", orderNumber, new OrderFailedEvent(orderNumber, result.getReason()));

                    // (Trong một hệ thống thật, chúng ta sẽ gửi lệnh "hoàn trả" ở đây)
                    continue; // <-- SỬA 5: Dùng 'continue'
                }

                // Kiểm tra xem đã nhận đủ kết quả chưa
                Object totalObj = redisTemplate.opsForHash().get(sagaKey, "totalItems");
                if (totalObj == null) {
                    log.error("SAGA: Không tìm thấy state 'totalItems' cho order {}. Bỏ qua.", orderNumber);
                    continue;
                }
                int totalItems = (int) totalObj;

                if (receivedCount == totalItems) {
                    // Đã nhận đủ VÀ không có cái nào FAILED
                    log.info("SAGA: Order {} is fully validated.", orderNumber);

                    // Gửi sự kiện Order VALIDATED
                    Object requestObj = redisTemplate.opsForHash().get(sagaKey, "request");
                    OrderRequest originalRequest = objectMapper.convertValue(requestObj, OrderRequest.class);

                    kafkaTemplate.send("order-validated-topic", orderNumber,
                            new OrderValidatedEvent(orderNumber, originalRequest.getOrderLineItemsDtoList()));

                    // Xóa Saga state
                    redisTemplate.delete(sagaKey);
                }
                // --- (Logic cũ của bạn kết thúc) ---

            } catch (Exception e) {
                log.error("SAGA: LỖI KHI XỬ LÝ InventoryCheckResult: {}. Sẽ KHÔNG retry.", record.key(), e);
            }
        }
    }

    @Transactional(readOnly = true) // Giao dịch chỉ đọc, nhanh hơn
    public OrderResponse getOrderDetails(String orderNumber) {
        log.info("Fetching order details for: {}", orderNumber);

        // 1. Tìm Order trong CSDL
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));

        // 2. Map từ Entity (Order) sang DTO (OrderResponse)
        return mapToOrderResponse(order);
    }

    /**
     * Helper: Chuyển đổi Entity Order -> DTO OrderResponse.
     */
    private OrderResponse mapToOrderResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .orderLineItemsList(order.getOrderLineItemsList()
                        .stream()
                        .map(this::mapToOrderLineItemsDto) // Tái sử dụng logic map
                        .toList())
                .build();
    }

    /**
     * Helper: Chuyển đổi Entity OrderLineItems -> DTO OrderLineItemsDto.
     * (Đây là logic ngược lại với hàm mapToDto bạn đã có)
     */
    private OrderLineItemsDto mapToOrderLineItemsDto(OrderLineItems orderLineItems) {
        return OrderLineItemsDto.builder()
                .id(orderLineItems.getId()) // Giả sử DTO của bạn cũng có Id
                .skuCode(orderLineItems.getSkuCode())
                .price(orderLineItems.getPrice())
                .quantity(orderLineItems.getQuantity())
                .build();
    }

    // ==========================================================
    // SỬA LỖI 1 TẠI ĐÂY
    // ==========================================================
    @KafkaListener(topics = "cart-checkout-topic", groupId = "order-updater-group")
    public void handleCartCheckout(List<ConsumerRecord<String, Object>> records) {
        log.info("Received a batch of {} cart-checkout events", records.size());

        for (ConsumerRecord<String, Object> record : records) {
            try {
                Object payload = record.value();
                log.info("Processing cart checkout for user: {}", record.key());

                CartCheckoutEvent event = objectMapper.convertValue(payload, CartCheckoutEvent.class);

                // Convert CartCheckoutEvent -> OrderRequest
                OrderRequest req = CartMapper.fromCart(event);

                placeOrder(req); // Gọi trực tiếp

            } catch (Exception e) {
                log.error("LỖI KHI XỬ LÝ CartCheckoutEvent: {}. Sẽ KHÔNG retry.", record.key(), e);
            }
        }
    }


    @KafkaListener(
            topics = {
                    "order-placed-topic",
                    "order-failed-topic",
            },
            containerFactory = "kafkaListenerContainerFactory" // <-- Dùng factory chung
    )
    public void handleOrderEvents(List<ConsumerRecord<String, Object>> records) {
        log.info("Received a batch of {} events", records.size());

        // Loop qua danh sách
        for (ConsumerRecord<String, Object> record : records) {
            String topic = record.topic();
            Object payload = record.value();
            log.debug("Processing event from topic [{}], key [{}]", topic, record.key());

            // Logic switch-case của bạn giữ nguyên
            try {
                switch (topic) {
                    case "order-placed-topic":
                        OrderPlacedEvent placedEvent = objectMapper.convertValue(payload, OrderPlacedEvent.class);
                        handleOrderPlacement(placedEvent); // Hàm private này giữ nguyên
                        break;

                    case "order-failed-topic":
                        OrderFailedEvent failedEvent = objectMapper.convertValue(payload, OrderFailedEvent.class);
                        handleOrderFailure(failedEvent); // Hàm private này giữ nguyên
                        break;

                    case "payment-processed-topic":
                        PaymentProcessedEvent processedEvent = objectMapper.convertValue(payload, PaymentProcessedEvent.class);
                        handlePaymentSuccess(processedEvent);
                        break;

                    case "payment-failed-topic":
                        PaymentFailedEvent paymentFailedEvent = objectMapper.convertValue(payload, PaymentFailedEvent.class);
                        handlePaymentFailure(paymentFailedEvent);
                        break;

                    default:
                        log.warn("Received message on unhandled topic: {}", topic);
                }
            } catch (Exception e) {
                log.error("LỖI KHI XỬ LÝ MESSAGE: {}. Sẽ KHÔNG retry.", record.key(), e);
            }
        }
    }

    public <T> T toEvent(Object payload, Class<T> clazz) {
        return objectMapper.convertValue(payload, clazz);
    }


    @KafkaListener(
            topics = "payment-validated-topic",
            groupId = "order-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleValidatedPayment(List<ConsumerRecord<String, Object>> records) {
        for (ConsumerRecord<String, Object> rec : records) {
            try {
                Object payload = rec.value(); // <-- LẤY GIÁ TRỊ
                PaymentProcessedEvent event =
                        objectMapper.convertValue(payload, PaymentProcessedEvent.class);
                handlePaymentSuccess(event);
            } catch (Exception e) {
                log.error("❌ Error converting/processing PaymentValidatedEvent at {}: {}",
                        rec.topic() + "-" + rec.partition() + "@" + rec.offset(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    protected void handleOrderPlacement(OrderPlacedEvent event) {
        log.info("Async Save: Saving Order {} to database...", event.getOrderNumber());

        Order order = new Order();
        order.setOrderNumber(event.getOrderNumber());
        List<OrderLineItems> orderLineItems = event.getOrderLineItemsDtoList()
                .stream()
                .map(this::mapToDto)
                .toList();
        order.setOrderLineItemsList(orderLineItems);
        order.setStatus("PENDING"); // Trạng thái PENDING ban đầu

        orderRepository.save(order);
        log.info("Async Save: Order {} saved to database.", event.getOrderNumber());
        // Sau khi lưu order vào DB
        OrderStatusEvent statusEvent = new OrderStatusEvent(event.getOrderNumber(), "PENDING");
        kafkaTemplate.send("order-status-topic", event.getOrderNumber(), statusEvent);
    }

    @Transactional
    protected void handleOrderFailure(OrderFailedEvent failedEvent) {
        log.info("Using OrderFailedEvent class: {}", failedEvent.getClass().getName());
        log.warn("INVENTORY FAILED: Received feedback for Order {}. Reason: {}",
                failedEvent.getOrderNumber(), failedEvent.getReason());

        Order order = orderRepository.findByOrderNumber(failedEvent.getOrderNumber())
                .orElseThrow(() -> new RuntimeException("Order not found: " + failedEvent.getOrderNumber()));
        if (order.getStatus().equals("PENDING")) {
            order.setStatus("FAILED");
            orderRepository.save(order);
            log.warn("Order {} status updated to FAILED due to inventory issue.", order.getOrderNumber());
            kafkaTemplate.send("order-status-topic", order.getOrderNumber(),
                    new OrderStatusEvent(order.getOrderNumber(), order.getStatus()));

        } else {
            log.warn("Received failure event for order {} but status was not PENDING (Status: {}).",
                    order.getOrderNumber(), order.getStatus());
        }
    }

    @Transactional
    protected void handlePaymentSuccess(PaymentProcessedEvent paymentProcessedEvent) {
        log.info("SUCCESS: Received PaymentProcessedEvent for Order {}. Payment ID: {}. Updating status...",
                paymentProcessedEvent.getOrderNumber(), paymentProcessedEvent.getPaymentId());

        // Không cần try-catch ở đây nữa vì đã có ở hàm listener chính
        Order order = orderRepository.findByOrderNumber(paymentProcessedEvent.getOrderNumber())
                .orElseThrow(() -> new RuntimeException("Order not found: " + paymentProcessedEvent.getOrderNumber()));

        if ("PENDING".equals(order.getStatus())) {
            order.setStatus("COMPLETED");
            orderRepository.save(order);
            log.info("Order {} status updated to COMPLETED.", order.getOrderNumber());
            kafkaTemplate.send("order-status-topic", order.getOrderNumber(),
                    new OrderStatusEvent(order.getOrderNumber(), order.getStatus()));
        } else {
            log.warn("Received payment success for order {} but status was not PENDING (Status: {}).",
                    order.getOrderNumber(), order.getStatus());
        }
    }

    @Transactional
    protected void handlePaymentFailure(PaymentFailedEvent paymentFailedEvent) {
        log.warn("FAILED: Received PaymentFailedEvent for Order {}. Reason: {}. Updating status...",
                paymentFailedEvent.getOrderNumber(), paymentFailedEvent.getReason());

        Order order = orderRepository.findByOrderNumber(paymentFailedEvent.getOrderNumber())
                .orElseThrow(() -> new RuntimeException("Order not found: " + paymentFailedEvent.getOrderNumber()));

        if ("PENDING".equals(order.getStatus())) {
            order.setStatus("PAYMENT_FAILED");
            orderRepository.save(order);
            log.warn("Order {} status updated to PAYMENT_FAILED.", order.getOrderNumber());
            kafkaTemplate.send("order-status-topic", order.getOrderNumber(),
                    new OrderStatusEvent(order.getOrderNumber(), order.getStatus()));
        } else {
            log.warn("Received payment failure for order {} but status was not PENDING (Status: {}).",
                    order.getOrderNumber(), order.getStatus());
        }
    }

    @KafkaListener(topics = "order-validated-topic", groupId = "order-group")
    public void handleValidated(List<ConsumerRecord<String, Object>> records) { // <-- SỬA 1: Nhận List
        log.info("SAGA SUCCESS: Received batch of {} validated events", records.size());

        for (ConsumerRecord<String, Object> record : records) { // <-- SỬA 2: Thêm vòng lặp
            try {
                // SỬA 3: Deserialization thủ công
                Object payload = record.value();
                OrderValidatedEvent event = objectMapper.convertValue(payload, OrderValidatedEvent.class);

                // --- (Logic cũ của bạn bắt đầu từ đây) ---
                log.info("SAGA SUCCESS: Order {} validated, updating status.", event.getOrderNumber());
                Order order = orderRepository.findByOrderNumber(event.getOrderNumber())
                        .orElseThrow(() -> new RuntimeException("Order not found: " + event.getOrderNumber()));

                if ("PENDING".equals(order.getStatus())) {
                    order.setStatus("VALIDATED"); // Trạng thái "đã xác thực kho"
                    orderRepository.save(order);
                    kafkaTemplate.send("order-status-topic",
                            order.getOrderNumber(),
                            new OrderStatusEvent(order.getOrderNumber(), "VALIDATED")
                    );

                    // (Bạn có thể kích hoạt payment-service từ đây nếu muốn)

                } else {
                    log.warn("Received validated event for order {} but status was not PENDING (Status: {}).",
                            order.getOrderNumber(), order.getStatus());
                }
                // --- (Logic cũ kết thúc) ---

            } catch (Exception e) {
                log.error("SAGA: LỖI KHI XỬ LÝ OrderValidatedEvent: {}. Sẽ KHÔNG retry.", record.key(), e);
            }
        }
    }


    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }
}