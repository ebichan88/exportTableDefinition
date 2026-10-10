package com.dbxray.mcp.tool;

import static com.dbxray.mcp.tool.Page.withPageProperties;
import static com.dbxray.mcp.tool.ToolSpecifications.DATABASE_PROPERTY;
import static com.dbxray.mcp.tool.ToolSpecifications.SCHEMA_FILTER_PROPERTY;
import static com.dbxray.mcp.tool.ToolSpecifications.booleanProperty;
import static com.dbxray.mcp.tool.ToolSpecifications.integerProperty;
import static com.dbxray.mcp.tool.ToolSpecifications.objectSchema;
import static com.dbxray.mcp.tool.ToolSpecifications.readOnlyTool;

import com.dbxray.mcp.catalog.SchemaCatalog;
import com.dbxray.mcp.catalog.TableCluster;
import com.dbxray.mcp.catalog.TableClusters;
import com.dbxray.mcp.catalog.TableEntry;
import com.dbxray.mcp.catalog.ViewpointEntry;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.List;
import java.util.Map;

/** 関連のつながりからテーブルのまとまりを推測するツール（{@code list_table_clusters}） */
final class ClusterTools {

  static final String LIST_TABLE_CLUSTERS = "list_table_clusters";

  /** 観点（人が宣言する業務のまとまり）の大きさと同じ程度にする */
  private static final int DEFAULT_MAX_CLUSTER_SIZE = 30;

  private static final int MIN_MAX_CLUSTER_SIZE = 2;
  private static final int MAX_MAX_CLUSTER_SIZE = 500;
  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 100;

  /** 1つのまとまりについて返すテーブルの上限。ハブを除いても分けられない大きなまとまりで結果が膨らまないようにする */
  private static final int MAX_LISTED_TABLES = 100;

  private final SchemaCatalog catalog;

  ClusterTools(SchemaCatalog catalog) {
    this.catalog = catalog;
  }

  /** ツールの一覧 */
  List<SyncToolSpecification> specifications() {
    return List.of(
        readOnlyTool(
            LIST_TABLE_CLUSTERS,
            "関連のまとまり",
            "外部キー・論理リレーションでつながるテーブルのまとまりを推測して返す。関連のつながりだけから求めた推測で、"
                + "人が宣言した観点（list_viewpoints）ではない。観点が宣言されていない範囲で業務のまとまりの見当を付けるときや、"
                + "観点を宣言する雛形に使う（宣言済みの観点があればそちらを優先する）。"
                + "まとまりがmaxClusterSizeを超える間は、被参照の最も多いテーブル（"
                + TableClusters.HUB_MIN_INCOMING
                + "テーブル以上から参照される共通のマスタ）をハブとして除いて分け直す。"
                + "ハブだけと関連を持つテーブルは、最も被参照の多いハブごとに1つのまとまりにする。"
                + "各まとまりは、被参照の多い順のテーブル（先頭が代表）、まとまりが参照するハブ、所属するテーブルを含む観点を返す。"
                + "範囲内のどのテーブルとも関連を持たないテーブルは、まとまりに含めず数だけを返す",
            objectSchema(
                withPageProperties(
                    Map.of(
                        "schema",
                        SCHEMA_FILTER_PROPERTY,
                        "database",
                        DATABASE_PROPERTY,
                        "maxClusterSize",
                        integerProperty(
                            "まとまりのテーブル数の上限（既定"
                                + DEFAULT_MAX_CLUSTER_SIZE
                                + "）。小さくすると細かく分かれる。ハブを除いても分けられない場合は超えることがある",
                            MIN_MAX_CLUSTER_SIZE,
                            MAX_MAX_CLUSTER_SIZE),
                        "excludeViewpointTables",
                        booleanProperty("いずれかの観点に所属するテーブルを除き、観点が未宣言の範囲だけでまとまりを求める（既定false）")),
                    DEFAULT_LIMIT,
                    MAX_LIMIT),
                List.of()),
            this::listTableClusters));
  }

