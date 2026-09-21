package com.export_table_definition.domain.model;

import java.nio.file.Path;
import java.util.List;

import com.export_table_definition.domain.model.collection.Columns;
import com.export_table_definition.domain.model.collection.Constraints;
import com.export_table_definition.domain.model.collection.ForeignKeys;
import com.export_table_definition.domain.model.collection.Indexes;
import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.ColumnEntity;
import com.export_table_definition.domain.model.entity.ConstraintEntity;
import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.entity.IndexEntity;
import com.export_table_definition.domain.model.entity.TableEntity;

/**
 * テーブル定義出力に必要な情報をまとめたレコード
 * 
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record TableDefinitionContent(BaseInfoEntity baseInfo, TableEntity table, List<ColumnEntity> columns,
        List<IndexEntity> indexes, List<ConstraintEntity> constraints, List<ForeignKeyEntity> foreignKeys,
        List<ForeignKeyEntity> incomingForeignKeys, Path outputBaseDir) {

    /**
     * テーブル定義出力に必要な情報をまとめたレコードを組み立てる
     *
     * @param baseInfo
     * @param table
     * @param columns
     * @param indexes
     * @param constraints
     * @param foreignkeys
     * @param baseDir
     * @return TableDefinitionContent
     */
    public static TableDefinitionContent assemble(BaseInfoEntity baseInfo, TableEntity table, Columns columns,
            Indexes indexes, Constraints constraints, ForeignKeys foreignkeys, Path baseDir) {
        return new TableDefinitionContent(baseInfo, table, columns.of(table), indexes.of(table), constraints.of(table),
                foreignkeys.of(table), foreignkeys.incomingOf(table), baseDir);
    }
}
