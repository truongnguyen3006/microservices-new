package com.myexampleproject.cartservice.service;

import com.myexampleproject.common.event.*;
import com.myexampleproject.cartservice.model.CartEntity;
import com.myexampleproject.cartservice.model.CartItemEntity;
import com.myexampleproject.cartservice.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, CartCheckoutEvent> kafkaTemplate;

    private static final String REDIS_CART_PREFIX = "cart:";
    private static final Duration REDIS_TTL = Duration.ofHours(24);
    private static final String CHECKOUT_TOPIC = "cart-checkout-topic";

    public CartEntity getCartFromCacheOrDb(String userId) {
        String key = REDIS_CART_PREFIX + userId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof CartEntity) {
            return (CartEntity) cached;
        }
        Optional<CartEntity> db = cartRepository.findById(userId);
        if (db.isPresent()) {
            redisTemplate.opsForValue().set(key, db.get(), REDIS_TTL);
            return db.get();
        }
        return CartEntity.builder().userId(userId).items(new ArrayList<>()).build();
    }

    @Transactional
    public CartEntity addItem(String userId, CartLineItem line) {
        CartEntity cart = cartRepository.findById(userId).orElseGet(() -> CartEntity.builder().userId(userId).items(new ArrayList<>()).build());

        Optional<CartItemEntity> exists = cart.getItems().stream()
                .filter(i -> i.getSkuCode().equals(line.getSkuCode()))
                .findFirst();

        if (exists.isPresent()) {
            CartItemEntity e = exists.get();
            e.setQuantity(e.getQuantity() + line.getQuantity());
            e.setPrice(line.getPrice());
        } else {
            CartItemEntity item = CartItemEntity.builder()
                    .skuCode(line.getSkuCode())
                    .quantity(line.getQuantity())
                    .price(line.getPrice())
                    .build();
            cart.getItems().add(item);
        }

        CartEntity saved = cartRepository.save(cart);
        // write-through cache
        redisTemplate.opsForValue().set(REDIS_CART_PREFIX + userId, saved, REDIS_TTL);
        return saved;
    }

    @Transactional
    public CartEntity removeItem(String userId, String sku) {
        CartEntity cart = cartRepository.findById(userId).orElse(null);
        if (cart == null) return null;
        cart.getItems().removeIf(i -> i.getSkuCode().equals(sku));
        CartEntity saved = cartRepository.save(cart);
        redisTemplate.opsForValue().set(REDIS_CART_PREFIX + userId, saved, REDIS_TTL);
        return saved;
    }

    public CartEntity viewCart(String userId) {
        return getCartFromCacheOrDb(userId);
    }

    @Transactional
    public void checkout(String userId) {
        // read from DB (single source of truth)
        CartEntity cart = cartRepository.findById(userId).orElse(null);
        if (cart == null || cart.getItems().isEmpty()) {
            throw new IllegalStateException("Cart empty");
        }

        List<CartLineItem> items = cart.getItems().stream()
                .map(i -> new CartLineItem(i.getSkuCode(), i.getQuantity(), i.getPrice()))
                .collect(Collectors.toList());

        CartCheckoutEvent event = new CartCheckoutEvent(userId, items);

        // send to Kafka with key = userId
        kafkaTemplate.send(CHECKOUT_TOPIC, userId, event).whenComplete((md, ex) -> {
            if (ex != null) {
                log.error("Failed to send checkout event for user {}: {}", userId, ex.getMessage());
            } else {
                log.info("Checkout event sent for user {} partition={}", userId, md.getRecordMetadata().partition());
            }
        });

        // remove cart (write-through)
        cartRepository.deleteById(userId);
        redisTemplate.delete(REDIS_CART_PREFIX + userId);
    }
}