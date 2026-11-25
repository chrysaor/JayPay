package com.jaypay.auth.adapter.out.password;

import com.jaypay.auth.application.port.out.PasswordHashing;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PasswordEncoderHashing implements PasswordHashing {

    private final PasswordEncoder passwordEncoder;

    @Override
    public String hash(String password) {
        return passwordEncoder.encode(password);
    }

    @Override
    public boolean matches(String password, String hash) {
        return passwordEncoder.matches(password, hash);
    }
}
