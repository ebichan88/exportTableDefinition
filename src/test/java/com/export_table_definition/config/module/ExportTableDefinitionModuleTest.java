package com.export_table_definition.config.module;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.repository.TableDefinitionRepository;
import com.export_table_definition.infrastructure.db.repository.OracleTableDefinitionRepository;
import com.export_table_definition.infrastructure.db.repository.PostgresTableDefinitionRepository;
import com.export_table_definition.infrastructure.db.type.DatabaseType;
import com.export_table_definition.presentation.ExportTableDefinitionController;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ExportTableDefinitionModule の束縛定義に関するテスト<br>
 * 実際のDIコンテナを組み立て、エントリーポイントが取得するコントローラーまでの依存関係が解決できることを確認する。
 * 束縛の追加漏れや、{@code @Inject}の付け忘れを実行前に検知するため（DBへの接続は行わない）
 */
public class ExportTableDefinitionModuleTest {

  @Test
  @DisplayName("PostgreSQL接続時: コントローラーまでの依存関係を解決でき、PostgreSQL用のリポジトリが束縛される")
  void testResolvesControllerForPostgresql() {
    final Injector injector =
        Guice.createInjector(new ExportTableDefinitionModule(DatabaseType.POSTGRESQL));

    assertNotNull(injector.getInstance(ExportTableDefinitionController.class));
    assertInstanceOf(
        PostgresTableDefinitionRepository.class,
        injector.getInstance(TableDefinitionRepository.class));
  }

  @Test
  @DisplayName("Oracle接続時: コントローラーまでの依存関係を解決でき、Oracle用のリポジトリが束縛される")
  void testResolvesControllerForOracle() {
    final Injector injector =
        Guice.createInjector(new ExportTableDefinitionModule(DatabaseType.ORACLE));

    assertNotNull(injector.getInstance(ExportTableDefinitionController.class));
    assertInstanceOf(
        OracleTableDefinitionRepository.class,
        injector.getInstance(TableDefinitionRepository.class));
  }
}
