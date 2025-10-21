package com.myexampleproject.inventoryservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.myexampleproject.inventoryservice.model.Inventory;
import com.myexampleproject.inventoryservice.repository.InventoryRepository;

@SpringBootApplication
@Slf4j
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner loadData(InventoryRepository inventoryRepository) {
        return args -> {
            // THÊM BƯỚC KIỂM TRA NÀY
            if (inventoryRepository.count() == 0) {
                log.info("Database is empty. Seeding inventory data...");

                Inventory item1 = new Inventory();
                item1.setSkuCode("iphone_15_pro");
                item1.setQuantity(100);

                Inventory item2 = new Inventory();
                item2.setSkuCode("macbook_pro_m4");
                item2.setQuantity(50);

                Inventory item3 = new Inventory();
                item3.setSkuCode("airpods_pro_3");
                item3.setQuantity(200);

                // Sản phẩm này để test trường hợp hết hàng (quantity = 0)
                Inventory item4 = new Inventory();
                item4.setSkuCode("iphone_17_orange");
                item4.setQuantity(0);

                inventoryRepository.save(item1);
                inventoryRepository.save(item2);
                inventoryRepository.save(item3);
                inventoryRepository.save(item4);

                log.info("Inventory data loaded.");
            } else {
                log.info("Inventory data already exists. Skipping seeding.");
            }
        };
    }
}
