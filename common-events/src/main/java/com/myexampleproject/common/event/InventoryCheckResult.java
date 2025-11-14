package com.myexampleproject.common.event;

import com.myexampleproject.common.dto.OrderLineItemsDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InventoryCheckResult {
    private String orderNumber;
    private OrderLineItemsDto item;
    private boolean success;
    private String reason;
}