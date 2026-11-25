package com.jaypay.auth.domain;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PasswordPolicy {

    private static final Set<String> COMMON = Set.of(
            "password123!", "password1234", "p@ssw0rd1234", "p@ssword1234", "passw0rd!234",
            "qwerty123456", "qwerty12345!", "1q2w3e4r5t6y", "1qaz2wsx3edc", "!qaz2wsx3edc",
            "abc123456789", "abcd1234!@#$", "iloveyou1234", "welcome12345", "welcome@1234",
            "admin1234567", "admin@123456", "administrator1", "letmein12345", "changeme1234",
            "trustno1trustno1", "123456789abc", "monkey123456", "dragon123456", "sunshine1234",
            "football1234", "baseball1234", "superman1234", "master123456", "jaypay123456!");

    public static final List<PasswordRule> RULES = List.of(
            new PasswordRule("MIN_LENGTH", "Use at least 12 characters.", (username, password) -> password.length() >= 12),
            new PasswordRule("MAX_LENGTH", "Use at most 72 bytes. A letter outside English takes two to four.", (username, password) -> password.getBytes(StandardCharsets.UTF_8).length <= 72),
            new PasswordRule("UPPERCASE", "Include an uppercase letter.", (username, password) -> password.chars().anyMatch(Character::isUpperCase)),
            new PasswordRule("LOWERCASE", "Include a lowercase letter.", (username, password) -> password.chars().anyMatch(Character::isLowerCase)),
            new PasswordRule("DIGIT", "Include a digit.", (username, password) -> password.chars().anyMatch(Character::isDigit)),
            new PasswordRule("SYMBOL", "Include a symbol such as ! or @.", (username, password) -> password.chars().anyMatch(PasswordPolicy::isSymbol)),
            new PasswordRule("NOT_USERNAME", "Do not include the username.", (username, password) -> !lower(password).contains(lower(username))),
            new PasswordRule("NOT_COMMON", "Pick a password that is not on the list of common passwords.", (username, password) -> !COMMON.contains(lower(password))));

    private PasswordPolicy() {
    }

    public static List<PasswordRule> violations(String username, String password) {
        return RULES.stream().filter(rule -> rule.isBrokenBy(username, password)).toList();
    }

    public static void check(String username, String password) {
        List<PasswordRule> broken = violations(username, password);
        if (!broken.isEmpty()) {
            throw new StaffAccountException(StaffAccountException.Reason.WEAK_PASSWORD, broken);
        }
    }

    private static boolean isSymbol(int character) {
        return !Character.isLetterOrDigit(character) && !Character.isWhitespace(character);
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
