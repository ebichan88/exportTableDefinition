package com.export_table_definition.mcp;

import com.export_table_definition.mcp.catalog.SchemaCatalog;
import com.export_table_definition.mcp.snapshot.SnapshotDirectoryReader;
import com.export_table_definition.mcp.tool.TableDefinitionTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * MCPサーバーのエントリーポイント<br>
 * 標準入出力（stdio）でMCPクライアントとやり取りする。標準出力はプロトコル専用のため、利用者向けの表示はすべて標準エラーへ出す
 */
public final class McpServerMain {

  /** 起動引数・スナップショットの誤りで起動できなかった場合の終了コード（cliの失敗と揃える） */
  static final int EXIT_USER_CORRECTABLE = 2;

  private static final String SERVER_NAME = "exportTableDefinition";

  private static final String INSTRUCTIONS =
      "DBのテーブル定義（exportTableDefinitionが出力したスキーマのスナップショット）を調べるサーバー。"
          + "テーブル名が分からなければsearch_tablesで探し、get_tableで定義を、"
          + "get_related_tablesでJOINに使う関連（外部キーと、DBに制約の無い論理リレーション）を確認する。";

  private McpServerMain() {}

  /**
   * MCPサーバーを起動するメソッド
   *
   * @param args {@code --snapshot=<ディレクトリ>}
   */
  public static void main(String[] args) {
    // 標準エラーの文字コードは実行環境のロケールに依存し、日本語が化けることがある。
    // MCPクライアントは標準エラーをログとして取り込むため、ロケールに関わらずUTF-8で出す
    System.setErr(
        new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
    final SchemaCatalog catalog;
    try {
      final ServerArguments arguments = ServerArguments.parse(args);
      catalog = new SnapshotDirectoryReader().read(arguments.snapshotDirectory());
      System.err.println(
          "[info]:"
              + catalog.tables().size()
              + "テーブルを読み込みました。 [snapshot="
              + arguments.snapshotDirectory().toAbsolutePath()
              + "]");
    } catch (UserCorrectableException e) {
      System.err.println("[error]:" + e.getMessage());
      System.exit(EXIT_USER_CORRECTABLE);
      return;
    }
    start(catalog);
  }

  /** stdioのトランスポートでサーバーを起動する。標準入力が閉じられるまでトランスポートのスレッドが応答し続ける */
  private static void start(SchemaCatalog catalog) {
    final McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());
    McpServer.sync(new StdioServerTransportProvider(jsonMapper))
        .serverInfo(SERVER_NAME, version())
        .instructions(INSTRUCTIONS)
        .capabilities(ServerCapabilities.builder().tools(false).build())
        .jsonMapper(jsonMapper)
        .tools(new TableDefinitionTools(catalog).specifications())
        .build();
  }

  /** jarのマニフェストのバージョン。jarから起動していない場合（テスト等）は{@code dev} */
  private static String version() {
    return Objects.requireNonNullElse(
        McpServerMain.class.getPackage().getImplementationVersion(), "dev");
  }
}
