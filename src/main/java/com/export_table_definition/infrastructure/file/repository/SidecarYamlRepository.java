package com.export_table_definition.infrastructure.file.repository;

import com.export_table_definition.domain.model.relation.Cardinality;
import com.export_table_definition.domain.model.relation.ForeignKeyEntity;
import com.export_table_definition.domain.model.sidecar.Annotations;
import com.export_table_definition.domain.model.sidecar.Sidecar;
import com.export_table_definition.domain.model.sidecar.TableAnnotation;
import com.export_table_definition.domain.model.table.TableKey;
import com.export_table_definition.domain.model.viewpoint.Viewpoint;
import com.export_table_definition.domain.model.viewpoint.Viewpoints;
import com.export_table_definition.domain.repository.SidecarRepository;
import com.export_table_definition.shared.exception.UserCorrectableException;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * サイドカーYAMLから手動付帯情報・論理リレーション・観点を読み込むリポジトリ実装クラス<br>
 * 想定するYAMLの構造は以下の通り。
 *
 * <pre>
 * tables:
 *   public.users:
 *     description: |
 *       テーブル説明（複数行可）
 *     remarks: テーブル備考
 *     columns:
 *       email: カラム備考
 * relations:
 *   - table: public.logs
 *     columns: [user_id]
 *     parentTable: public.users
 *     parentColumns: [id]
 *     name: rel_logs_users
 *     cardinality: 1対多
 * viewpoints:
 *   - id: order
 *     name: 受注管理
 *     description: 観点の説明（複数行可）
 *     tables: [sales.order*, sales.customer, "!sales.order_bk"]
 * </pre>
 */
public class SidecarYamlRepository implements SidecarRepository {

  private static final Logger logger = LogManager.getLogger(SidecarYamlRepository.class);

  private static final String KEY_TABLES = "tables";
  private static final String KEY_DESCRIPTION = "description";
  private static final String KEY_REMARKS = "remarks";
  private static final String KEY_COLUMNS = "columns";

  private static final String KEY_RELATIONS = "relations";
  private static final String KEY_TABLE = "table";
  private static final String KEY_PARENT_TABLE = "parentTable";
  private static final String KEY_PARENT_COLUMNS = "parentColumns";
  private static final String KEY_NAME = "name";
  private static final String KEY_CARDINALITY = "cardinality";

  private static final String KEY_VIEWPOINTS = "viewpoints";
  private static final String KEY_ID = "id";

  private static final Set<String> ROOT_KEYS = Set.of(KEY_TABLES, KEY_RELATIONS, KEY_VIEWPOINTS);
  private static final Set<String> TABLE_KEYS = Set.of(KEY_DESCRIPTION, KEY_REMARKS, KEY_COLUMNS);
  private static final Set<String> RELATION_KEYS =
      Set.of(
          KEY_TABLE, KEY_COLUMNS, KEY_PARENT_TABLE, KEY_PARENT_COLUMNS, KEY_NAME, KEY_CARDINALITY);
  private static final Set<String> VIEWPOINT_KEYS =
      Set.of(KEY_ID, KEY_NAME, KEY_DESCRIPTION, KEY_TABLES);

  /** {@inheritDoc} */
  @Override
  public Sidecar load(String sidecarPath) {
    if (sidecarPath == null || sidecarPath.isBlank()) {
      return Sidecar.empty();
    }
    final Path path = requireFile(sidecarPath.trim());
    try (final Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      final Object root = parseYaml(reader, path);
      if (root != null && !(root instanceof Map)) {
        logger.warn(
            "Ignoring the annotation file because its top level is not a mapping of '{}', '{}' and '{}'. "
                + "[annotationPath={}]",
            KEY_TABLES,
            KEY_RELATIONS,
            KEY_VIEWPOINTS,
            path);
        return Sidecar.empty();
      }
      warnUnknownKeys(asMap(root), ROOT_KEYS, "the annotation file", path);
      final Annotations annotations = parseAnnotations(root, path);
      final List<ForeignKeyEntity> logicalRelations = parseRelations(root, path);
      final Viewpoints viewpoints = parseViewpoints(root, path);
      logger.info(
          "Loaded sidecar. [annotationPath={}, tableCount={}, relationCount={}, viewpointCount={}]",
          path,
          annotations.tableKeys().size(),
          logicalRelations.size(),
          viewpoints.asList().size());
      return new Sidecar(annotations, logicalRelations, viewpoints);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to read the annotation file. [annotationPath=" + path + "]", e);
    }
  }

