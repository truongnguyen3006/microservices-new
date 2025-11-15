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
import org.springframework.kafka.annotation.KafkaListener;
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
    // THÊM: Định nghĩa Group ID cho listener dọn dẹp
    private static final String CART_CLEANER_GROUP_ID = "cart-cleaner-group";

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

    public void checkout(String userId) {
        // 2. SỬA: Đọc từ cache/DB (dùng hàm viewCart)
        // (Không cần đọc từ DB, dùng hàm viewCart đã tối ưu cache)
        CartEntity cart = this.viewCart(userId);

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
    }

    // THÊM MỚI: KAFKA LISTENER ĐỂ DỌN DẸP
    // ==========================================================

    /**
     * Listener này chạy bất đồng bộ, lắng nghe chính topic "cart-checkout-topic"
     * nhưng với một group-id khác.
     * Nhiệm vụ của nó là dọn dẹp CSDL và Redis sau khi user đã checkout.
     */
    @KafkaListener(topics = CHECKOUT_TOPIC, groupId = CART_CLEANER_GROUP_ID)
    @Transactional // <-- Thêm @Transactional vào đây
    public void handleCheckoutCleanup(CartCheckoutEvent event) {
        String userId = event.getUserId();
        log.info("CLEANUP: Bắt đầu dọn dẹp giỏ hàng cho user {}", userId);

        try {
            // 1. Xóa trong CSDL (Source of truth)
            cartRepository.deleteById(userId);

            // 2. Xóa trong Redis cache
            redisTemplate.delete(REDIS_CART_PREFIX + userId);

            log.info("CLEANUP: Đã dọn dẹp giỏ hàng (DB & Redis) cho user {}", userId);

        } catch (Exception e) {
            log.error("CLEANUP FAILED: Lỗi khi dọn dẹp giỏ hàng cho user {}: {}", userId, e.getMessage(), e);
            // Ném lại lỗi để cơ chế retry của Kafka (nếu có) hoạt động
            throw e;
        }
    }
}