package com.myexampleproject.orderservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.myexampleproject.orderservice.model.Order;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNumber(String orderNumber);

    // Sử dụng "LEFT JOIN FETCH" để lấy luôn orderLineItemsList trong 1 câu lệnh SQL
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.orderLineItemsList WHERE o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumberWithItems(@Param("orderNumber") String orderNumber);
}
