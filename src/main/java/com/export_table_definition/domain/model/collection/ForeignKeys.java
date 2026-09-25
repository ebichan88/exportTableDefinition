package com.export_table_definition.domain.model.collection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.value.TableKey;
/**
 * 外部キー情報の集合を扱うクラス<br>
 * DBに実在する外部キー制約と、サイドカーYAML由来の論理リレーションを同一の集合として保持する。
 * ER図はテーブル間の関連をまとめて描く必要があるため両者を区別せず扱い、
 * テーブル定義書のセクションは{@link #physicalOf(TableEntity)}／{@link #logicalOf(TableEntity)}で
 * 由来ごとに取り出して掲載する
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public final class ForeignKeys extends AbstractEntities<ForeignKeyEntity> {
    /** 被参照側（自テーブルを参照している外部キー）をテーブルキーでインデックス化したマップ */
    private final Map<TableKey, List<ForeignKeyEntity>> incomingByKey;

    private ForeignKeys(Map<TableKey, List<ForeignKeyEntity>> byKey, Map<TableKey, List<ForeignKeyEntity>> incomingByKey) {
        super(byKey);
        this.incomingByKey = Collections.unmodifiableMap(incomingByKey);
    }

    public static ForeignKeys of(List<ForeignKeyEntity> list) {
        final Map<TableKey, List<ForeignKeyEntity>> byKey = index(list, c -> TableKey.of(c.schemaName(), c.tableName()));
        // 自己参照（自テーブルを参照する外部キー）は、被参照側の一覧に含めると
        // 外部キー情報セクションと重複して表示されてしまうため除外する
        final List<ForeignKeyEntity> incomingCandidates = list.stream()
                .filter(fk -> !fk.getSchemaTableName().equals(fk.getReferenceSchemaTableName())).toList();
        final Map<TableKey, List<ForeignKeyEntity>> incomingByKey = index(incomingCandidates,
                c -> TableKey.of(c.referenceSchemaName(), c.referenceTableName()));
        return new ForeignKeys(byKey, incomingByKey);
    }

    /**
     * 指定されたテーブルを参照している外部キー（被参照側）のリストを取得するメソッド
     *
     * @param table テーブルエンティティ
     * @return 当該テーブルを参照している外部キーのリスト。存在しない場合は空のリストを返す
     */
    public List<ForeignKeyEntity> incomingOf(TableEntity table) {
        return incomingByKey.getOrDefault(TableKey.of(table), List.of());
    }

    /**
     * 指定されたテーブルが持つ、DBに実在する外部キー制約のリストを取得するメソッド<br>
     * テーブル定義書の「外部キー情報」セクションには制約として実在するものだけを掲載する
     *
     * @param table テーブルエンティティ
     * @return 当該テーブルの物理外部キーのリスト。存在しない場合は空のリストを返す
     */
    public List<ForeignKeyEntity> physicalOf(TableEntity table) {
        return of(table).stream().filter(fk -> !fk.isLogical()).toList();
    }

    /**
     * 指定されたテーブルが持つ、サイドカーYAML由来の論理リレーションのリストを取得するメソッド<br>
     * テーブル定義書では「論理リレーション情報」セクションとして物理外部キーとは別に掲載する
     *
     * @param table テーブルエンティティ
     * @return 当該テーブルの論理リレーションのリスト。存在しない場合は空のリストを返す
     */
    public List<ForeignKeyEntity> logicalOf(TableEntity table) {
        return of(table).stream().filter(ForeignKeyEntity::isLogical).toList();
    }

    /**
     * 外部キーをスキーマ単位にグループ化するメソッド<br>
     * スキーマ別ER図では「そのスキーマのテーブルが関与する外部キー」がまとめて必要になるため、
     * スキーマ跨ぎの外部キーは参照元・参照先の双方のスキーマに登録する。<br>
     * 返却するマップはフィールドとして保持せず呼び出しのたびに構築する。
     * 保持すると外部キー1件あたり最大2つの参照を処理全体にわたって抱え続けることになるため、
     * 利用側のスコープを抜けた時点で解放されるようにしている
     *
     * @return スキーマ名をキー、当該スキーマが関与する外部キーのリストを値とするマップ
     */
    public Map<String, List<ForeignKeyEntity>> groupBySchema() {
        final Map<String, List<ForeignKeyEntity>> bySchema = new LinkedHashMap<>();
        byKey.values().forEach(foreignKeyList -> foreignKeyList.forEach(fk -> {
            bySchema.computeIfAbsent(fk.schemaName(), k -> new ArrayList<>()).add(fk);
            if (!fk.schemaName().equals(fk.referenceSchemaName())) {
                bySchema.computeIfAbsent(fk.referenceSchemaName(), k -> new ArrayList<>()).add(fk);
            }
        }));
        return bySchema;
    }

    /**
     * スキーマを跨ぐ外部キーのリストを取得するメソッド<br>
     * 全件を平坦化したリストを作らず、該当する部分集合のみを構築する
     *
     * @return 参照元と参照先のスキーマが異なる外部キーのリスト
     */
    public List<ForeignKeyEntity> crossSchema() {
        return byKey.values().stream().flatMap(List::stream)
                .filter(fk -> !fk.schemaName().equals(fk.referenceSchemaName())).toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected TableKey extractKey(ForeignKeyEntity e) {
        return TableKey.of(e.schemaName(), e.tableName());
    }
}
