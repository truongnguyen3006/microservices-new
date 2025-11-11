package com.myexampleproject.userservice.controller;

import com.myexampleproject.userservice.dto.AdminUpdateUserRequest;
import com.myexampleproject.userservice.dto.UserRequest;
import com.myexampleproject.userservice.dto.UserResponse;
import com.myexampleproject.userservice.model.User;
import com.myexampleproject.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

//@Controller: Báo cho Spring biết đây là một "Bean",nó chuyên xử lý các request
//@ResponseBody: Tự động chuyển đổi kết quả trả về từ các phương thức
// (ví dụ: một đối tượng User hoặc List<User>) thành dạng dữ liệu JSON để gửi về cho client.
//một Lễ tân chuyên xử lý API và tự động trả về JSON
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService; // TIÊM SERVICE

//  ĐẦU VÀO: Dùng DTO Request
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@RequestBody UserRequest userRequest) {
        return userService.createUser(userRequest);
    }

    // User tự cập nhật
    @PatchMapping("/me")
    @PreAuthorize("hasRole('user')")
    public ResponseEntity<UserResponse> updateSelf(Authentication authentication,
                                                   @RequestBody UserRequest req) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String keycloakId = jwt.getClaim("sub");
        return ResponseEntity.ok(userService.updateSelfUser(keycloakId, req));
    }

    // Admin cập nhật user
    @PatchMapping("/admin/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse  updateUserStatus(@PathVariable Long id, @RequestBody AdminUpdateUserRequest request) {
        return userService.updateUserByAdmin(id, request.isEnabled());
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<UserResponse> getAllUsers(){
        return userService.getAllUsers();
    }

//@PathVariable trích xuất (lấy) giá trị từ một "biến" nằm trên chính đường dẫn URL.
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public UserResponse getUserById(@PathVariable Long id){
        return userService.getUserById(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public void deleteUserById(@PathVariable Long id){
        userService.deleteUserById(id);
    }
}
