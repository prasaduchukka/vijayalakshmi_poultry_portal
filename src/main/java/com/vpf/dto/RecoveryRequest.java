package com.vpf.dto;

import com.vpf.entity.RecoveryOtp;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class RecoveryRequest {
    @NotBlank @Email private String email;
    @NotNull private RecoveryOtp.Purpose purpose;
}
