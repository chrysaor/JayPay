package com.jaypay.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.CompositeArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class HexagonalArchitectureTest {

    private static final String SPRING_BOOT_APPLICATION = "org.springframework.boot.autoconfigure.SpringBootApplication";

    private static final String[] FRAMEWORKS = {
            "org.springframework..", "jakarta..", "org.axonframework.."
    };

    private static final String[] OUTSIDE_WORLD = {
            "jakarta.persistence..", "jakarta.validation..", "org.springframework.data..", "org.springframework.web..",
            "org.springframework.kafka..", "org.axonframework..", "com.fasterxml.jackson.databind..", "tools.jackson..",
            "java.net.http..", "software.amazon.."
    };

    private static JavaClasses service;
    private static String root;

    @BeforeAll
    static void importServiceClasses() {
        JavaClasses imported = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .importPackages("com.jaypay");
        root = imported.stream()
                .filter(javaClass -> javaClass.isAnnotatedWith(SPRING_BOOT_APPLICATION))
                .map(JavaClass::getPackageName)
                .findFirst()
                .orElseThrow();
        service = imported.that(resideInAPackage(root + ".."));
    }

    @Test
    void everyClassLivesInAHexagonalLayer() {
        classes().should().resideInAnyPackage(
                        root,
                        layer("domain"),
                        layer("application.port.in"),
                        layer("application.port.out"),
                        layer("application.service"),
                        layer("adapter.in"),
                        layer("adapter.out"),
                        layer("adapter.axon"))
                .because("the root package holds only the application class and its configuration")
                .check(service);
    }

    @Test
    void domainDependsOnNoOtherLayerAndNoFramework() {
        noClasses().that().resideInAPackage(layer("domain"))
                .should().dependOnClassesThat().resideInAnyPackage(with(FRAMEWORKS, layer("application"), layer("adapter")))
                .allowEmptyShould(true)
                .check(service);
    }

    @Test
    void applicationReachesTheOutsideWorldOnlyThroughPorts() {
        noClasses().that().resideInAPackage(layer("application"))
                .should().dependOnClassesThat().resideInAnyPackage(with(OUTSIDE_WORLD, layer("adapter")))
                .allowEmptyShould(true)
                .check(service);
    }

    @Test
    void portsExchangeNeitherEntitiesNorAdapterTypes() {
        noClasses().that().resideInAPackage(layer("application.port"))
                .should().dependOnClassesThat().resideInAnyPackage(layer("adapter"), layer("application.service"), "jakarta.persistence..")
                .allowEmptyShould(true)
                .check(service);
    }

    @Test
    void adaptersEnterTheApplicationOnlyThroughPorts() {
        noClasses().that().resideInAPackage(layer("adapter"))
                .should().dependOnClassesThat().resideInAPackage(layer("application.service"))
                .allowEmptyShould(true)
                .check(service);
    }

    @Test
    void inboundAdaptersDriveUseCasesAndNeverTouchTheOutboundSide() {
        noClasses().that().resideInAPackage(layer("adapter.in"))
                .should().dependOnClassesThat().resideInAnyPackage(layer("adapter.out"), layer("application.port.out"))
                .allowEmptyShould(true)
                .check(service);
    }

    @Test
    void outboundAdaptersImplementOutboundPortsAndNeverTouchTheInboundSide() {
        noClasses().that().resideInAPackage(layer("adapter.out"))
                .should().dependOnClassesThat().resideInAnyPackage(layer("adapter.in"), layer("application.port.in"))
                .allowEmptyShould(true)
                .check(service);
    }

    @Test
    void outboundAdaptersHandleNoIncomingMessages() {
        noMethods().that().areDeclaredInClassesThat().resideInAPackage(layer("adapter.out"))
                .should().beAnnotatedWith("org.axonframework.commandhandling.CommandHandler")
                .orShould().beAnnotatedWith("org.axonframework.eventhandling.EventHandler")
                .orShould().beAnnotatedWith("org.axonframework.queryhandling.QueryHandler")
                .orShould().beAnnotatedWith("org.axonframework.modelling.saga.SagaEventHandler")
                .orShould().beAnnotatedWith("org.springframework.kafka.annotation.KafkaListener")
                .allowEmptyShould(true)
                .check(service);
    }

    @Test
    void siblingAdaptersNeverCallEachOther() {
        CompositeArchRule.of(slices().matching(root + ".adapter.in.(*)..").should().notDependOnEachOther())
                .and(slices().matching(root + ".adapter.out.(*)..").should().notDependOnEachOther())
                .because("adapters meet only through the ports of the application")
                .allowEmptyShould(true)
                .check(service);
    }

    @Test
    void axonMessagesAreContractsThatBothAdapterSidesShare() {
        classes().that().resideInAPackage(layer("adapter.axon"))
                .should().onlyDependOnClassesThat().resideInAnyPackage(layer("adapter.axon"), "java..", "org.axonframework..")
                .because("the event store persists their class names, so commands, events and queries stay put and carry no behaviour")
                .allowEmptyShould(true)
                .check(service);
    }

    private static String layer(String name) {
        return root + "." + name + "..";
    }

    private static String[] with(String[] packages, String... more) {
        return Stream.concat(Stream.of(packages), Stream.of(more)).toArray(String[]::new);
    }
}
