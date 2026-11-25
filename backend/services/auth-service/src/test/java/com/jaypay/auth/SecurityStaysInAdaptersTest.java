package com.jaypay.auth;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class SecurityStaysInAdaptersTest {

    @Test
    void domainAndApplicationKnowNothingOfSpringSecurityOrTokens() {
        noClasses().that().resideInAnyPackage("com.jaypay.auth.domain..", "com.jaypay.auth.application..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.security..", "com.nimbusds..")
                .check(new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("com.jaypay.auth"));
    }
}
