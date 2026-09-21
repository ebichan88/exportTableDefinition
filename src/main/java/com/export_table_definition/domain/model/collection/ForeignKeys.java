package com.export_table_definition.domain.model.collection;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.value.TableKey;
/**
 * 外部キー情報の集合を扱うクラス
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
     * {@inheritDoc}
     */
    @Override
    protected TableKey extractKey(ForeignKeyEntity e) {
        return TableKey.of(e.schemaName(), e.tableName());
    }
}
