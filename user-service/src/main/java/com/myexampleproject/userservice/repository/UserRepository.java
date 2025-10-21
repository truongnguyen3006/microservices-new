package com.myexampleproject.userservice.repository;

import com.myexampleproject.userservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

//Tham số đầu tiên. Đây là tên của lớp Entity
//Long: Tham số thứ hai. Đây là kiểu dữ liệu của Khóa Chính
public interface UserRepository extends JpaRepository<User, Long> {
    // Tự động có các hàm: save, findById, findAll, deleteById...

    // có thể thêm các hàm tìm kiếm tùy chỉnh, ví dụ:
    // Optional<User> findByUsername(String username);
}
