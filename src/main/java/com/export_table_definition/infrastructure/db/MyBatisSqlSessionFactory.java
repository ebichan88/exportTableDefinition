package com.export_table_definition.infrastructure.db;

import com.export_table_definition.config.InvalidConfigurationException;
import com.export_table_definition.config.PropertyLoader;
import com.export_table_definition.domain.UserCorrectableException;
import com.export_table_definition.infrastructure.db.type.DatabaseType;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Properties;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * SqlSessionFactoryクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public final class MyBatisSqlSessionFactory {

  private static final Logger logger = LogManager.getLogger(MyBatisSqlSessionFactory.class);
  private static final String MYBATIS_CONFIG = "mybatis-config.xml";
  private static final String PROPERTY_BUNDLE_NAME = "mybatis";

  /** 唯一のSqlSessionFactoryインスタンス */
  private static SqlSessionFactory sqlSessionFactory;

  /** conf/mybatis.propertiesの値を上書きする接続情報（CLI引数・環境変数由来） */
  private static Properties connectionOverrides = new Properties();

  /** コンストラクタ（インスタンス化不可） */
  private MyBatisSqlSessionFactory() {}

  /**
   * conf/mybatis.propertiesの値を上書きする接続情報を設定する<br>
   * SqlSessionFactory初期化前（{@link #getSqlSessionFactory()}呼び出し前）に呼び出すこと
   *
   * @param overrides 上書きする接続情報（driver/url/username/password）
   * @throws IllegalStateException SqlSessionFactoryが初期化済みの場合
   */
  public static synchronized void setConnectionOverrides(Properties overrides) {
    if (sqlSessionFactory != null) {
      throw new IllegalStateException("SqlSessionFactory is already initialized.");
    }
    connectionOverrides = overrides;
  }

  /**
   * SqlSessionFactoryインスタンスの取得
   *
   * @return SqlSessionFactory
   */
  public static synchronized SqlSessionFactory getSqlSessionFactory() {
    if (sqlSessionFactory != null) {
      return sqlSessionFactory;
    }
    logger.info(
        "Initializing SqlSessionFactory from {} (bundle={})", MYBATIS_CONFIG, PROPERTY_BUNDLE_NAME);
    try (final InputStream inputStream = Resources.getResourceAsStream(MYBATIS_CONFIG)) {
      if (inputStream == null) {
        throw new IllegalStateException("Could not find resource: " + MYBATIS_CONFIG);
      }
      final Properties properties = loadBaseProperties();
      properties.putAll(connectionOverrides);
      sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream, properties);
    } catch (IOException e) {
      throw new IllegalStateException("SqlSessionFactory initialization failed.", e);
    }
    logger.info("SqlSessionFactory initialization completed.");
    return sqlSessionFactory;
  }

  /**
   * conf/mybatis.propertiesを読み込む<br>
   * ファイルが存在しない場合は空のPropertiesを返す（CLI引数・環境変数のみで接続情報を賄うケースを許容するため）
   *
   * @return 読み込んだProperties（ファイルが存在しない場合は空）
   */
  private static Properties loadBaseProperties() {
    try {
      return PropertyLoader.getProperties(PROPERTY_BUNDLE_NAME);
    } catch (InvalidConfigurationException e) {
      logger.info(
          "conf/mybatis.properties not found. Relying on CLI/env connection overrides only.");
      return new Properties();
    }
  }

  /**
   * SqlSession開始
   *
   * @return SqlSession
   */
  public static SqlSession openSession() {
    return getSqlSessionFactory().openSession();
  }

  /**
   * 接続するデータベースの名称を取得する
   *
   * @return 接続するデータベースの列挙型
   * @throws UserCorrectableException DBに接続できない場合や、接続先が対応していないDBの場合
   */
  public static DatabaseType getConnectionDbName() {
    final SqlSessionFactory factory = getSqlSessionFactory();
    try (final SqlSession session = factory.openSession()) {
      return toDatabaseType(connect(session).getMetaData().getDatabaseProductName());
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to get the database name.", e);
    }
  }

  /**
   * DBへ接続するメソッド<br>
   * 接続できないのは接続情報の誤りかDBが起動していない場合で、いずれも利用者が見直せば解消するため {@link
   * UserCorrectableException}として伝える（接続できなかった理由は原因の例外のメッセージが示す）
   *
   * @param session SqlSession
   * @return DBとの接続
   * @throws UserCorrectableException DBに接続できない場合
   */
  private static Connection connect(SqlSession session) {
    try {
      return session.getConnection();
    } catch (PersistenceException e) {
      throw new UserCorrectableException(
          "Could not connect to the database. Check the connection settings "
              + "(conf/mybatis.properties, --db-* arguments or DB_* environment variables) "
              + "and that the database is running.",
          e);
    }
  }

  /**
   * DBの製品名を、対応するDB種別へ変換するメソッド
   *
   * @param productName JDBCドライバが返すDBの製品名
   * @return DB種別
   * @throws UserCorrectableException 対応していないDBの場合（接続先の設定を見直せば解消する）
   */
  private static DatabaseType toDatabaseType(String productName) {
    try {
      return DatabaseType.findByName(productName.toLowerCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new UserCorrectableException(
          e.getMessage() + ". Connect to a supported database (PostgreSQL or Oracle).", e);
    }
  }
}
