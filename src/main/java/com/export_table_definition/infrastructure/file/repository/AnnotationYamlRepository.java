package com.export_table_definition.infrastructure.file.repository;

import com.export_table_definition.domain.model.annotation.Annotations;
import com.export_table_definition.domain.model.annotation.Sidecar;
import com.export_table_definition.domain.model.annotation.TableAnnotation;
import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.type.Cardinality;
import com.export_table_definition.domain.model.value.TableKey;
import com.export_table_definition.domain.repository.AnnotationRepository;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * サイドカーYAMLから手動付帯情報・論理リレーションを読み込むリポジトリ実装クラス<br>
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
 * </pre>
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class AnnotationYamlRepository implements AnnotationRepository {

  private static final Logger logger = LogManager.getLogger(AnnotationYamlRepository.class);

  /** YAMLのトップレベルキー（テーブルごとの付帯情報を束ねる） */
  private static final String KEY_TABLES = "tables";

  private static final String KEY_DESCRIPTION = "description";
  private static final String KEY_REMARKS = "remarks";
  private static final String KEY_COLUMNS = "columns";

  /** YAMLのトップレベルキー（論理リレーションの定義を束ねる） */
  private static final String KEY_RELATIONS = "relations";

  private static final String KEY_TABLE = "table";
  private static final String KEY_PARENT_TABLE = "parentTable";
  private static final String KEY_PARENT_COLUMNS = "parentColumns";
  private static final String KEY_NAME = "name";
  private static final String KEY_CARDINALITY = "cardinality";

  /** {@inheritDoc} */
  @Override
  public Sidecar load(String annotationPath) {
    if (annotationPath == null || annotationPath.isBlank()) {
      return Sidecar.empty();
    }
    final Path path = Path.of(annotationPath.trim());
    if (!Files.exists(path)) {
      logger.warn(
          "Annotation file not found. Skipping merge of manual annotations. [annotationPath={}]",
          path);
      return Sidecar.empty();
    }
    try (final Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      final Object root = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
      final Annotations annotations = parseAnnotations(root, path);
      final List<ForeignKeyEntity> logicalRelations = parseRelations(root, path);
      logger.info(
          "Loaded sidecar. [annotationPath={}, tableCount={}, relationCount={}]",
          path,
          annotations.tableKeys().size(),
          logicalRelations.size());
      return new Sidecar(annotations, logicalRelations);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to read the annotation file. [annotationPath=" + path + "]", e);
    }
  }

  /**
   * SnakeYAMLで読み込んだ生のオブジェクトから、テーブル単位の付帯情報を{@link Annotations}へ変換するメソッド<br>
   * 想定外の構造の箇所は警告ログを出して読み飛ばし、可能な範囲で読み込みを継続する
   *
   * @param root YAMLのルートオブジェクト
   * @param path 読み込み元のパス（ログ用）
   * @return 変換した付帯情報
   */
  private Annotations parseAnnotations(Object root, Path path) {
    final Map<String, Object> tables = asMap(mapValue(root, KEY_TABLES));
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
          byKey.put(tableKey, toTableAnnotation(asMap(value)));
        });
    return Annotations.of(byKey);
  }

  /**
   * SnakeYAMLで読み込んだ生のオブジェクトから、論理リレーションの定義を変換するメソッド<br>
   * 参照元・参照先のテーブルや列が欠けている定義はER図の関連線を描けないため読み飛ばす。 列数の不一致は関連線の描画自体は成立するため、警告のみ出して定義は維持する
   *
   * @param root YAMLのルートオブジェクト
   * @param path 読み込み元のパス（ログ用）
   * @return 変換した論理リレーションのリスト
   */
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
   * 論理リレーション1件分のマップを{@link ForeignKeyEntity}へ変換するメソッド
   *
   * @param relationMap 論理リレーション1件分のマップ
   * @param path 読み込み元のパス（ログ用）
   * @return 変換した論理リレーション。必須項目が欠けている場合はnull
   */
  private ForeignKeyEntity toLogicalRelation(Map<String, Object> relationMap, Path path) {
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
        String.join(",", childColumns),
        parent.schema(),
        parent.table(),
        String.join(",", parentColumns),
        resolveCardinality(asString(relationMap.get(KEY_CARDINALITY)), child, path));
  }

  /**
   * 論理リレーションの多重度を解決するメソッド<br>
   * DBに制約が存在せず機械的に判定できないため、YAMLでの明示指定を優先し、 未指定・不正な指定の場合は既定値（{@link
   * Cardinality#DEFAULT_FOR_LOGICAL_RELATION}）を用いる。 未指定の場合は既定値へ黙って落とすが、不正な値が指定された場合は気付けるよう警告する
   *
   * @param label YAMLで指定された多重度のラベル（未指定可）
   * @param child 参照元（子）テーブルのキー（ログ用）
   * @param path 読み込み元のパス（ログ用）
   * @return 解決した多重度
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
   * 「スキーマ.テーブル」形式のキー文字列を{@link TableKey}へ変換するメソッド<br>
   * 解析自体は{@link TableKey#parse}に委ね、ここでは解析失敗時の警告ログ（読み込み元のパス等の コンテキストを含む）のみを担う
   *
   * @param rawKey キー文字列
   * @param sectionKey 読み込み中のセクション名（ログ用）
   * @param path 読み込み元のパス（ログ用）
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

  /**
   * 1テーブル分の付帯情報マップを{@link TableAnnotation}へ変換するメソッド
   *
   * @param tableMap 1テーブル分の付帯情報マップ
   * @return 変換した付帯情報
   */
  private TableAnnotation toTableAnnotation(Map<String, Object> tableMap) {
    final String description = asString(tableMap.get(KEY_DESCRIPTION));
    final String remarks = asString(tableMap.get(KEY_REMARKS));
    final Map<String, Object> columns = asMap(tableMap.get(KEY_COLUMNS));
    final Map<String, String> columnRemarks = new LinkedHashMap<>();
    columns.forEach(
        (columnName, remark) -> {
          if (columnName != null && !columnName.isBlank()) {
            columnRemarks.put(columnName.trim(), asString(remark));
          }
        });
    return new TableAnnotation(description, remarks, columnRemarks);
  }

  /**
   * マップから指定キーの値を取得するメソッド
   *
   * @param obj 対象オブジェクト（マップ想定）
   * @param key 取得するキー
   * @return キーに対応する値。対象がマップでない場合や未設定の場合はnull
   */
  private Object mapValue(Object obj, String key) {
    return asMap(obj).get(key);
  }

  /**
   * オブジェクトをキー文字列のマップとして安全に取得するメソッド
   *
   * @param obj 対象オブジェクト
   * @return マップ。対象がマップでない場合は空マップ
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
   * オブジェクトを文字列のリストとして安全に取得するメソッド<br>
   * 単一列の関連を{@code columns: user_id}のように書けるよう、スカラー値も1要素のリストとして受け付ける
   *
   * @param obj 対象オブジェクト
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
   * オブジェクトを文字列として安全に取得するメソッド
   *
   * @param obj 対象オブジェクト
   * @return 文字列。nullの場合は空文字
   */
  private String asString(Object obj) {
    return obj == null ? "" : String.valueOf(obj);
  }
}
