package com.myexampleproject.inventoryservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderValidatedEvent {
    private String orderNumber;
    // Thêm các thông tin cần thiết cho thanh toán, ví dụ:
    // private BigDecimal amount;
    // private String userId;
}
