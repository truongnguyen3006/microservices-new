package com.myexampleproject.notificationservice.service;

import com.myexampleproject.common.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    // Inject template để gửi WebSocket
    private final SimpMessagingTemplate messagingTemplate;

    @KafkaListener(
            topics = "order-placed-topic",
            groupId = "notification-group",
            containerFactory = "orderPlacedKafkaListenerContainerFactory"
    )
    public void handleOrderPlaced(@Payload OrderPlacedEvent event) {
        log.info("Order placed: {}", event.getOrderNumber());

        // Gửi thông báo realtime
        // Topic: /topic/order/{orderNumber}
        messagingTemplate.convertAndSend("/topic/order/" + event.getOrderNumber(),
                Map.of("status", "PENDING", "message", "Đơn hàng đã được tiếp nhận!"));
    }

    @KafkaListener(
            topics = "payment-processed-topic",
            groupId = "notification-group",
            containerFactory = "paymentProcessedKafkaListenerContainerFactory"
    )
    public void handlePaymentSuccess(@Payload PaymentProcessedEvent event) {
        log.info("Payment success for order {} (paymentId={})",
                event.getOrderNumber(), event.getPaymentId());
        messagingTemplate.convertAndSend("/topic/order/" + event.getOrderNumber(),
                Map.of("status", "COMPLETED", "message", "Thanh toán thành công! Đơn hàng hoàn tất."));
    }

    // --- 3. KHI CÓ LỖI (Hết hàng hoặc Lỗi thanh toán) ---
    // Gộp chung handleOrderFailed và PaymentFailed lại cho gọn
    @KafkaListener(topics = {
            "order-failed-topic",
            "payment-failed-topic"},
            groupId = "notification-group")
    public void handleFailures(@Payload Object event) {
        String orderNumber = "";
        String reason = "";

        if (event instanceof OrderFailedEvent) {
            orderNumber = ((OrderFailedEvent) event).getOrderNumber();
            reason = ((OrderFailedEvent) event).getReason();
        } else if (event instanceof PaymentFailedEvent) {
            orderNumber = ((PaymentFailedEvent) event).getOrderNumber();
            reason = ((PaymentFailedEvent) event).getReason();
        }

        log.warn("Order Failed Notification for {}: {}", orderNumber, reason);

        // Gửi thông báo lỗi xuống Frontend
        messagingTemplate.convertAndSend("/topic/order/" + orderNumber,
                Map.of("status", "FAILED", "message", "Đơn hàng thất bại: " + reason));
    }
}
