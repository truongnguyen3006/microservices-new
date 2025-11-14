package com.myexampleproject.orderservice.dto;

import com.myexampleproject.common.dto.OrderLineItemsDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderResponse {
    private Long id;
    private String orderNumber;
    private String status;
    private List<OrderLineItemsDto> orderLineItemsList;
}
