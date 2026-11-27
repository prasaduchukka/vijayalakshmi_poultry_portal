package com.vpf.service;

import com.vpf.dto.FarmSettingsRequest;
import com.vpf.entity.FarmSettings;
import com.vpf.repository.FarmSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FarmSettingsService {

    private final FarmSettingsRepository repository;

    public FarmSettings get() {
        FarmSettings s = repository.findById(1L).orElseGet(() -> {
            FarmSettings fresh = new FarmSettings();
            fresh.setId(1L);
            return repository.save(fresh);
        });
        if (s.getFarmName() == null || s.getFarmName().isBlank()
                || "Rainbow Poultry Stock Point".equalsIgnoreCase(s.getFarmName().trim())) {
            s.setFarmName("Vijayalakshmi Poultry Farm");
            s = repository.save(s);
        }
        return s;
    }

    public FarmSettings update(FarmSettingsRequest req) {
        FarmSettings s = get();
        if (req.getFarmName() != null && !req.getFarmName().isBlank()) s.setFarmName(req.getFarmName().trim());
        s.setOwnerName(req.getOwnerName());
        s.setOwnerPhone(req.getOwnerPhone());
        return repository.save(s);
    }
}
