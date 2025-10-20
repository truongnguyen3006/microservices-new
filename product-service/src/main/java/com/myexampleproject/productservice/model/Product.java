package com.myexampleproject.productservice.model;
import java.math.BigDecimal;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.*;
//class Java này sẽ ánh xạ tới một collection trong MongoDB.
//Dùng thay cho @Entity trong JPA.
@Document(value = "product")
//Tự động tạo constructor với tất cả các field.
@AllArgsConstructor
//Tự động tạo constructor rỗng.
@NoArgsConstructor
//Tạo ra Builder Pattern để khởi tạo object gọn hơn.
@Builder
//auto getter/setter/toString/equals/hashCode.
@Data
public class Product {
	@Id	
	private String id;
	private String name;
	private String description;
	private BigDecimal price;
}
