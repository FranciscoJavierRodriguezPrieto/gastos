package com.gastos.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * La arquitectura hexagonal deja de existir en cuanto alguien tiene prisa. Estos tests
 * la convierten en una condicion de build: si una regla se rompe, el pipeline falla.
 */
@AnalyzeClasses(packages = "com.gastos", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule el_dominio_no_depende_de_spring = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
            .because("el nucleo financiero debe poder ejecutarse y testearse sin framework");

    @ArchTest
    static final ArchRule el_dominio_no_depende_de_persistencia = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "javax.persistence..")
            .because("el modelo de dominio no puede quedar acoplado al esquema de base de datos");

    @ArchTest
    static final ArchRule el_dominio_no_depende_de_la_aplicacion_ni_de_la_infraestructura = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..")
            .because("las dependencias apuntan siempre hacia dentro");

    @ArchTest
    static final ArchRule la_aplicacion_no_depende_de_la_infraestructura = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .because("la capa de aplicacion habla con puertos, no con adaptadores");

    @ArchTest
    static final ArchRule los_contextos_no_se_acoplan_entre_si = noClasses()
            .that().resideInAPackage("com.gastos.mortgage..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.gastos.accounts..", "com.gastos.expenses..", "com.gastos.iam..")
            .because("cada contexto acotado se comunica por contratos publicos, no por sus modelos internos");

    @ArchTest
    static final ArchRule el_nucleo_no_usa_la_hora_del_sistema = noClasses()
            .that().resideInAnyPackage("..domain..", "..application..")
            .should().callMethod(java.time.Instant.class, "now")
            .orShould().callMethod(java.time.LocalDate.class, "now")
            .because("el tiempo se inyecta como Clock para que los calculos sean deterministas en test");
}
