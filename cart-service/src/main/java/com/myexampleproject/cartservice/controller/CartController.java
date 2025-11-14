package com.myexampleproject.cartservice.controller;

import com.myexampleproject.common.event.CartLineItem;
import com.myexampleproject.cartservice.model.CartEntity;
import com.myexampleproject.cartservice.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PostMapping("/add/{userId}")
    public ResponseEntity<?> addToCart(@PathVariable String userId, @RequestBody CartLineItem item) {
        CartEntity cart = cartService.addItem(userId, item);
        return ResponseEntity.ok(cart);
    }

    @PostMapping("/remove/{userId}/{sku}")
    public ResponseEntity<?> remove(@PathVariable String userId, @PathVariable String sku) {
        CartEntity cart = cartService.removeItem(userId, sku);
        return ResponseEntity.ok(cart);
    }

    @GetMapping("/view/{userId}")
    public ResponseEntity<?> view(@PathVariable String userId) {
        CartEntity cart = cartService.viewCart(userId);
        return ResponseEntity.ok(cart);
    }

    @PostMapping("/checkout/{userId}")
    public ResponseEntity<?> checkout(@PathVariable String userId) {
        cartService.checkout(userId);
        return ResponseEntity.accepted().body("Checkout queued");
    }
}
