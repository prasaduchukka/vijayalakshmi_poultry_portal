package com.vpf.dto;

import com.vpf.entity.RecoveryOtp;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class VerifyRecoveryOtpRequest {
    @NotBlank @Email private String email;
    @NotNull private RecoveryOtp.Purpose purpose;
    @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "OTP must be 6 digits") private String otp;
}
