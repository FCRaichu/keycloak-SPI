package com.fc.keycloak.userstorage.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter
public class UserEntity {
    private Long id;
    private String userId;
    private String password;
    private String nickname;
    private String role;
    private Integer points;
    private String profileImage;
    private LocalDateTime lastLogin;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
}
