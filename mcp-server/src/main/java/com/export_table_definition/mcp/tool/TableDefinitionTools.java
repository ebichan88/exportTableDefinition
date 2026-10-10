package com.export_table_definition.mcp.tool;

import com.export_table_definition.mcp.catalog.SchemaCatalog;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.util.List;
import java.util.stream.Stream;

/**
 * テーブル定義を調べるMCPのツールの一覧<br>
 * 結果はJSONの文字列で返す。値が無い項目（空文字・空リスト）は出力しない
 */
public final class TableDefinitionTools {

  private final SchemaCatalog catalog;

  /**
   * @param catalog 検索の対象にする全オブジェクト
   */
  public TableDefinitionTools(SchemaCatalog catalog) {
    this.catalog = catalog;
  }

  /**
   * MCPサーバーへ登録するツールの一覧を返すメソッド
   *
   * @return 全体像をつかむ・探す・詳細を見る、の順に並べたツール（MCPクライアントにはこの順で示される）
   */
  public List<SyncToolSpecification> specifications() {
    return Stream.of(
            new SchemaTools(catalog).specifications(),
            new ViewpointTools(catalog).specifications(),
            new ClusterTools(catalog).specifications(),
            new TableTools(catalog).specifications(),
            new RelationTools(catalog).specifications(),
            new FunctionTools(catalog).specifications(),
            new SequenceTools(catalog).specifications(),
            new TypeTools(catalog).specifications(),
            new TriggerTools(catalog).specifications())
        .flatMap(List::stream)
        .toList();
  }
}
