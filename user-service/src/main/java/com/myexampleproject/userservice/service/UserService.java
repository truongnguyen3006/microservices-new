package com.myexampleproject.userservice.service;

import com.myexampleproject.userservice.dto.UserRequest;
import com.myexampleproject.userservice.dto.UserResponse;
import com.myexampleproject.userservice.model.User;
import com.myexampleproject.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service // Đánh dấu đây là một Bean Service
public class UserService {
    private final UserRepository userRepository;
    //    users -> mapToUserResponse(users)
    //    map(this::mapToUserResponse):
    //    Áp dụng hàm mapToUserResponse cho từng User
    //    trong luồng để biến nó thành một UserResponse.
    public List<UserResponse> getAllUsers(){
        List<User> users = userRepository.findAll();
        return users.stream().map(this::mapToUserResponse).toList();
    }

    public UserResponse getUserById(Long id){
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        return mapToUserResponse(user);
    }

    public User createUser(UserRequest  userRequest){
        // Đây là nơi xử lý logic nghiệp vụ
        // Ví dụ: Kiểm tra email đã tồn tại chưa
        User user = User.builder()
                .username(userRequest.getUsername())
                .password(userRequest.getPassword())
                .email(userRequest.getEmail())
                .role("USER")
                .build();
        return userRepository.save(user);
    }

    public void deleteUserById(Long id){
        // Thêm logic kiểm tra nếu cần
        if(!userRepository.existsById(id)){
            throw new RuntimeException("User not found");
        }
        userRepository.deleteById(id);
    }

    private UserResponse mapToUserResponse(User  user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .build();
    }

}
