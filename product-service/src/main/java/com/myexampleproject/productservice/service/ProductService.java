package com.myexampleproject.productservice.service;

import java.util.List;
import java.util.stream.Collectors; // <-- Import thêm

import com.myexampleproject.common.event.*;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.myexampleproject.productservice.dto.ProductRequest;
import com.myexampleproject.productservice.dto.ProductResponse;
import com.myexampleproject.productservice.model.Product;
import com.myexampleproject.productservice.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    private final ProductRepository productRepository;
    private final KafkaTemplate<String, ProductCreatedEvent> productCreatedKafka;
    private final KafkaTemplate<String, ProductCacheEvent> productCacheKafka;

    // Đổi tên cache sang _v5 cho sạch sẽ
    @CacheEvict(cacheNames = "products_json_v5", allEntries = true)
    public ProductResponse createProduct(ProductRequest productRequest) {
        Product product = Product.builder()
                .name(productRequest.getName())
                .description(productRequest.getDescription())
                .price(productRequest.getPrice())
                .skuCode(productRequest.getSkuCode())
                .category(productRequest.getCategory())
                .imageUrl(productRequest.getImageUrl())
                .build();
        Product savedProduct = productRepository.save(product);
        log.info("Product {} saved locally.", savedProduct.getId());

        ProductCreatedEvent event = ProductCreatedEvent.builder()
                .skuCode(savedProduct.getSkuCode())
                .initialQuantity(productRequest.getInitialQuantity())
                .build();
        productCreatedKafka.send("product-created-topic", event.getSkuCode(), event);

        ProductCacheEvent cacheEvent = ProductCacheEvent.builder()
                .skuCode(savedProduct.getSkuCode())
                .name(savedProduct.getName())
                .price(savedProduct.getPrice())
                .imageUrl(savedProduct.getImageUrl())
                .build();
        productCacheKafka.send("product-cache-update-topic", cacheEvent.getSkuCode(), cacheEvent);

        return mapToProductResponse(savedProduct);
    }

    @Cacheable(cacheNames = "products_json_v5")
    public List<ProductResponse> getAllProducts() {
        List<Product> products = productRepository.findAll();
        // QUAN TRỌNG: Dùng .collect(Collectors.toList()) thay vì .toList()
        // Lý do: .toList() trả về ImmutableList (Final Class) -> Jackson không ghi Type Info được -> Lỗi Redis.
        // Collectors.toList() trả về ArrayList (Non-Final) -> Hoạt động tốt.
        return products.stream()
                .map(this::mapToProductResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(cacheNames = "product_item_json_v5", key = "#id")
    public ProductResponse getProductById(Long id){
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        return mapToProductResponse(product);
    }

    // SỬA LỖI LOGIC CACHE Ở ĐÂY
    @Caching(evict = {
            @CacheEvict(cacheNames = "product_item_json_v5", key = "#id"), // Xóa cache chi tiết
            @CacheEvict(cacheNames = "products_json_v5", allEntries = true) // SỬA: Xóa cache danh sách (Lúc trước bạn để nhầm tên cache chi tiết)
    })
    public void deleteProductById(Long id){
        if(!productRepository.existsById(id)){
            throw new RuntimeException("Product not found");
        }
        productRepository.deleteById(id);
    }

    private ProductResponse mapToProductResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .skuCode(product.getSkuCode())
                .imageUrl(product.getImageUrl())
                .build();
    }
}