package com.vpf.dto;

import com.vpf.entity.enums.UserRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {
    private Long id;
    private String username;
    private String fullName;
    private UserRole role;
    private boolean enabled;
    private LocalDateTime createdDate;
}
