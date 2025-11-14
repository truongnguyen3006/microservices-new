package com.myexampleproject.cartservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

//đánh dấu đây là một ứng dụng Spring Boot,
// cho phép tự động cấu hình và quét các thành phần trong dự án.
@SpringBootApplication
public class CartServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CartServiceApplication.class, args);
    }

}
