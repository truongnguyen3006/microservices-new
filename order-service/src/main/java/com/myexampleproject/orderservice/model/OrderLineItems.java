package com.myexampleproject.orderservice.model;

import java.math.BigDecimal;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name="t_orders_line_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderLineItems {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY )
	private Long id;
	//Mã sản phẩm (SKU) của dòng sản phẩm trong đơn hàng.
	private String skuCode;
	private BigDecimal price;
	private Integer quantity;
}