  private CallToolResult listTableClusters(ToolArguments arguments) {
    final int maxClusterSize = arguments.optionalInt("maxClusterSize", DEFAULT_MAX_CLUSTER_SIZE);
    final boolean excludeViewpointTables =
        arguments.optionalBoolean("excludeViewpointTables", false);
    final Page page = Page.read(arguments, DEFAULT_LIMIT);
    final TableClusters found =
        catalog.tableClusters(arguments.scope(), maxClusterSize, excludeViewpointTables);
    final List<ViewpointEntry> viewpoints = catalog.listViewpoints(arguments.scope());
    return ToolResults.json(
        new ListTableClustersOutput(
            found.clusters().size(),
            page.nextOffset(found.clusters().size()),
            found.unrelatedTables(),
            found.hubs().stream().map(this::hubOf).toList(),
            page.apply(found.clusters()).stream()
                .map(cluster -> ListTableClustersOutput.Cluster.of(cluster, viewpoints))
                .toList()));
  }

  private ListTableClustersOutput.Hub hubOf(TableEntry table) {
    return new ListTableClustersOutput.Hub(
        table.key().database(),
        table.key().qualifiedName(),
        table.logicalName(),
        catalog.relationCountsOf(table).incoming());
  }

  /**
   * {@code list_table_clusters}の結果
   *
   * @param total まとまりの数
   * @param nextOffset 続きを取得するときに指定する{@code offset}。続きが無い場合はnull
   * @param unrelatedTables 範囲内のどのテーブルとも関連を持たないため、まとまりに含めなかったテーブルの数
   * @param hubs まとまりを分けるために除いた共通のテーブル（被参照の多い順）
   */
  record ListTableClustersOutput(
      int total, Integer nextOffset, int unrelatedTables, List<Hub> hubs, List<Cluster> clusters) {

    /**
     * まとまりを分けるために除いた共通のテーブル
     *
     * @param name {@code スキーマ名.テーブル名}
     * @param incoming 自テーブルを参照しているテーブルの数（{@code list_tables}の{@code incoming}と同じ）
     */
    record Hub(String database, String name, String logicalName, int incoming) {}

    /**
     * 1つのまとまり
     *
     * @param representative 代表のテーブル（まとまりの中で被参照のテーブル数が最も多いもの。{@code スキーマ名.テーブル名}）
     * @param size まとまりのテーブル数
     * @param tables 被参照の多い順のテーブル（上限まで）
     * @param hasMoreTables 上限で切り捨てたテーブルがある場合はtrue。無い場合はnull
     * @param hubs まとまりのテーブルと関連を持つハブ（{@code スキーマ名.テーブル名}）
     * @param viewpoints まとまりのテーブルを1つ以上含む観点のid（宣言順）
     */
    record Cluster(
        String database,
        String representative,
        int size,
        List<Member> tables,
        Boolean hasMoreTables,
        List<String> hubs,
        List<String> viewpoints) {

      static Cluster of(TableCluster cluster, List<ViewpointEntry> viewpoints) {
        final List<TableEntry> tables = cluster.tables();
        return new Cluster(
            cluster.representative().key().database(),
            cluster.representative().key().qualifiedName(),
            tables.size(),
            tables.stream().limit(MAX_LISTED_TABLES).map(Member::of).toList(),
            tables.size() > MAX_LISTED_TABLES ? Boolean.TRUE : null,
            cluster.hubs().stream().map(hub -> hub.key().qualifiedName()).toList(),
            viewpoints.stream()
                .filter(
                    viewpoint -> tables.stream().anyMatch(table -> viewpoint.contains(table.key())))
                .map(ViewpointEntry::id)
                .toList());
      }
    }

    /**
     * まとまりに属するテーブル
     *
     * @param name {@code スキーマ名.テーブル名}
     */
    record Member(String name, String logicalName) {

      static Member of(TableEntry table) {
        return new Member(table.key().qualifiedName(), table.logicalName());
      }
    }
  }
}
