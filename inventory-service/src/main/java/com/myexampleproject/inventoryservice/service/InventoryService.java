package com.myexampleproject.inventoryservice.service;

import java.util.List;
import java.util.stream.Collectors;

import com.myexampleproject.inventoryservice.event.OrderPlacedEvent;
import com.myexampleproject.inventoryservice.dto.OrderLineItemsDto;
import com.myexampleproject.inventoryservice.event.OrderProcessingEvent;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.myexampleproject.inventoryservice.dto.InventoryResponse;
import com.myexampleproject.inventoryservice.repository.InventoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {
	
	private final InventoryRepository inventoryRepository;
	private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    @KafkaListener(topics = "order-processing-topic", groupId = "inventory-processor-group")
    public void handleOrderProcessingEvent(OrderProcessingEvent orderProcessingEvent) {
        log.info("Received order {} to check inventory", orderProcessingEvent.getOrderNumber());
//        Lấy SKU codes từ sự kiện
        List<String> skuCodes = orderProcessingEvent.getOrderLineItemsDtoList().stream()
                .map(OrderLineItemsDto::getSkuCode)
                .toList();
//        logic nghiệp vụ
        List<InventoryResponse> inventoryCheckList = inventoryRepository.findBySkuCodeIn(skuCodes).stream()
                .map(inventory ->
                        InventoryResponse.builder().skuCode(inventory.getSkuCode())
                                .isInStock(inventory.getQuantity() > 0)
                                .build()
                ).toList();

//        Kiểm tra kết quả
        boolean allProductsInStock = inventoryCheckList.stream().allMatch(InventoryResponse::isInStock);
        boolean allProductsExist = (inventoryCheckList.size() == skuCodes.size());
        if (allProductsInStock && allProductsExist) {
            log.info("Inventory check SUCCESS for Order {}. Sending notification.", orderProcessingEvent.getOrderNumber());
            // (Nghiệp vụ thực tế: bạn sẽ trừ kho ở đây. Ví dụ: inventoryRepository.reduceStock(skuCodes))

            // 4. Gửi sự kiện tới NotificationService
            kafkaTemplate.send("notificationTopic", new OrderPlacedEvent(orderProcessingEvent.getOrderNumber()));
        } else {
            log.warn("Inventory check FAILED for Order {}. Products out of stock or not found.", orderProcessingEvent.getOrderNumber());
            // (Nghiệp vụ thực tế: bạn sẽ gửi sự kiện "OrderFailedEvent" để OrderService có thể CẬP NHẬT trạng thái "FAILED")
        }
    }



	@Transactional(readOnly = true)
    @SneakyThrows
	public List<InventoryResponse> isInStock(List<String> skuCode) {
		return inventoryRepository.findBySkuCodeIn(skuCode).stream()
				.map(inventory ->
					InventoryResponse.builder().skuCode(inventory.getSkuCode())
					.isInStock(inventory.getQuantity() > 0)
					.build()
				).toList();
	}
}