  /**
   * 指定したのにファイルが無い場合に付帯情報なしで続行すると、付帯情報の消えた定義書が気付かれずに出力されるため、 利用者が直せる誤りとして報告する
   *
   * @param sidecarPath サイドカーYAMLのパス（前後の空白を除去済み）
   * @throws UserCorrectableException パスとして解釈できない場合や、ファイルが存在しない・ファイルでない場合
   */
  private Path requireFile(String sidecarPath) {
    final Path path;
    try {
      path = Path.of(sidecarPath);
    } catch (InvalidPathException e) {
      throw new UserCorrectableException(
          "annotationPath is not a valid path. [annotationPath=" + sidecarPath + "]", e);
    }
    if (!Files.exists(path)) {
      throw new UserCorrectableException(
          "Annotation file not found. Check annotationPath in ExportTableDefinition.properties "
              + "(or the --annotation-path argument). "
              + "[annotationPath="
              + path.toAbsolutePath().normalize()
              + "]");
    }
    if (!Files.isRegularFile(path)) {
      throw new UserCorrectableException(
          "annotationPath does not point to a file. [annotationPath="
              + path.toAbsolutePath().normalize()
              + "]");
    }
    return path;
  }

  /**
   * YAMLとして解釈できないのは利用者が手で書いたファイルの誤りのため、どのファイルを直せばよいかを添えて {@link UserCorrectableException}として伝える
   * （解析の失敗箇所は原因の例外のメッセージが示す）
   *
   * @throws UserCorrectableException YAMLとして解釈できない場合（構文誤り・UTF-8以外の文字コード等）
   */
  private Object parseYaml(Reader reader, Path path) {
    try {
      return new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
    } catch (YAMLException e) {
      throw new UserCorrectableException(
          "Failed to parse the annotation file. Check that it is valid YAML saved in UTF-8. "
              + "[annotationPath="
              + path
              + "]",
          e);
    }
  }

  /** 想定外の構造の箇所は警告ログを出して読み飛ばし、可能な範囲で読み込みを継続する */
  private Annotations parseAnnotations(Object root, Path path) {
    final Object tablesValue = mapValue(root, KEY_TABLES);
    if (tablesValue != null && !(tablesValue instanceof Map)) {
      logger.warn(
          "Ignoring '{}' because it is not a mapping. [annotationPath={}]", KEY_TABLES, path);
      return Annotations.empty();
    }
    final Map<String, Object> tables = asMap(tablesValue);
    if (tables.isEmpty()) {
      // 論理リレーションのみを記述したサイドカーも有効なため、tablesが無いことは異常ではない
      logger.debug("Annotation file has no 'tables' entries. [annotationPath={}]", path);
      return Annotations.empty();
    }
    final Map<TableKey, TableAnnotation> byKey = new LinkedHashMap<>();
    tables.forEach(
        (rawKey, value) -> {
          final TableKey tableKey = toTableKey(rawKey, KEY_TABLES, path);
          if (tableKey == null) {
            return;
          }
          if (value != null && !(value instanceof Map)) {
            logger.warn(
                "Ignoring '{}' entry because it is not a mapping. [table={}, annotationPath={}]",
                KEY_TABLES,
                tableKey.qualifiedName(),
                path);
            return;
          }
          byKey.put(tableKey, toTableAnnotation(asMap(value), tableKey, path));
        });
    return Annotations.of(byKey);
  }

  /** 参照元・参照先のテーブルや列が欠けている定義はER図の関連線を描けないため読み飛ばす。 列数の不一致は関連線の描画自体は成立するため、警告のみ出して定義は維持する */
  private List<ForeignKeyEntity> parseRelations(Object root, Path path) {
    final Object relations = mapValue(root, KEY_RELATIONS);
    if (relations == null) {
      return List.of();
    }
    if (!(relations instanceof List<?> relationList)) {
      logger.warn("Ignoring 'relations' because it is not a list. [annotationPath={}]", path);
      return List.of();
    }
    final List<ForeignKeyEntity> result = new ArrayList<>();
    relationList.forEach(
        relation -> {
          final ForeignKeyEntity entity = toLogicalRelation(asMap(relation), path);
          if (entity != null) {
            result.add(entity);
          }
        });
    return List.copyOf(result);
  }

