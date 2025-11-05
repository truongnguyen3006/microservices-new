package com.myexampleproject.paymentservice.controller;

import com.myexampleproject.paymentservice.event.OrderValidatedEvent;
import com.myexampleproject.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment") // Đặt một đường dẫn riêng cho test
@RequiredArgsConstructor
@Slf4j
public class PaymentController {
    private final PaymentService paymentService;
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public String triggerPaymentProcessing(@RequestBody OrderValidatedEvent testEvent) {
        log.info("Received test trigger for Order: {}", testEvent.getOrderNumber());
        try {
            // Gọi trực tiếp hàm xử lý Kafka listener
            paymentService.handleOrderValidation(testEvent);
            return "Test event processed successfully.";
        } catch (Exception e) {
            log.error("Error during test trigger for Order {}: ", testEvent.getOrderNumber(), e);
            return "Error processing test event: " + e.getMessage();
        }
    }
}