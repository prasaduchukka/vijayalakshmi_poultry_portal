package com.vpf.dto;

import lombok.Data;

@Data
public class FarmSettingsRequest {
    private String farmName;
    private String ownerName;
    private String ownerPhone;
}
