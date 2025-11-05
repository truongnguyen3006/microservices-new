package com.myexampleproject.productservice.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductRequest {
	private String name;
	private String description;
	private BigDecimal price;

    private String skuCode;         // Mã định danh
    private Integer initialQuantity;  // Số lượng tồn kho ban đầu
}
