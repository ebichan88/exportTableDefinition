package com.export_table_definition.infrastructure.file.repository;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.export_table_definition.domain.model.annotation.Annotations;
import com.export_table_definition.domain.model.annotation.TableAnnotation;
import com.export_table_definition.domain.model.value.TableKey;
import com.export_table_definition.domain.repository.AnnotationRepository;

/**
 * サイドカーYAMLから手動付帯情報を読み込むリポジトリ実装クラス<br>
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

    /**
     * {@inheritDoc}
     */
    @Override
    public Annotations load(String annotationPath) {
        if (StringUtils.isBlank(annotationPath)) {
            return Annotations.empty();
        }
        final Path path = Path.of(annotationPath.trim());
        if (!Files.exists(path)) {
            logger.warn("Annotation file not found. Skipping merge of manual annotations. [annotationPath={}]", path);
            return Annotations.empty();
        }
        try (final Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            final Object root = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
            final Annotations annotations = parse(root, path);
            logger.info("Loaded manual annotations. [annotationPath={}, tableCount={}]", path,
                    annotations.tableKeys().size());
            return annotations;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the annotation file. [annotationPath=" + path + "]", e);
        }
    }

    /**
     * SnakeYAMLで読み込んだ生のオブジェクトを{@link Annotations}へ変換するメソッド<br>
     * 想定外の構造の箇所は警告ログを出して読み飛ばし、可能な範囲で読み込みを継続する
     *
     * @param root YAMLのルートオブジェクト
     * @param path 読み込み元のパス（ログ用）
     * @return 変換した付帯情報
     */
    private Annotations parse(Object root, Path path) {
        final Map<String, Object> tables = asMap(mapValue(root, KEY_TABLES));
        if (tables.isEmpty()) {
            logger.warn("Annotation file has no 'tables' entries. [annotationPath={}]", path);
            return Annotations.empty();
        }
        final Map<TableKey, TableAnnotation> byKey = new LinkedHashMap<>();
        tables.forEach((rawKey, value) -> {
            final TableKey tableKey = toTableKey(rawKey, path);
            if (tableKey == null) {
                return;
            }
            byKey.put(tableKey, toTableAnnotation(asMap(value)));
        });
        return Annotations.of(byKey);
    }

    /**
     * 「スキーマ.テーブル」形式のキー文字列を{@link TableKey}へ変換するメソッド
     *
     * @param rawKey キー文字列
     * @param path   読み込み元のパス（ログ用）
     * @return 変換したテーブルキー。形式が不正な場合はnull
     */
    private TableKey toTableKey(String rawKey, Path path) {
        if (StringUtils.isBlank(rawKey) || !rawKey.contains(".")) {
            logger.warn("Ignoring annotation key not in 'schema.table' format. [key={}, annotationPath={}]", rawKey,
                    path);
            return null;
        }
        final int separatorIndex = rawKey.indexOf('.');
        final String schema = rawKey.substring(0, separatorIndex).trim();
        final String table = rawKey.substring(separatorIndex + 1).trim();
        if (schema.isEmpty() || table.isEmpty()) {
            logger.warn("Ignoring annotation key not in 'schema.table' format. [key={}, annotationPath={}]", rawKey,
                    path);
            return null;
        }
        return TableKey.of(schema, table);
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
        columns.forEach((columnName, remark) -> {
            if (StringUtils.isNotBlank(columnName)) {
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
        map.forEach((k, v) -> {
            if (k != null) {
                result.put(String.valueOf(k), v);
            }
        });
        return result;
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
