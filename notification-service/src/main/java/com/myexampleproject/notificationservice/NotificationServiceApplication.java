package com.myexampleproject.notificationservice;

import com.myexampleproject.notificationservice.event.PaymentFailedEvent;
import com.myexampleproject.notificationservice.event.PaymentProcessedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.KafkaListener;

// THÊM IMPORT NÀY:
import org.springframework.messaging.handler.annotation.Payload;


@SpringBootApplication
@Slf4j
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}