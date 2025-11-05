//package com.myexampleproject.inventoryservice.repository;
//
//import java.util.List;
//import java.util.Optional;
//
//import jakarta.persistence.LockModeType;
//import org.springframework.data.jpa.repository.JpaRepository;
//
//import com.myexampleproject.inventoryservice.model.Inventory;
//import org.springframework.data.jpa.repository.Lock;
//
//public interface InventoryRepository extends JpaRepository<Inventory, Long>{
//
//	List <Inventory> findBySkuCodeIn(List<String> skuCode);
////    "ổ khóa" để đảm bảo 2 luồng không thể cùng sửa 1 dòng
//    @Lock(LockModeType.PESSIMISTIC_WRITE)
//    Optional<Inventory> findBySkuCode(String skuCode);
//}
