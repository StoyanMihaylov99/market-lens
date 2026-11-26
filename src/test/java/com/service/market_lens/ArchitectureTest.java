package com.service.market_lens;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Package boundaries from CONTEXT.md, verified on the compiled main classes (tests excluded, so test helpers may
 * cross boundaries).
 */
@AnalyzeClasses(packages = "com.service.market_lens", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "com.service.market_lens";

    /** "The LLM never does the math": metrics is pure Java over domain records — no Spring, I/O, web or vendors. */
    @ArchTest
    static final ArchRule metricsIsPure = classes().that().resideInAPackage(ROOT + ".metrics..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    ROOT + ".metrics..",
                    ROOT + ".marketdata",   // domain records only, not adapter sub-packages
                    "java.lang..", "java.math..", "java.time..", "java.util..",
                    "org.jspecify.annotations..")
            .because("metrics must be deterministic functions over domain records");

    /** Scoring logic uses Spring only for wiring and configuration binding. */
    @ArchTest
    static final ArchRule scoringHasNoIoOrWeb = noClasses().that().resideInAPackage(ROOT + ".analysis.scoring..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    ROOT + ".marketdata..", ROOT + ".api..",
                    "org.springframework.web..", "org.springframework.http..", "java.net..", "java.io..")
            .because("scoring works on computed metrics only");

    /** Vendor types stay inside their adapter package. */
    @ArchTest
    static final ArchRule vendorTypesStayInAdapter = noClasses()
            .that().resideOutsideOfPackage(ROOT + ".marketdata.alphavantage..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".marketdata.alphavantage..")
            .because("business logic depends on the MarketDataProvider port, never on a vendor");

    /** The domain port and records know nothing about features that use them. */
    @ArchTest
    static final ArchRule marketDataDomainIsIndependent = noClasses().that().resideInAPackage(ROOT + ".marketdata")
            .should().dependOnClassesThat().resideInAnyPackage(
                    ROOT + ".api..", ROOT + ".analysis..", ROOT + ".metrics..", ROOT + ".config..");

    /** Nothing depends on the web layer. */
    @ArchTest
    static final ArchRule nothingDependsOnApi = noClasses().that().resideOutsideOfPackage(ROOT + ".api..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".api..");

    /** Cross-cutting config is used by features, never the reverse. */
    @ArchTest
    static final ArchRule configDependsOnNoFeature = noClasses().that().resideInAPackage(ROOT + ".config..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    ROOT + ".api..", ROOT + ".analysis..", ROOT + ".metrics..", ROOT + ".marketdata..");

    @ArchTest
    static final ArchRule noCyclesBetweenFeatures = slices().matching(ROOT + ".(*)..").should().beFreeOfCycles();
}
