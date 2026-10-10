package com.dbxray.testsupport;

import com.dbxray.infrastructure.db.ConnectionSettings;
import com.dbxray.infrastructure.db.MyBatisSqlSessionFactories;
import java.util.Map;
import org.apache.ibatis.session.SqlSessionFactory;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Oracleの結合テストで使うサンプルDB（{@code docs/sample/oracle/ddl.sql}を流し込んだOracle Database Free）<br>
 * コンテナの起動は重いため、テストクラスをまたいで1つのコンテナを使い回す（最初に使われた時に起動し、JVMの終了時に
 * Testcontainersが破棄する）。Dockerが使えない場合は、テストを黙ってスキップせず失敗させる
 */
public final class OracleSampleDatabase {

  /** ベースライン（{@code docs/sample/oracle/output}）の出力に使ったDB名（接続先のPDB名） */
  public static final String DATABASE_NAME = "FREEPDB1";

  /** DDLが作るスキーマ（ユーザー）。接続にもこのユーザーを使う */
  public static final String SCHEMA = "SAMPLE";

  /** DDLで作るユーザーのパスワード */
  private static final String PASSWORD = "sample";

  private static final String DDL_PATH = "../docs/sample/oracle/ddl.sql";

  /** DB作成済みのイメージ（faststart）を使い、起動を数十秒に抑える */
  private static final String IMAGE = "gvenzl/oracle-free:23-slim-faststart";

  private static final String DDL_IN_CONTAINER = "/opt/sample/ddl.sql";

  /** 初期化スクリプト。イメージは初期化スクリプトをCDBのルートで実行するが、DDLはPDBに接続して流す前提のため、PDBへ切り替えてから流す */
  private static final String INIT_SCRIPT =
      "alter session set container = " + DATABASE_NAME + ";\n@" + DDL_IN_CONTAINER + "\n";

  private static SqlSessionFactory sqlSessionFactory;

  private OracleSampleDatabase() {}

  /**
   * サンプルDBへ接続するSqlSessionFactoryを取得するメソッド（初回はコンテナを起動する）
   *
   * @return サンプルDBへ{@link #SCHEMA}のユーザーで接続するSqlSessionFactory
   */
  public static synchronized SqlSessionFactory sqlSessionFactory() {
    if (sqlSessionFactory == null) {
      sqlSessionFactory = MyBatisSqlSessionFactories.create(start());
    }
    return sqlSessionFactory;
  }

  /**
   * コンテナを起動し、DB接続情報を返すメソッド<br>
   * DDLは{@code whenever sqlerror exit failure}で流すため、DDLに誤りがあれば以降を流さずに止まり、起動待ちが失敗する
   *
   * @return 起動したコンテナのDB接続情報
   */
  @SuppressWarnings("resource") // JVMの終了時にTestcontainersが破棄する
  private static ConnectionSettings start() {
    final OracleContainer container =
        new OracleContainer(IMAGE)
            .withCopyFileToContainer(MountableFile.forHostPath(DDL_PATH), DDL_IN_CONTAINER)
            .withCopyToContainer(
                Transferable.of(INIT_SCRIPT), "/container-entrypoint-initdb.d/init.sql");
    container.start();
    return ConnectionSettings.of(
        Map.of(
            "driver",
            container.getDriverClassName(),
            "url",
            container.getJdbcUrl(),
            "username",
            SCHEMA,
            "password",
            PASSWORD));
  }
}
