package com.myexampleproject.productservice.controller;

import java.util.List;

import com.myexampleproject.common.event.ProductCacheEvent;
import com.myexampleproject.common.event.ProductCreatedEvent;
import com.myexampleproject.productservice.model.Product;
import com.myexampleproject.productservice.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import com.myexampleproject.productservice.dto.ProductRequest;
import com.myexampleproject.productservice.dto.ProductResponse;
import com.myexampleproject.productservice.service.ProductService;

import lombok.RequiredArgsConstructor;
//method trong class sẽ trả về JSON hoặc XML (không trả về view)
@RestController
//mapping URL đến method hoặc class
@RequestMapping("/api/product")
@RequiredArgsConstructor
@Slf4j
public class ProductController {
	
	private final ProductService productService;
	
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProductResponse createProduct(@RequestBody ProductRequest productRequest) {
        return productService.createProduct(productRequest);
	}
	
	@GetMapping
	@ResponseStatus(HttpStatus.OK)
	public List<ProductResponse> getAllProducts(){
		return productService.getAllProducts();
	}

    @GetMapping("/{id}")
    public ProductResponse getProductById(@PathVariable Long id){
        return productService.getProductById(id);
    }

    @DeleteMapping("/{id}")
    public void deleteProductById(@PathVariable Long id){
        productService.deleteProductById(id);
    }

    // Inject thêm 2 bean này vào Controller (nếu chưa có)
    private final ProductRepository productRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Trong ProductController.java

    // Trong ProductController.java

    @GetMapping("/admin/warm-cache")
    public String warmProductCache() {
        log.info("Starting FULL cache warm-up...");

        // 1. QUAN TRỌNG: Xóa cache cũ của chính Product Service để Frontend thấy dữ liệu mới
        productService.clearProductListCache();

        // 2. Logic cũ (Gửi Kafka cho các service khác) giữ nguyên
        List<Product> allProducts = productRepository.findAll();
        int count = 0;

        for (Product product : allProducts) {
            // Gửi Event cập nhật giá cho Order Service
            ProductCacheEvent cacheEvent = ProductCacheEvent.builder()
                    .skuCode(product.getSkuCode())
                    .name(product.getName())
                    .price(product.getPrice())
                    .imageUrl(product.getImageUrl())
                    .build();
            kafkaTemplate.send("product-cache-update-topic", product.getSkuCode(), cacheEvent);

            // Gửi Event cập nhật kho cho Inventory Service
            ProductCreatedEvent inventoryEvent = ProductCreatedEvent.builder()
                    .skuCode(product.getSkuCode())
                    .initialQuantity(1000)
                    .build();
            kafkaTemplate.send("product-created-topic", product.getSkuCode(), inventoryEvent);

            count++;
        }

        return "Đã xóa cache cũ và gửi " + count + " sản phẩm sang Kafka.";
    }
}