  /**
   * 観点として成り立たない定義（識別子の誤り・所属テーブルの指定漏れ等）と、識別子が既出の定義は、警告ログを出して読み飛ばす
   * （識別子は観点ページのファイル名になるため、重複すると後の観点のページで先の観点のページを上書きしてしまう）
   */
  private Viewpoints parseViewpoints(Object root, Path path) {
    final Object viewpoints = mapValue(root, KEY_VIEWPOINTS);
    if (viewpoints == null) {
      return Viewpoints.empty();
    }
    if (!(viewpoints instanceof List<?> viewpointList)) {
      logger.warn(
          "Ignoring '{}' because it is not a list. [annotationPath={}]", KEY_VIEWPOINTS, path);
      return Viewpoints.empty();
    }
    final Map<String, Viewpoint> byId = new LinkedHashMap<>();
    viewpointList.forEach(
        viewpointValue -> {
          final Viewpoint viewpoint = toViewpoint(asMap(viewpointValue), path);
          if (viewpoint == null) {
            return;
          }
          if (byId.containsKey(viewpoint.id())) {
            logger.warn(
                "Ignoring viewpoint with a duplicate 'id'. [id={}, annotationPath={}]",
                viewpoint.id(),
                path);
            return;
          }
          byId.put(viewpoint.id(), viewpoint);
        });
    return Viewpoints.of(List.copyOf(byId.values()));
  }

  /**
   * 値の検証（識別子の形式・所属テーブルのパターン）は{@link Viewpoint#of}に委ね、ここでは検証に失敗した場合の警告ログのみを担う
   *
   * @return 変換した観点。観点として成り立たない場合はnull
   */
  private Viewpoint toViewpoint(Map<String, Object> viewpointMap, Path path) {
    final String id = asString(viewpointMap.get(KEY_ID));
    warnUnknownKeys(viewpointMap, VIEWPOINT_KEYS, "'" + KEY_VIEWPOINTS + "' entry of " + id, path);
    try {
      return Viewpoint.of(
          id,
          asString(viewpointMap.get(KEY_NAME)),
          asString(viewpointMap.get(KEY_DESCRIPTION)),
          asStringList(viewpointMap.get(KEY_TABLES)));
    } catch (IllegalArgumentException e) {
      logger.warn("Ignoring viewpoint '{}'. {} [annotationPath={}]", id, e.getMessage(), path);
      return null;
    }
  }

  /**
   * @return 変換した論理リレーション。必須項目が欠けている場合はnull
   */
  private ForeignKeyEntity toLogicalRelation(Map<String, Object> relationMap, Path path) {
    warnUnknownKeys(
        relationMap,
        RELATION_KEYS,
        "'" + KEY_RELATIONS + "' entry of " + asString(relationMap.get(KEY_TABLE)),
        path);
    final TableKey child = toTableKey(asString(relationMap.get(KEY_TABLE)), KEY_RELATIONS, path);
    final TableKey parent =
        toTableKey(asString(relationMap.get(KEY_PARENT_TABLE)), KEY_RELATIONS, path);
    final List<String> childColumns = asStringList(relationMap.get(KEY_COLUMNS));
    final List<String> parentColumns = asStringList(relationMap.get(KEY_PARENT_COLUMNS));
    if (child == null || parent == null || childColumns.isEmpty() || parentColumns.isEmpty()) {
      logger.warn(
          "Ignoring relation missing required keys ('table', 'columns', 'parentTable', 'parentColumns'). "
              + "[relation={}, annotationPath={}]",
          relationMap,
          path);
      return null;
    }
    if (childColumns.size() != parentColumns.size()) {
      logger.warn(
          "Relation has a different number of 'columns' and 'parentColumns'. "
              + "[table={}, parentTable={}, annotationPath={}]",
          child.qualifiedName(),
          parent.qualifiedName(),
          path);
    }
    return ForeignKeyEntity.logical(
        child.schema(),
        child.table(),
        ForeignKeyEntity.resolveLogicalRelationName(
            asString(relationMap.get(KEY_NAME)), child.table(), childColumns),
        childColumns,
        parent.schema(),
        parent.table(),
        parentColumns,
        resolveCardinality(asString(relationMap.get(KEY_CARDINALITY)), child, path));
  }

