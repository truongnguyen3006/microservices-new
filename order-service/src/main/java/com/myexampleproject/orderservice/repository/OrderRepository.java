package com.myexampleproject.orderservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.myexampleproject.orderservice.model.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

}
