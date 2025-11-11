package com.myexampleproject.userservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.myexampleproject.userservice.dto.UserRequest;
import com.myexampleproject.userservice.dto.UserResponse;
import com.myexampleproject.userservice.service.KeycloakService;
import com.myexampleproject.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    @Value("${keycloak.server-url}")
    private String keycloakServerUrl;

    @Value("${keycloak.realm}")
    private String keycloakRealm;

    @Value("${keycloak.client-id}")
    private String keycloakClientId;

    @Value("${keycloak.client-secret}")
    private String keycloakClientSecret;


    private final KeycloakService keycloakService;
    private final UserService userService;

    private WebClient getWebClient() {
        return WebClient.builder()
                .baseUrl(keycloakServerUrl + "/realms/" + keycloakRealm + "/protocol/openid-connect")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .build();
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@RequestBody UserRequest userRequest) {
        return userService.createUser(userRequest);
    }

    // Login bằng username + password
    @PostMapping("/login")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<?> login(@RequestParam String username, @RequestParam String password) {
        WebClient client = getWebClient();
        JsonNode response = client.post()
                .uri("/token")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .body(BodyInserters.fromFormData("grant_type", "password")
                        .with("client_id", keycloakClientId)
                        .with("client_secret", keycloakClientSecret)
                        .with("username", username)
                        .with("password", password))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        return ResponseEntity.ok(response);
    }

    // Refresh token khi hết hạn
    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<JsonNode> refreshToken(@RequestParam String refresh_token) {
        WebClient client = getWebClient();
        JsonNode response = client.post()
                .uri("/token")
                .body(BodyInserters.fromFormData("grant_type", "refresh_token")
                        .with("client_id", keycloakClientId)
                        .with("client_secret", keycloakClientSecret)
                        .with("refresh_token", refresh_token))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        return ResponseEntity.ok(response);
    }

    // Đăng xuất (xóa refresh token)
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<JsonNode> logout(@RequestParam String refresh_token) {
        WebClient client = getWebClient();
        JsonNode response = client.post()
                .uri("/logout")
                .body(BodyInserters.fromFormData("client_id", keycloakClientId)
                        .with("client_secret", keycloakClientSecret)
                        .with("refresh_token", refresh_token))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        return ResponseEntity.ok(response);
    }
}