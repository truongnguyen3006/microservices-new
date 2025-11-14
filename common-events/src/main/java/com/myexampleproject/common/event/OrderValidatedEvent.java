package com.myexampleproject.common.event;

import com.myexampleproject.common.dto.OrderLineItemsDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderValidatedEvent {
    private String orderNumber;
    private List<OrderLineItemsDto> orderLineItemsDtoList;
}
