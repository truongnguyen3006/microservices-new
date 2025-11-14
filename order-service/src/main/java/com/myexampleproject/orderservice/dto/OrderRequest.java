package com.myexampleproject.orderservice.dto;

import java.util.List;

import com.myexampleproject.common.dto.OrderLineItemsDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderRequest {
	private List<OrderLineItemsDto> orderLineItemsDtoList;
}
