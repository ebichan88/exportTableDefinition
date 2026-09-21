package com.export_table_definition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.export_table_definition.config.PropertyLoader;
import com.export_table_definition.config.module.ExportTableDefinitionModule;
import com.export_table_definition.infrastructure.db.MyBatisSqlSessionFactory;
import com.export_table_definition.presentation.ExportTableDefinitionController;
import com.export_table_definition.presentation.dto.ResultDto;
import com.google.inject.Guice;

/**
 * テーブル定義出力処理を呼び出すクラス
 * 
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class ExportTableDefinition {

    /** chunkSize未設定時のデフォルト値（1スキーマあたりこの件数ごとに詳細情報を取得・出力する） */
    private static final int DEFAULT_CHUNK_SIZE = 3000;
    /** DB接続情報の上書きに対応するプロパティキーと、対応するCLI引数名・環境変数名 */
    private static final Map<String, ConnectionArg> CONNECTION_ARGS = Map.of(
            "driver", new ConnectionArg("--db-driver", "DB_DRIVER"),
            "url", new ConnectionArg("--db-url", "DB_URL"),
            "username", new ConnectionArg("--db-username", "DB_USERNAME"),
            "password", new ConnectionArg("--db-password", "DB_PASSWORD"));
    private ExportTableDefinitionController controller;

    ExportTableDefinition(ExportTableDefinitionController controller) {
        this.controller = controller;
    }

    /**
     * テーブル定義出力処理のエントリーポイントメソッド
     *
     * @param args コマンドライン引数（{@code --db-url=...}のような{@code --キー=値}形式でDB接続情報を上書き可能。
     *             未指定の場合は同名の環境変数（例: {@code DB_URL}）、さらに未指定の場合は
     *             {@code conf/mybatis.properties}の値が使用される）
     */
    public static void main(String[] args) {
        MyBatisSqlSessionFactory.setConnectionOverrides(resolveConnectionOverrides(args));
        new ExportTableDefinition(Guice.createInjector(new ExportTableDefinitionModule())
                .getInstance(ExportTableDefinitionController.class)).run();
    }

    /**
     * CLI引数・環境変数からDB接続情報の上書き値を解決するメソッド<br>
     * 優先順位: CLI引数 &gt; 環境変数 &gt; （未指定の場合は{@code conf/mybatis.properties}の値をそのまま使用）
     *
     * @param args コマンドライン引数
     * @return 上書きするDB接続情報（未指定のキーは含まれない）
     */
    private static Properties resolveConnectionOverrides(String[] args) {
        final Map<String, String> cliArgs = parseArgs(args);
        final Properties overrides = new Properties();
        CONNECTION_ARGS.forEach((key, connectionArg) -> {
            final String value = cliArgs.containsKey(connectionArg.cliName()) ? cliArgs.get(connectionArg.cliName())
                    : System.getenv(connectionArg.envName());
            if (value != null && !value.isBlank()) {
                overrides.setProperty(key, value);
            }
        });
        return overrides;
    }

    /**
     * {@code --キー=値}形式のコマンドライン引数を解析するメソッド
     *
     * @param args コマンドライン引数
     * @return 引数名（{@code --}付き）と値のマップ
     */
    private static Map<String, String> parseArgs(String[] args) {
        final Map<String, String> result = new HashMap<>();
        for (final String arg : args) {
            final int separatorIndex = arg.indexOf('=');
            if (!arg.startsWith("--") || separatorIndex < 0) {
                continue;
            }
            result.put(arg.substring(0, separatorIndex), arg.substring(separatorIndex + 1));
        }
        return result;
    }

    /**
     * DB接続情報1項目分のCLI引数名・環境変数名の組
     *
     * @param cliName CLI引数名（{@code --}付き）
     * @param envName 環境変数名
     */
    private record ConnectionArg(String cliName, String envName) {
    }

    /**
     * テーブル定義出力処理実行メソッド
     */
    void run() {
        // プロパティファイルの読み込み
        final List<String> schemaList = PropertyLoader.getList("ExportTableDefinition", "schema");
        final List<String> tableList = PropertyLoader.getList("ExportTableDefinition", "table");
        final String outputPath = PropertyLoader.getString("ExportTableDefinition", "outputPath");
        final int chunkSize = PropertyLoader.getInt("ExportTableDefinition", "chunkSize", DEFAULT_CHUNK_SIZE);
        // 処理開始メッセージ出力
        System.out.println("""
                Starting output of table definition document.
                Please wait a moment ...
                """);
        // テーブル定義出力処理実行
        final ResultDto resultDto = controller.execute(schemaList, tableList, outputPath, chunkSize);
        // 処理終了メッセージ出力
        System.out.println(resultDto.getResultMessage());
    }
}
