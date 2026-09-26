package com.export_table_definition.testsupport;

import com.export_table_definition.infrastructure.db.ConnectionSettings;
import com.export_table_definition.infrastructure.db.MyBatisSqlSessionFactories;
import java.util.Map;
import org.apache.ibatis.session.SqlSessionFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * 結合テストで使うサンプルDB（{@code docs/sample/postgres/ddl.sql}を流し込んだPostgreSQL）<br>
 * コンテナの起動は重いため、テストクラスをまたいで1つのコンテナを使い回す（最初に使われた時に起動し、JVMの終了時に
 * Testcontainersが破棄する）。Dockerが使えない場合は、テストを黙ってスキップせず失敗させる
 */
public final class SampleDatabase {

  /** ベースライン（{@code docs/sample/postgres/output}）の出力に使ったDB名 */
  public static final String DATABASE_NAME = "testdb";

  /** ベースラインの出力に使ったDDL */
  private static final String DDL_PATH = "docs/sample/postgres/ddl.sql";

  /** verifyスキルの手順と同じイメージ */
  private static final String IMAGE = "postgres:16";

  private static SqlSessionFactory sqlSessionFactory;

  /** コンストラクタ（インスタンス化不可） */
  private SampleDatabase() {}

  /**
   * サンプルDBへ接続するSqlSessionFactoryを取得するメソッド（初回はコンテナを起動する）
   *
   * @return サンプルDBへ接続するSqlSessionFactory
   */
  public static synchronized SqlSessionFactory sqlSessionFactory() {
    if (sqlSessionFactory == null) {
      sqlSessionFactory = MyBatisSqlSessionFactories.create(start());
    }
    return sqlSessionFactory;
  }

  /**
   * コンテナを起動し、DB接続情報を返すメソッド<br>
   * DDLはイメージの初期化スクリプト（{@code ON_ERROR_STOP=1}で実行される）として流し込むため、DDLに誤りがあれば起動に失敗する
   *
   * @return 起動したコンテナのDB接続情報
   */
  @SuppressWarnings("resource") // JVMの終了時にTestcontainersが破棄する
  private static ConnectionSettings start() {
    final PostgreSQLContainer container =
        new PostgreSQLContainer(IMAGE)
            .withDatabaseName(DATABASE_NAME)
            .withCopyFileToContainer(
                MountableFile.forHostPath(DDL_PATH), "/docker-entrypoint-initdb.d/ddl.sql");
    container.start();
    return ConnectionSettings.of(
        Map.of(
            "driver", container.getDriverClassName(),
            "url", container.getJdbcUrl(),
            "username", container.getUsername(),
            "password", container.getPassword()));
  }
}
