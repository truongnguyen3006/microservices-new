package com.myexampleproject.notificationservice.service;

import com.myexampleproject.notificationservice.event.OrderPlacedEvent;
import com.myexampleproject.notificationservice.event.PaymentFailedEvent;
import com.myexampleproject.notificationservice.event.PaymentProcessedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    @KafkaListener(
            topics = "order-placed-topic",
            groupId = "notification-group",
            containerFactory = "orderPlacedKafkaListenerContainerFactory"
    )
    public void handleOrderPlaced(@Payload OrderPlacedEvent event) {
        log.info("Order placed: {}", event.getOrderNumber());
    }

    @KafkaListener(
            topics = "payment-processed-topic",
            groupId = "notification-group",
            containerFactory = "paymentProcessedKafkaListenerContainerFactory"
    )
    public void handlePaymentSuccess(@Payload PaymentProcessedEvent event) {
        log.info("Payment success for order {} (paymentId={})",
                event.getOrderNumber(), event.getPaymentId());
    }

    @KafkaListener(
            topics = "payment-failed-topic",
            groupId = "notification-group",
            containerFactory = "paymentFailedKafkaListenerContainerFactory"
    )
    public void handlePaymentFailed(@Payload PaymentFailedEvent event) {
        log.warn("Payment failed for order {}: {}", event.getOrderNumber(), event.getReason());
    }
}
