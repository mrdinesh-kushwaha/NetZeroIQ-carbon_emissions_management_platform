package com.carbonlens.dto;

import lombok.*;

public class AuthDtos {

    @Data
    public static class LoginRequest {
        private String email;
        private String password;
    }

    @Data @Builder
    public static class LoginResponse {
        private String access;
        private String refresh;
        private UserDto user;
    }

    @Data
    public static class RefreshRequest {
        private String refresh;
    }

    @Data @Builder
    public static class RefreshResponse {
        private String access;
    }
}
