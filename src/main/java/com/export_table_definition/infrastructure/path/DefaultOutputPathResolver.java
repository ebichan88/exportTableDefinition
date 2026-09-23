package com.export_table_definition.infrastructure.path;

import java.nio.file.Path;

import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.service.path.OutputPathResolver;

/**
 * 出力パス解決のデフォルト実装
 * 
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class DefaultOutputPathResolver implements OutputPathResolver {

    private static final String TABLE_LIST_FILENAME_PATTERN = "tableList_%s.md";
    private static final String TABLE_LIST_PAGED_FILENAME_PATTERN = "tableList_%s_%d.md";
    private static final String OBJECT_LIST_FILENAME_PATTERN = "%sList_%s.md";
    private static final String OBJECT_LIST_PAGED_FILENAME_PATTERN = "%sList_%s_%d.md";
    private static final String ER_DIAGRAM_FILENAME_PATTERN = "erDiagram_%s_%s.md";
    private static final String ER_DIAGRAM_PAGED_FILENAME_PATTERN = "erDiagram_%s_%s_%d.md";
    private static final String ER_DIAGRAM_GROUP_FILENAME_PATTERN = "erDiagram_%s_%s_group%d.md";
    private static final String ER_DIAGRAM_GROUP_PAGED_FILENAME_PATTERN = "erDiagram_%s_%s_group%d_%d.md";

    /**
     * {@inheritDoc}
     */
    @Override
    public Path resolveTableDefinitionDirectory(BaseInfoEntity baseInfo, TableEntity table, Path baseOutputDir) {
        return baseOutputDir.resolve(baseInfo.dbName()).resolve(table.schemaName()).resolve(table.tableType());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Path resolveTableDefinitionFile(BaseInfoEntity baseInfo, TableEntity table, Path baseOutputDir) {
        return resolveTableDefinitionDirectory(baseInfo, table, baseOutputDir)
                .resolve(table.physicalTableName() + ".md");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Path resolveTableListFile(BaseInfoEntity baseInfo, Path baseOutputDir) {
        return baseOutputDir.resolve(String.format(TABLE_LIST_FILENAME_PATTERN, baseInfo.dbName()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Path resolveTableListFile(BaseInfoEntity baseInfo, Path baseOutputDir, int pageIndex) {
        return baseOutputDir.resolve(String.format(TABLE_LIST_PAGED_FILENAME_PATTERN, baseInfo.dbName(), pageIndex));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Path resolveObjectListFile(BaseInfoEntity baseInfo, Path baseOutputDir, String prefix) {
        return baseOutputDir.resolve(String.format(OBJECT_LIST_FILENAME_PATTERN, prefix, baseInfo.dbName()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Path resolveObjectListFile(BaseInfoEntity baseInfo, Path baseOutputDir, String prefix, int pageIndex) {
        return baseOutputDir
                .resolve(String.format(OBJECT_LIST_PAGED_FILENAME_PATTERN, prefix, baseInfo.dbName(), pageIndex));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Path resolveErDiagramFile(BaseInfoEntity baseInfo, Path baseOutputDir, String schemaName) {
        return baseOutputDir.resolve(String.format(ER_DIAGRAM_FILENAME_PATTERN, baseInfo.dbName(), schemaName));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Path resolveErDiagramFile(BaseInfoEntity baseInfo, Path baseOutputDir, String schemaName, int pageIndex) {
        return baseOutputDir
                .resolve(String.format(ER_DIAGRAM_PAGED_FILENAME_PATTERN, baseInfo.dbName(), schemaName, pageIndex));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Path resolveErDiagramGroupFile(BaseInfoEntity baseInfo, Path baseOutputDir, String schemaName,
            int groupNo) {
        return baseOutputDir
                .resolve(String.format(ER_DIAGRAM_GROUP_FILENAME_PATTERN, baseInfo.dbName(), schemaName, groupNo));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Path resolveErDiagramGroupFile(BaseInfoEntity baseInfo, Path baseOutputDir, String schemaName, int groupNo,
            int pageIndex) {
        return baseOutputDir.resolve(String.format(ER_DIAGRAM_GROUP_PAGED_FILENAME_PATTERN, baseInfo.dbName(),
                schemaName, groupNo, pageIndex));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Path resolveSchemaObjectDirectory(BaseInfoEntity baseInfo, Path baseOutputDir, String schemaName,
            String kind) {
        return baseOutputDir.resolve(baseInfo.dbName()).resolve(schemaName).resolve(kind);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Path resolveSchemaObjectFile(BaseInfoEntity baseInfo, Path baseOutputDir, String schemaName, String kind,
            String name) {
        return resolveSchemaObjectDirectory(baseInfo, baseOutputDir, schemaName, kind).resolve(name + ".md");
    }

}