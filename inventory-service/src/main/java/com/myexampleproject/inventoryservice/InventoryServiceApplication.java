package com.myexampleproject.inventoryservice;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.myexampleproject.inventoryservice.model.Inventory;
import com.myexampleproject.inventoryservice.repository.InventoryRepository;

@SpringBootApplication
public class InventoryServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(InventoryServiceApplication.class, args);
	}
	
	@Bean
	public CommandLineRunner loadData(InventoryRepository inventoryRepository) {
		return args -> {
			Inventory inventory = new Inventory();
			inventory.setSkuCode("iphone_17");
			inventory.setQuantity(100);
			
			Inventory inventory1 = new Inventory();
			inventory1.setSkuCode("iphone_17_orange");
			inventory1.setQuantity(0);
			
			inventoryRepository.save(inventory);
			inventoryRepository.save(inventory1);
		};
	}
}
