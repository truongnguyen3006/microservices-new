package com.myexampleproject.productservice.service;

import java.util.List;

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
	
	public void createProduct(ProductRequest productRequest) {
		Product product = Product.builder()
				.name(productRequest.getName())
				.description(productRequest.getDescription())
				.price(productRequest.getPrice())
				.build();
		
		productRepository.save(product);
		log.info("Product {} is saved", product.getId());
	}

	public List<ProductResponse> getAllProducts() {
		List<Product> products = productRepository.findAll();
		
		return products.stream().map(this::mapToProductResponse).toList();
	}
	
	private ProductResponse mapToProductResponse(Product product) {
		return ProductResponse.builder()
				.id(product.getId())
				.name(product.getName())
				.description(product.getDescription())
				.price(product.getPrice())
				.build();
	}
}
