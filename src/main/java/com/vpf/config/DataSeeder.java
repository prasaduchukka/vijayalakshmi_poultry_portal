package com.vpf.config;

import com.vpf.entity.User;
import com.vpf.entity.enums.UserRole;
import com.vpf.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates a single default admin user on first run, only if no user exists yet. */
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.default-admin.username}")
    private String defaultUsername;

    @Value("${app.default-admin.password}")
    private String defaultPassword;

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            User admin = new User();
            admin.setUsername(defaultUsername);
            admin.setPassword(passwordEncoder.encode(defaultPassword));
            admin.setFullName("Admin");
            admin.setRole(UserRole.ADMIN);
            admin.setEnabled(true);
            userRepository.save(admin);
            System.out.println("=================================================");
            System.out.println(" Created default admin user: " + defaultUsername);
            System.out.println(" CHANGE THIS PASSWORD after first login.");
            System.out.println("=================================================");
        }
    }
}
