package com.export_table_definition.config.module;

import com.export_table_definition.application.CheckDocumentDiffUsecase;
import com.export_table_definition.application.ExportTableDefinitionUsecase;
import com.export_table_definition.application.impl.CheckDocumentDiffUsecaseImpl;
import com.export_table_definition.application.impl.ExportTableDefinitionUsecaseImpl;
import com.export_table_definition.domain.repository.TableDefinitionRepository;
import com.export_table_definition.infrastructure.db.type.DatabaseType;
import com.google.inject.AbstractModule;

/**
 * DB種別が決まってから束縛する依存関係のモジュール<br>
 * DBへ接続して接続先のDB種別を判定した後、{@link ExportTableDefinitionModule}で組み立てたDIコンテナの子として組み立てる。
 * DB種別で実装が変わる{@link TableDefinitionRepository}と、それに依存するユースケースを束縛する （親のコンテナでは、DB種別が未定のためこれらを解決できない）
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class DatabaseDependentModule extends AbstractModule {

  private final DatabaseType databaseType;

  /**
   * コンストラクタ<br>
   * 束縛の定義（{@link #configure()}）の中でDBへ接続しないよう、接続先DBの種別は呼び出し元で判定して受け取る。<br>
   * DIコンテナの中（束縛の定義やProvider）で接続すると、接続の失敗がGuiceの例外（CreationException・ProvisionException）に包まれて届き、
   * エントリーポイントが「DBに接続できない」を利用者が直せる誤りとして報告できなくなるため
   *
   * @param databaseType 接続先DBの種別（{@link TableDefinitionRepository}の実装クラスの選択に用いる）
   */
  public DatabaseDependentModule(DatabaseType databaseType) {
    this.databaseType = databaseType;
  }

  @Override
  protected void configure() {
    bind(TableDefinitionRepository.class).to(databaseType.getRepositoryClass());
    bind(ExportTableDefinitionUsecase.class).to(ExportTableDefinitionUsecaseImpl.class);
    bind(CheckDocumentDiffUsecase.class).to(CheckDocumentDiffUsecaseImpl.class);
  }
}
