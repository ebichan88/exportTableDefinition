package com.dbxray.mcp;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.FileDescriptor;
import java.io.PrintStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** docs/architecture/mcp-server.mdの構成のうち、パッケージの依存の向き・標準出力への書き込みの禁止等の機械的に判定できるものを検査するテスト */
class ArchitectureTest {

  private static final String ROOT = "com.dbxray.mcp";

  private static final JavaClasses MAIN_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(ROOT);

  @Test
  @DisplayName(
      "パッケージの依存の向きは tool → catalog ← snapshot・catalog ← insight に従い、snapshotとinsightは互いに依存しない")
  void testPackageDependencies() {
    layeredArchitecture()
        .consideringOnlyDependenciesInLayers()
        // 末尾に..を付けないため直下のパッケージ（エントリーポイント）だけを指し、配下の各パッケージは含まない
        .layer("Entry")
        .definedBy(ROOT)
        .layer("Tool")
        .definedBy(ROOT + ".tool..")
        .layer("Snapshot")
        .definedBy(ROOT + ".snapshot..")
        .layer("Insight")
        .definedBy(ROOT + ".insight..")
        .layer("Catalog")
        .definedBy(ROOT + ".catalog..")
        .whereLayer("Entry")
        .mayNotBeAccessedByAnyLayer()
        .whereLayer("Tool")
        .mayOnlyBeAccessedByLayers("Entry")
        .whereLayer("Snapshot")
        .mayOnlyBeAccessedByLayers("Entry")
        .whereLayer("Insight")
        .mayOnlyBeAccessedByLayers("Entry")
        .whereLayer("Catalog")
        .mayOnlyBeAccessedByLayers("Entry", "Tool", "Snapshot", "Insight")
        // 読み込みの失敗を利用者が直せる誤りとして伝える例外だけは、直下にあっても読み込み側から使う
        .ignoreDependency(alwaysTrue(), equivalentTo(UserCorrectableException.class))
        .check(MAIN_CLASSES);
  }

  @Test
  @DisplayName("catalogはMCPのSDKにもJSONにも依存しない")
  void testCatalogDependsOnlyOnJdk() {
    classes()
        .that()
        .resideInAPackage(ROOT + ".catalog..")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage("java..", ROOT + ".catalog..")
        .check(MAIN_CLASSES);
  }

  @Test
  @DisplayName("標準出力へ書き込まない（標準出力はMCPのプロトコルが使う）")
  void testNoClassWritesToStandardOutput() {
    noClasses()
        .should()
        .accessField(System.class, "out")
        .orShould()
        .accessField(FileDescriptor.class, "out")
        .orShould()
        .callMethod(System.class, "setOut", PrintStream.class)
        .because("標準出力へ書き込むとMCPのメッセージに混ざり、クライアントとの通信が壊れるため")
        .check(MAIN_CLASSES);
  }

  @Test
  @DisplayName("cliのコードに依存しない（cliとの接点はスナップショットの形式だけ）")
  void testDoesNotDependOnCli() {
    noClasses()
        .should()
        .dependOnClassesThat(
            resideInAPackage("com.dbxray..").and(resideOutsideOfPackage(ROOT + "..")))
        .check(MAIN_CLASSES);
  }
}
