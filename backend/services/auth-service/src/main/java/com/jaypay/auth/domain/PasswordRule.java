package com.jaypay.auth.domain;

import java.util.function.BiPredicate;

public record PasswordRule(String name, String message, BiPredicate<String, String> satisfiedBy) {

    boolean isBrokenBy(String username, String password) {
        return !satisfiedBy.test(username, password);
    }
}
