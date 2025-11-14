package com.myexampleproject.orderservice.model;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name="t_orders", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"orderNumber"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    @Column(name = "orderNumber", nullable = false, unique = true)
    private String orderNumber;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    private List<OrderLineItems> orderLineItemsList = new ArrayList<>(); // ✅ mutable list
    private String status;
}
