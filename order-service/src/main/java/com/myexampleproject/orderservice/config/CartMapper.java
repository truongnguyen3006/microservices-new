package com.myexampleproject.orderservice.config;

import com.myexampleproject.common.dto.OrderLineItemsDto;
import com.myexampleproject.common.event.CartCheckoutEvent;
import com.myexampleproject.orderservice.dto.OrderRequest;

import java.math.BigDecimal;
import java.util.List;

public class CartMapper {
    public static OrderRequest fromCart(CartCheckoutEvent event) {

        List<OrderLineItemsDto> items = event.getItems().stream()
                .map(i -> OrderLineItemsDto.builder()
                        .skuCode(i.getSkuCode())
                        .price(BigDecimal.valueOf(i.getPrice()))
                        .quantity(i.getQuantity())
                        .build()
                ).toList();

        return OrderRequest.builder()
                .orderLineItemsDtoList(items)
                .build();
    }
}
