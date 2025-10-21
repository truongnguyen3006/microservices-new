package com.myexampleproject.userservice.model;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedDate;

@Entity
@Table(name ="user")
@AllArgsConstructor
//Tự động tạo constructor rỗng.
@NoArgsConstructor
//Tạo ra Builder Pattern để khởi tạo object gọn hơn.
@Builder
//auto getter/setter/toString/equals/hashCode.
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    @Column(unique = true, nullable = false)
    private String username;
    @Column(unique = true, nullable = false)
    private String email;
    @Column(nullable = false)
    private String password;
    private String role = "USER";
    @CreationTimestamp
    private LocalDateTime createdDate;
    @UpdateTimestamp
    private  LocalDateTime updatedDate;
}
