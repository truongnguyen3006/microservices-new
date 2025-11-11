package com.myexampleproject.userservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.myexampleproject.userservice.dto.AdminUpdateUserRequest;
import com.myexampleproject.userservice.dto.UserRequest;
import com.myexampleproject.userservice.dto.UserResponse;
import com.myexampleproject.userservice.model.User;
import com.myexampleproject.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service // Đánh dấu đây là một Bean Service
public class UserService {
    private final UserRepository userRepository;
    private final KeycloakService keycloakService;

    // Tạo user trong  DB
    public UserResponse createUser(UserRequest userRequest){
        // 1️⃣ Gọi API Keycloak để tạo user
        String keycloakId = keycloakService.createUserInKeycloak(userRequest);

        // 2️⃣ Gán role mặc định ("user") cho user vừa tạo
        keycloakService.assignRealmRoleToUser(keycloakId, "user");

        // 2️⃣ Lưu user profile vào DB
        User user = User.builder()
                .keycloakId(keycloakId)
                .fullName(userRequest.getFullName())
                .email(userRequest.getEmail())
                .phoneNumber(userRequest.getPhoneNumber())
                .address(userRequest.getAddress())
                .status(true)
                .build();
        userRepository.save(user);
        return mapToUserResponse(user);
    }

    //    Người dùng tự cập nhật
    public UserResponse updateSelfUser(String keycloakId, UserRequest request) {
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user"));

        // Cập nhật thông tin trong DB
        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getEmail() != null) user.setEmail(request.getEmail());
        if (request.getPhoneNumber() != null) user.setPhoneNumber(request.getPhoneNumber());
        if (request.getAddress() != null) user.setAddress(request.getAddress());
        userRepository.save(user);

        // Đồng bộ lên Keycloak
        Map<String, Object> body = new HashMap<>();
        if (request.getEmail() != null) body.put("email", request.getEmail());
        if (request.getFullName() != null) body.put("firstName", request.getFullName());
        if (request.getPhoneNumber() != null) body.put("attributes", Map.of("phoneNumber", request.getPhoneNumber()));
        if (request.getAddress() != null) body.put("attributes", Map.of("address", request.getAddress()));


        keycloakService.updateUserInKeycloak(user.getKeycloakId(), body);

        // Nếu người dùng đổi mật khẩu
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            keycloakService.updatePasswordInKeycloak(user.getKeycloakId(), request.getPassword());
        }

        return mapToUserResponse(user);
    }

    // Admin cập nhật người dùng
    public UserResponse updateUserByAdmin(Long id,  boolean enabled) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user"));

        keycloakService.updateUserInKeycloak(user.getKeycloakId(), Map.of("enabled", enabled));
        user.setStatus(enabled); // 🟢 Cập nhật field mới
        userRepository.save(user); // 🟢 Lưu vào DB
        return mapToUserResponse(user);
    }


    public List<UserResponse> getAllUsers(){
        List<User> users = userRepository.findAll();
        return users.stream().map(this::mapToUserResponse).toList();
    }

    public UserResponse getUserById(Long id){
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        return mapToUserResponse(user);
    }

    public void deleteUserById(Long id){
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        // 1️⃣ Xóa trên Keycloak
        keycloakService.deleteUser(user.getKeycloakId());
        userRepository.deleteById(id);
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .keycloakId(user.getKeycloakId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .address(user.getAddress())
                .status(user.isStatus())
                .build();
    }

}
