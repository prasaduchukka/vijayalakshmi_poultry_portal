package com.vpf.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Singleton row of farm-identity info shown on the branding and PDF letterhead. */
@Getter
@Setter
@Entity
@Table(name = "farm_settings")
public class FarmSettings {

    @Id
    private Long id = 1L;

    private String farmName = "Vijayalakshmi Poultry Farm";
    private String ownerName;
    private String ownerPhone;
}
