package com.myexampleproject.productservice.model;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;


import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

//class Java này sẽ ánh xạ tới một collection trong MongoDB.
//Dùng thay cho @Entity trong JPA.
@Entity
@Table(name = "product")
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
    // 4. Thêm dòng này để id tự động tăng (AUTO_INCREMENT trong MySQL)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
	private String name;
	private String description;
    @Column(nullable = false)
	private BigDecimal price;
    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
