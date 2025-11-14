package com.myexampleproject.productservice.service;

import java.util.List;

import com.myexampleproject.common.event.*;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.myexampleproject.productservice.dto.ProductRequest;
import com.myexampleproject.productservice.dto.ProductResponse;
import com.myexampleproject.productservice.model.Product;
import com.myexampleproject.productservice.repository.ProductRepository;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
//tạo hàm xây dựng cho biến final hoặc nonnull
@RequiredArgsConstructor
//tạo một logger
@Slf4j
public class ProductService {
	private final ProductRepository productRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
//    "Evict" là "xóa bỏ"
//Khi bạn tạo sản phẩm mới
// Danh sách getAllProducts cũ trong cache (products) bị sai (vì thiếu sản phẩm mới).
//Annotation này ra lệnh: "Sau khi hàm createProduct chạy xong,
// hãy xóa sạch (allEntries = true) mọi thứ trong ngăn products."
// Lần gọi getAllProducts tiếp theo sẽ phải query lại DB (lấy danh sách mới) và cache lại.
    @CacheEvict(cacheNames = "products", allEntries = true)
	public ProductResponse createProduct(ProductRequest productRequest) {
        Product product = Product.builder()
				.name(productRequest.getName())
				.description(productRequest.getDescription())
				.price(productRequest.getPrice())
                .skuCode(productRequest.getSkuCode())
				.build();
        Product savedProduct = productRepository.save(product);
        log.info("Product {} saved locally.", savedProduct.getId());
        // 2. --- LOGIC MỚI: BẮN SỰ KIỆN KAFKA ---
        // Tạo sự kiện chỉ chứa thông tin inventory cần
        ProductCreatedEvent event = ProductCreatedEvent.builder()
                .skuCode(savedProduct.getSkuCode())
                .initialQuantity(productRequest.getInitialQuantity())
                .build();

        // Gửi sự kiện tới topic "product-created-topic"
        kafkaTemplate.send("product-created-topic", event.getSkuCode(), event);

        log.info("ProductCreatedEvent sent for skuCode: {}", event.getSkuCode());

        // 3. Trả về Response
		return mapToProductResponse(savedProduct);
	}

//    tạo một "ngăn kéo" cache tên là products
    @Cacheable(cacheNames = "products")
	public List<ProductResponse> getAllProducts() {
        // Lần đầu chạy, code này sẽ thực thi và query DB
        // Lần thứ 2 trở đi, nó sẽ trả về kết quả từ Redis ngay lập tức
		List<Product> products = productRepository.findAll();
		return products.stream().map(this::mapToProductResponse).toList();
	}

//   Dùng tham số id làm key
    @Cacheable(cacheNames = "product", key = "#id") // <-- Cache theo ID
    public ProductResponse getProductById(Long id){
        Product product = productRepository.findById(id).orElseThrow(() -> new RuntimeException("Product not found"));
        return mapToProductResponse(product);
    }

    @CacheEvict(cacheNames = {"product", "products"}, key = "#id", allEntries = true)
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
				.build();
	}
}
