package com.jaypay.auth;

import com.jaypay.management.ManagementPortContract;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class AuthApplicationTest extends ManagementPortContract {

    @DynamicPropertySource
    static void keycloak(DynamicPropertyRegistry registry) {
        StaffTokens.register(registry);
    }
}
