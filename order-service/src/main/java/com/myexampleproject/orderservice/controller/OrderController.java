package com.myexampleproject.orderservice.controller;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.myexampleproject.orderservice.dto.OrderRequest;
import com.myexampleproject.orderservice.service.OrderService;

import lombok.RequiredArgsConstructor;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @CircuitBreaker(name = "inventory", fallbackMethod = "fallbackMethod")
    @TimeLimiter(name = "inventory")
    @Retry(name = "inventory")
    public CompletableFuture<String> placeOrder(@RequestBody OrderRequest orderRequest) {
        log.info("Placing Order");
        return CompletableFuture.supplyAsync(()->orderService.placeOrder(orderRequest));
    }

    public CompletableFuture<String> fallbackMethod(OrderRequest orderRequest,  Throwable throwable) {
        log.info("Cannot Place Order Executing Fallback logic");
        return CompletableFuture.supplyAsync(()->"Oops! Something went wrong, please order after some time!");
    }
}