  /**
   * DBに制約が存在せず機械的に判定できないため、YAMLでの明示指定を優先し、 未指定・不正な指定の場合は既定値（{@link
   * Cardinality#DEFAULT_FOR_LOGICAL_RELATION}）を用いる。未指定の場合は既定値へ黙って落とすが、不正な値が指定された場合は気付けるよう警告する
   *
   * @param label YAMLで指定された多重度のラベル（未指定可）
   * @param child 警告ログにのみ使う参照元（子）テーブルのキー
   */
  private Cardinality resolveCardinality(String label, TableKey child, Path path) {
    if (label == null || label.isBlank()) {
      return Cardinality.DEFAULT_FOR_LOGICAL_RELATION;
    }
    return Cardinality.fromLabel(label)
        .orElseGet(
            () -> {
              logger.warn(
                  "Ignoring unknown 'cardinality' and falling back to the default. "
                      + "[cardinality={}, default={}, table={}, annotationPath={}]",
                  label,
                  Cardinality.DEFAULT_FOR_LOGICAL_RELATION.getLabel(),
                  child.qualifiedName(),
                  path);
              return Cardinality.DEFAULT_FOR_LOGICAL_RELATION;
            });
  }

  /**
   * 解析自体は{@link TableKey#parse}に委ね、ここでは解析失敗時の警告ログのみを担う
   *
   * @return 変換したテーブルキー。形式が不正な場合はnull
   */
  private TableKey toTableKey(String rawKey, String sectionKey, Path path) {
    return TableKey.parse(rawKey)
        .orElseGet(
            () -> {
              logger.warn(
                  "Ignoring key not in 'schema.table' format. [key={}, section={}, annotationPath={}]",
                  rawKey,
                  sectionKey,
                  path);
              return null;
            });
  }

  private TableAnnotation toTableAnnotation(
      Map<String, Object> tableMap, TableKey tableKey, Path path) {
    warnUnknownKeys(
        tableMap, TABLE_KEYS, "'" + KEY_TABLES + "' entry of " + tableKey.qualifiedName(), path);
    final String description = asString(tableMap.get(KEY_DESCRIPTION));
    final String remarks = asString(tableMap.get(KEY_REMARKS));
    final Object columnsValue = tableMap.get(KEY_COLUMNS);
    if (columnsValue != null && !(columnsValue instanceof Map)) {
      logger.warn(
          "Ignoring '{}' because it is not a mapping of column names to remarks. "
              + "[table={}, annotationPath={}]",
          KEY_COLUMNS,
          tableKey.qualifiedName(),
          path);
    }
    final Map<String, Object> columns = asMap(columnsValue);
    final Map<String, String> columnRemarks = new LinkedHashMap<>();
    columns.forEach(
        (columnName, remark) -> {
          if (columnName != null && !columnName.isBlank()) {
            columnRemarks.put(columnName.trim(), asString(remark));
          }
        });
    return new TableAnnotation(description, remarks, columnRemarks);
  }

  /** 未知のキーは読み飛ばすが、キー名の書き誤り（{@code descripton}等）で付帯情報が黙って消えないよう警告する */
  private void warnUnknownKeys(
      Map<String, Object> map, Set<String> knownKeys, String location, Path path) {
    map.keySet().stream()
        .filter(key -> !knownKeys.contains(key))
        .forEach(
            key ->
                logger.warn(
                    "Ignoring unknown key in {}. [key={}, annotationPath={}]",
                    location,
                    key,
                    path));
  }

  private Object mapValue(Object obj, String key) {
    return asMap(obj).get(key);
  }

  /**
   * @return 対象がマップでない場合は空マップ
   */
  private Map<String, Object> asMap(Object obj) {
    if (!(obj instanceof Map<?, ?> map)) {
      return Map.of();
    }
    final Map<String, Object> result = new LinkedHashMap<>();
    map.forEach(
        (k, v) -> {
          if (k != null) {
            result.put(String.valueOf(k), v);
          }
        });
    return result;
  }

  /**
   * 単一列の関連を{@code columns: user_id}のように書けるよう、スカラー値も1要素のリストとして受け付ける
   *
   * @return 空白要素を除いた文字列のリスト。対象がリスト・スカラーのいずれでもない場合は空リスト
   */
  private List<String> asStringList(Object obj) {
    if (obj == null) {
      return List.of();
    }
    final List<?> rawList = obj instanceof List<?> list ? list : List.of(obj);
    return rawList.stream()
        .map(this::asString)
        .map(String::trim)
        .filter(Predicate.not(String::isBlank))
        .toList();
  }

  /**
   * @return nullの場合は空文字
   */
  private String asString(Object obj) {
    return obj == null ? "" : String.valueOf(obj);
  }
}
