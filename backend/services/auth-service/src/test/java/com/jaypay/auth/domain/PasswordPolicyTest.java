package com.jaypay.auth.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class PasswordPolicyTest {

    private static final String USERNAME = "kim.operator";

    @Test
    void acceptsAPasswordThatKeepsEveryRule() {
        assertThat(PasswordPolicy.violations(USERNAME, "Blue-Harbor-42-Lamp")).isEmpty();
    }

    @ParameterizedTest(name = "{0} breaks {1}")
    @CsvSource({
            "Short-Pw-1, MIN_LENGTH",
            "blue-harbor-42-lamp, UPPERCASE",
            "BLUE-HARBOR-42-LAMP, LOWERCASE",
            "Blue-Harbor-Lamp-Post, DIGIT",
            "BlueHarbor42Lamp, SYMBOL",
            "Kim.Operator-42-Lamp, NOT_USERNAME",
            "Password123!, NOT_COMMON",
            "pASSWORD123!, NOT_COMMON",
    })
    void namesTheOneRuleAPasswordBreaks(String password, String rule) {
        assertThat(PasswordPolicy.violations(USERNAME, password))
                .extracting(PasswordRule::name)
                .containsExactly(rule);
    }

    @Test
    void countsTheLengthLimitInBytesBecauseTheHashIgnoresWhatComesAfterThem() {
        assertThat(PasswordPolicy.violations(USERNAME, "Aa1!" + "a".repeat(68))).isEmpty();
        assertThat(PasswordPolicy.violations(USERNAME, "Aa1!" + "a".repeat(69)))
                .extracting(PasswordRule::name)
                .containsExactly("MAX_LENGTH");
        assertThat(PasswordPolicy.violations(USERNAME, "Aa1!-" + "\u00E9".repeat(34)))
                .extracting(PasswordRule::name)
                .containsExactly("MAX_LENGTH");
    }

    @Test
    void namesEveryRuleAPasswordBreaksWithAMessageForTheUser() {
        assertThat(PasswordPolicy.violations(USERNAME, "abc"))
                .extracting(PasswordRule::name, PasswordRule::message)
                .containsExactly(
                        tuple("MIN_LENGTH", "Use at least 12 characters."),
                        tuple("UPPERCASE", "Include an uppercase letter."),
                        tuple("DIGIT", "Include a digit."),
                        tuple("SYMBOL", "Include a symbol such as ! or @."));
    }

    @Test
    void rejectsAWeakPasswordWithTheRulesItBreaks() {
        assertThatThrownBy(() -> PasswordPolicy.check(USERNAME, "blue-harbor-lamp"))
                .isInstanceOfSatisfying(StaffAccountException.class, rejected -> {
                    assertThat(rejected.reason()).isEqualTo(StaffAccountException.Reason.WEAK_PASSWORD);
                    assertThat(rejected.violations()).extracting(PasswordRule::name).containsExactly("UPPERCASE", "DIGIT");
                });
    }
}
