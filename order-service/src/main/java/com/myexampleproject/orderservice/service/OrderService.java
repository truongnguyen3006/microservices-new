package com.myexampleproject.orderservice.service;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myexampleproject.orderservice.event.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.myexampleproject.orderservice.dto.OrderLineItemsDto;
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

    // Hàm này bây giờ CỰC NHANH - chỉ làm 1 việc là gửi Kafka.
    public void placeOrder(OrderRequest orderRequest) {

        // 1. Vẫn tạo OrderNumber
        String orderNumber = UUID.randomUUID().toString();

        log.info("Order {} received. Sending processing events...", orderNumber);

        // 2. Gửi TẤT CẢ sự kiện xử lý (trừ kho)
        for (OrderLineItemsDto item : orderRequest.getOrderLineItemsDtoList()) {
            OrderProcessingEvent itemEvent = new OrderProcessingEvent(
                    orderNumber,
                    List.of(item) // Chỉ chứa 1 món hàng
            );
            kafkaTemplate.send("order-processing-topic", item.getSkuCode(), itemEvent);
        }

        // 3. THÊM MỚI: Gửi sự kiện "OrderPlaced"
        // Sự kiện này dùng để BÊN TRONG service này tự bắt lấy và LƯU vào CSDL
        OrderPlacedEvent placedEvent = new OrderPlacedEvent(
                orderNumber,
                orderRequest.getOrderLineItemsDtoList() // Gửi toàn bộ DTO
        );
        kafkaTemplate.send("order-placed-topic", orderNumber, placedEvent);

        // 4. BỎ: Không save CSDL ở đây nữa
        // orderRepository.save(order);

        log.info("All events for Order {} sent.", orderNumber);
        // Hàm này kết thúc và trả về 200 OK cho người dùng NGAY LẬP TỨC
    }

    @KafkaListener(
            topics = {
                    "order-placed-topic",
                    "order-failed-topic",
                    "payment-processed-topic",
                    "payment-failed-topic"
            },
            containerFactory = "kafkaListenerContainerFactory" // <-- Dùng factory chung
    )
    @Transactional // Đặt Transactional ở đây để bao bọc TẤT CẢ logic
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
                // Khi xử lý batch, 1 lỗi có thể làm toàn bộ batch bị retry
                // Tạm thời log lỗi và cho qua message tiếp theo
                log.error("Error processing individual event in batch from topic {} for key {}: ", topic, record.key(), e);
                // Hoặc ném cả RuntimeException để retry toàn bộ batch
                // throw new RuntimeException("Failed to process batch, will retry.", e);
            }
        }
        // Transaction sẽ commit 1 lần duy nhất ở đây
        log.info("Batch of {} events processed.", records.size());
    }


    // === CÁC HÀM XỬ LÝ (PRIVATE) ===
    // (Bỏ @KafkaListener và @Transactional khỏi các hàm này)

    private void handleOrderPlacement(OrderPlacedEvent event) {
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
    }

    private void handleOrderFailure(OrderFailedEvent failedEvent) {
        log.info("Using OrderFailedEvent class: {}", failedEvent.getClass().getName());
        log.warn("INVENTORY FAILED: Received feedback for Order {}. Reason: {}",
                failedEvent.getOrderNumber(), failedEvent.getReason());

        Order order = orderRepository.findByOrderNumber(failedEvent.getOrderNumber())
                .orElseThrow(() -> new RuntimeException("Order not found: " + failedEvent.getOrderNumber()));
        if (order.getStatus().equals("PENDING")) {
            order.setStatus("FAILED");
            orderRepository.save(order);
            log.warn("Order {} status updated to FAILED due to inventory issue.", order.getOrderNumber());
        } else {
            log.warn("Received failure event for order {} but status was not PENDING (Status: {}).",
                    order.getOrderNumber(), order.getStatus());
        }
    }

    private void handlePaymentSuccess(PaymentProcessedEvent paymentProcessedEvent) {
        log.info("SUCCESS: Received PaymentProcessedEvent for Order {}. Payment ID: {}. Updating status...",
                paymentProcessedEvent.getOrderNumber(), paymentProcessedEvent.getPaymentId());

        // Không cần try-catch ở đây nữa vì đã có ở hàm listener chính
        Order order = orderRepository.findByOrderNumber(paymentProcessedEvent.getOrderNumber())
                .orElseThrow(() -> new RuntimeException("Order not found: " + paymentProcessedEvent.getOrderNumber()));

        if ("PENDING".equals(order.getStatus())) {
            order.setStatus("COMPLETED");
            orderRepository.save(order);
            log.info("Order {} status updated to COMPLETED.", order.getOrderNumber());
        } else {
            log.warn("Received payment success for order {} but status was not PENDING (Status: {}).",
                    order.getOrderNumber(), order.getStatus());
        }
    }

    private void handlePaymentFailure(PaymentFailedEvent paymentFailedEvent) {
        log.warn("FAILED: Received PaymentFailedEvent for Order {}. Reason: {}. Updating status...",
                paymentFailedEvent.getOrderNumber(), paymentFailedEvent.getReason());

        Order order = orderRepository.findByOrderNumber(paymentFailedEvent.getOrderNumber())
                .orElseThrow(() -> new RuntimeException("Order not found: " + paymentFailedEvent.getOrderNumber()));

        if ("PENDING".equals(order.getStatus())) {
            order.setStatus("PAYMENT_FAILED");
            orderRepository.save(order);
            log.warn("Order {} status updated to PAYMENT_FAILED.", order.getOrderNumber());
        } else {
            log.warn("Received payment failure for order {} but status was not PENDING (Status: {}).",
                    order.getOrderNumber(), order.getStatus());
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