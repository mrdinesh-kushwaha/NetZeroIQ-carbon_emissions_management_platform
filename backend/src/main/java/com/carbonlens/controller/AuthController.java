package com.carbonlens.controller;

import com.carbonlens.dto.AuthDtos;
import com.carbonlens.dto.UserDto;
import com.carbonlens.model.User;
import com.carbonlens.repository.UserRepository;
import com.carbonlens.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;


    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthDtos.LoginRequest request) {
        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
        String password = request.getPassword() == null ? "" : request.getPassword().trim();

        if (email.isBlank() || password.isBlank()) {
            return ResponseEntity.status(401).body(Map.of("detail", "Email and password are required."));
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !user.isEnabled()) {
            return ResponseEntity.status(401).body(Map.of("detail", "Invalid email or password."));
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("detail", "Invalid email or password."));
        }

        return ResponseEntity.ok(buildLoginResponse(user));
    }

    private AuthDtos.LoginResponse buildLoginResponse(User user) {
        String access = jwtUtil.generateAccessToken(user);
        String refresh = jwtUtil.generateRefreshToken(user);
        return AuthDtos.LoginResponse.builder()
                .access(access)
                .refresh(refresh)
                .user(UserDto.from(user))
                .build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody AuthDtos.RefreshRequest request) {
        String refreshToken = request.getRefresh();
        if (!jwtUtil.validateToken(refreshToken)) {
            return ResponseEntity.status(401).body(Map.of("detail", "Invalid or expired refresh token."));
        }
        String email = jwtUtil.extractUsername(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        String newAccess = jwtUtil.generateAccessToken(user);
        return ResponseEntity.ok(Map.of("access", newAccess));
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(Principal principal) {
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return ResponseEntity.ok(UserDto.from(user));
    }
}
