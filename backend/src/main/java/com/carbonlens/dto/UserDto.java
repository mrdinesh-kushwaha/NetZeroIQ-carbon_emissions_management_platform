package com.carbonlens.dto;

import com.carbonlens.model.User;
import lombok.*;
import java.util.UUID;

@Data @Builder
public class UserDto {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private String role;
    private TenantDto tenant;

    public static UserDto from(User u) {
        return UserDto.builder()
                .id(u.getId()).email(u.getEmail())
                .firstName(u.getFirstName()).lastName(u.getLastName())
                .fullName(u.getFullName()).role(u.getRole().name())
                .tenant(TenantDto.from(u.getTenant()))
                .build();
    }
}
