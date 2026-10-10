package com.export_table_definition.mcp.tool;

import com.export_table_definition.mcp.catalog.Lookup;
import com.export_table_definition.mcp.catalog.ObjectKey;
import com.export_table_definition.mcp.catalog.ObjectReference;
import com.export_table_definition.mcp.catalog.SchemaCatalog;
import com.export_table_definition.mcp.catalog.SearchScope;
import com.export_table_definition.mcp.catalog.TableEntry;
import io.modelcontextprotocol.server.McpServerFeatures.SyncCompletionSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceTemplateSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.CompleteRequest;
import io.modelcontextprotocol.spec.McpSchema.CompleteResult;
import io.modelcontextprotocol.spec.McpSchema.CompleteResult.CompleteCompletion;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.ResourceReference;
import io.modelcontextprotocol.spec.McpSchema.ResourceTemplate;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * テーブルの定義をMCPのリソースとして返す（検証用の試作）<br>
 * 利用者がMCPクライアントの{@code @}等で添付する用途を想定し、{@code get_table}と同じ内容を返す。 出し方は、全テーブルを具体的なリソースとして列挙する{@link
 * Mode#LIST}と、URIテンプレートと変数の補完で指す{@link Mode#TEMPLATE}の2通りで、 MCPクライアントごとの対応が異なるため別々に切り替えられる
 */
public final class TableDefinitionResources {

  /** 検証のため、出し方を切り替える起動時のシステムプロパティ名（例: {@code -Dmcp.resources=list,template}） */
  public static final String MODE_PROPERTY = "mcp.resources";

  /** リソースの出し方 */
  public enum Mode {
    /** 全テーブルを具体的なリソースとして列挙する（{@code resources/list}） */
    LIST,
    /** URIテンプレートと変数の補完で指す（{@code resources/templates/list}・{@code completion/complete}） */
    TEMPLATE;

    /**
     * システムプロパティの値から出し方を求めるメソッド
     *
     * @param value 例: {@code list,template}・{@code list}・{@code none}。未指定（nullまたは空）の場合は両方
     * @throws IllegalArgumentException 解釈できない値を含む場合
     */
    public static Set<Mode> parse(String value) {
      final Set<Mode> modes = EnumSet.noneOf(Mode.class);
      if (value == null || value.isBlank()) {
        return EnumSet.allOf(Mode.class);
      }
      for (final String token : value.split(",")) {
        final String name = token.strip().toLowerCase(Locale.ROOT);
        switch (name) {
          case "list" -> modes.add(LIST);
          case "template" -> modes.add(TEMPLATE);
          case "none", "" -> {}
          default ->
              throw new IllegalArgumentException(
                  "-D" + MODE_PROPERTY + "には list・template・none をカンマ区切りで指定してください。");
        }
      }
      return modes;
    }
  }

  private static final String MIME_TYPE = "application/json";

  /** 補完で返す値の上限（MCPの仕様上の上限） */
  private static final int MAX_COMPLETIONS = 100;

  private static final int MAX_DESCRIPTION_LENGTH = 120;

  private final SchemaCatalog catalog;
  private final Set<Mode> modes;
  private final TableOutputBuilder outputBuilder;

  /**
   * @param catalog 返す対象のテーブルを持つカタログ
   * @param modes 有効にする出し方
   */
  public TableDefinitionResources(SchemaCatalog catalog, Set<Mode> modes) {
    this.catalog = catalog;
    this.modes = modes.isEmpty() ? EnumSet.noneOf(Mode.class) : EnumSet.copyOf(modes);
    this.outputBuilder = new TableOutputBuilder(catalog, Set.of(), List.of());
  }

  /** 具体的なリソース（全テーブル）を出すか */
  public boolean listEnabled() {
    return modes.contains(Mode.LIST);
  }

  /** URIテンプレートと補完を出すか */
  public boolean templateEnabled() {
    return modes.contains(Mode.TEMPLATE);
  }

  /**
   * 全テーブルを具体的なリソースとして返すメソッド
   *
   * @return {@link Mode#LIST}が無効の場合は空
   */
  public List<SyncResourceSpecification> resourceSpecifications() {
    if (!listEnabled()) {
      return List.of();
    }
    return catalog.tables().stream()
        .map(table -> new SyncResourceSpecification(resourceOf(table), this::read))
        .toList();
  }

  /**
   * URIテンプレートを返すメソッド
   *
   * @return {@link Mode#TEMPLATE}が無効の場合は空
   */
  public List<SyncResourceTemplateSpecification> templateSpecifications() {
    if (!templateEnabled()) {
      return List.of();
    }
    final ResourceTemplate template =
        ResourceTemplate.builder()
            .uriTemplate(TableResourceUri.TEMPLATE)
            .name("テーブル定義")
            .title("テーブル定義")
            .description("テーブル（ビューを含む）の定義。DB名・スキーマ名・テーブル名（物理名）を指定する")
            .mimeType(MIME_TYPE)
            .build();
    return List.of(new SyncResourceTemplateSpecification(template, this::read));
  }

  /**
   * URIテンプレートの変数（DB名・スキーマ名・テーブル名）の補完を返すメソッド
   *
   * @return {@link Mode#TEMPLATE}が無効の場合は空
   */
  public List<SyncCompletionSpecification> completionSpecifications() {
    if (!templateEnabled()) {
      return List.of();
    }
    return List.of(
        new SyncCompletionSpecification(
            new ResourceReference(TableResourceUri.TEMPLATE), this::complete));
  }

  /**
   * 具体的なリソースの表示名<br>
   * MCPクライアントの絞り込み検索（Claude Codeは{@code name}・{@code description}・URIが対象。{@code title}は対象外）で、
   * 論理名・物理名のどちらでも探せるよう両方を含める
   */
  static String displayName(TableEntry table) {
    final String qualified = table.key().qualifiedName();
    return table.logicalName().isEmpty() ? qualified : table.logicalName() + " (" + qualified + ")";
  }

  private Resource resourceOf(TableEntry table) {
    final String name = displayName(table);
    return Resource.builder()
        .uri(TableResourceUri.of(table.key()))
        .name(name)
        .title(name)
        .description(description(table))
        .mimeType(MIME_TYPE)
        .build();
  }

  /**
   * 具体的なリソースの説明<br>
   * Claude Codeの{@code @}の候補は、URIが途中で切れる（{@code etd-list:exporttable://testdb…}）うえ、表示に{@code
   * name}より {@code description}を優先するため、どのテーブルか分かるよう先頭に表示名を入れる
   */
  private static String description(TableEntry table) {
    final String base = displayName(table) + " / " + table.key().database() + " / " + table.type();
    if (table.description().isEmpty()) {
      return base;
    }
    final String text = base + " / " + table.description().replaceAll("\\s+", " ");
    return text.length() <= MAX_DESCRIPTION_LENGTH
        ? text
        : text.substring(0, MAX_DESCRIPTION_LENGTH) + "…";
  }

  private ReadResourceResult read(McpSyncServerExchange exchange, ReadResourceRequest request) {
    final String uri = request.uri();
    final ObjectKey key =
        TableResourceUri.parse(uri)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "URIは exporttable://{database}/{schema}/{table} の形で指定してください。"));
    final Lookup<TableEntry> lookup =
        catalog.lookupTable(
            new ObjectReference(new SearchScope(key.database(), key.schema()), key.name()));
    return switch (lookup) {
      case Lookup.Found<TableEntry> found ->
          new ReadResourceResult(
              List.of(
                  new TextResourceContents(
                      uri, MIME_TYPE, outputBuilder.build(found.value()).toString())));
      case Lookup.Ambiguous<TableEntry> ambiguous ->
          throw new IllegalArgumentException("同名のテーブルが複数あります。DB名・スキーマ名を含めたURIを指定してください。");
      case Lookup.NotFound<TableEntry> notFound -> throw McpError.RESOURCE_NOT_FOUND.apply(uri);
    };
  }

  private CompleteResult complete(McpSyncServerExchange exchange, CompleteRequest request) {
    final String variable = request.argument().name();
    final String typed = normalize(request.argument().value());
    final Map<String, String> resolved =
        request.context() == null || request.context().arguments() == null
            ? Map.of()
            : request.context().arguments();
    final String database = decoded(resolved.get("database"));
    final String schema = decoded(resolved.get("schema"));
    final Predicate<TableEntry> inScope =
        table ->
            (database.isEmpty() || table.key().database().equals(database))
                && (schema.isEmpty() || table.key().schema().equals(schema));

    final List<String> candidates =
        switch (variable) {
          case "database" ->
              catalog.tables().stream()
                  .map(table -> table.key().database())
                  .distinct()
                  .filter(name -> normalize(name).contains(typed))
                  .sorted()
                  .toList();
          case "schema" ->
              catalog.tables().stream()
                  .filter(table -> database.isEmpty() || table.key().database().equals(database))
                  .map(table -> table.key().schema())
                  .distinct()
                  .filter(name -> normalize(name).contains(typed))
                  .sorted()
                  .toList();
          case "table" ->
              catalog.tables().stream()
                  .filter(inScope)
                  .filter(table -> matchesTable(table, typed))
                  .sorted(tableOrder(typed))
                  .map(table -> table.key().name())
                  .distinct()
                  .toList();
          default -> List.of();
        };
    final List<String> values =
        candidates.stream().limit(MAX_COMPLETIONS).map(TableResourceUri::encode).toList();
    return new CompleteResult(
        new CompleteCompletion(values, candidates.size(), candidates.size() > values.size()));
  }

  /** 物理名・スキーマ修飾名・論理名のいずれかに入力が含まれるか（論理名でも物理名でも補完できるようにする） */
  private static boolean matchesTable(TableEntry table, String typed) {
    return normalize(table.key().name()).contains(typed)
        || normalize(table.key().qualifiedName()).contains(typed)
        || normalize(table.logicalName()).contains(typed);
  }

  /** 物理名の前方一致、物理名の部分一致、論理名のみの一致の順に並べ、同順位は物理名の順にする */
  private static Comparator<TableEntry> tableOrder(String typed) {
    return Comparator.comparingInt((TableEntry table) -> rank(table, typed))
        .thenComparing(table -> table.key().name());
  }

  private static int rank(TableEntry table, String typed) {
    final String name = normalize(table.key().name());
    if (name.startsWith(typed)) {
      return 0;
    }
    return name.contains(typed) ? 1 : 2;
  }

  private static String decoded(String value) {
    return value == null ? "" : TableResourceUri.decode(value);
  }

  private static String normalize(String value) {
    return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT);
  }
}
