package com.myexampleproject.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartLineItem {
    private String skuCode;
    private int quantity;
    private double price;
}