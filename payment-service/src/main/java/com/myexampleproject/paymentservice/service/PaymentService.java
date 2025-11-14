package com.myexampleproject.paymentservice.service;

import com.myexampleproject.common.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    // Listener để nhận sự kiện từ Inventory Service
    @KafkaListener(
            topics = "order-validated-topic",
            groupId = "payment-group",
            containerFactory = "paymentKafkaListenerContainerFactory"
    )
    public void handleOrderValidation(OrderValidatedEvent orderValidatedEvent) {
        log.info("Received OrderValidatedEvent for Order {}. Processing payment...",
                orderValidatedEvent.getOrderNumber());

        // --- GIẢ LẬP XỬ LÝ THANH TOÁN ---
        boolean paymentSuccess = processPayment(orderValidatedEvent); //
        if (paymentSuccess) {
            // Thanh toán thành công -> Gửi PaymentProcessedEvent
            String paymentId = UUID.randomUUID().toString(); // Tạo ID thanh toán giả
            PaymentProcessedEvent successEvent = new PaymentProcessedEvent(
                    orderValidatedEvent.getOrderNumber(),
                    paymentId
            );
            kafkaTemplate.send("payment-processed-topic", orderValidatedEvent.getOrderNumber(), successEvent); // Gửi tới topic mới
            log.info("Payment SUCCESS for Order {}. Payment ID: {}",
                    orderValidatedEvent.getOrderNumber(), paymentId);
        } else {
            // Thanh toán thất bại -> Gửi PaymentFailedEvent
            PaymentFailedEvent failedEvent = new PaymentFailedEvent(
                    orderValidatedEvent.getOrderNumber(),
                    "Payment gateway declined." // Lý do giả lập
            );
            kafkaTemplate.send("payment-failed-topic",orderValidatedEvent.getOrderNumber(), failedEvent); // Gửi tới topic mới
            log.warn("Payment FAILED for Order {}. Reason: {}",
                    orderValidatedEvent.getOrderNumber(), failedEvent.getReason());
        }
    }

    private boolean processPayment(OrderValidatedEvent event) {
        log.info("Simulating payment processing for Order {}...", event.getOrderNumber());
        // Thêm logic phức tạp hơn nếu muốn (ví dụ: random thành công/thất bại)
        return true; // Luôn trả về thành công cho đơn giản
    }
}
