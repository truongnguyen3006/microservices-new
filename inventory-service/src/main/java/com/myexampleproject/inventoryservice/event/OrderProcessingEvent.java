package com.myexampleproject.inventoryservice.event;

import com.myexampleproject.inventoryservice.dto.OrderLineItemsDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import com.myexampleproject.inventoryservice.dto.OrderLineItemsDto;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderProcessingEvent {
    private String orderNumber;
    private List<OrderLineItemsDto> orderLineItemsDtoList;
}
