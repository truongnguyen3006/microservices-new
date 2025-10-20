package com.myexampleproject.productservice.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
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
public class ProductController {
	
	private final ProductService productService;
	
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public void createProduct(@RequestBody ProductRequest productRequest) {
        productService.createProduct(productRequest);
	}
	
	@GetMapping
	@ResponseStatus(HttpStatus.OK)
	public List<ProductResponse> getAllProducts(){
		return productService.getAllProducts();
	}
}
