package io.github.afunemma.salonbooking;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import jakarta.persistence.Entity;

/**
 * Architecture rules, checked on every build. If a change breaks one, the build fails and
 * says which class broke which rule, so the design can't erode by accident.
 */
@AnalyzeClasses(packages = "io.github.afunemma.salonbooking", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	@ArchTest
	static final ArchRule controllersDoNotUseRepositories = noClasses().that()
		.areAnnotatedWith(RestController.class)
		.should()
		.dependOnClassesThat()
		.areAssignableTo(Repository.class)
		.because("controllers handle HTTP; data access goes through a service");

	@ArchTest
	static final ArchRule controllersDoNotExposeEntities = noClasses().that()
		.areAnnotatedWith(RestController.class)
		.should()
		.dependOnClassesThat()
		.areAnnotatedWith(Entity.class)
		.because("the API uses request/response records so the database can change without breaking clients");

	@ArchTest
	static final ArchRule schedulingIsFrameworkFree = noClasses().that()
		.resideInAPackage("..scheduling..")
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("org.springframework..", "jakarta.persistence..", "org.hibernate..")
		.because("the core scheduling logic should be plain Java that's easy to test and reuse");

	@ArchTest
	static final ArchRule noPackageCycles = slices().matching("io.github.afunemma.salonbooking.(*)..")
		.should()
		.beFreeOfCycles()
		.because("packages that depend on each other can't be understood or changed separately");

	@ArchTest
	static final ArchRule noFieldInjection = NO_CLASSES_SHOULD_USE_FIELD_INJECTION
		.because("constructor injection makes dependencies explicit and classes easy to test");

	@ArchTest
	static final ArchRule noSystemOut = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
		.because("use a logger, not System.out");

	@ArchTest
	static final ArchRule noJavaUtilLogging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING
		.because("the project logs through SLF4J");

}
