package com.export_table_definition.domain.service.writer;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.export_table_definition.domain.model.TableDefinitionContent;
import com.export_table_definition.domain.model.annotation.TableAnnotation;
import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.ColumnEntity;
import com.export_table_definition.domain.model.entity.ConstraintEntity;
import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.entity.IndexEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.entity.TriggerEntity;
import com.export_table_definition.domain.repository.FileRepository;
import com.export_table_definition.infrastructure.path.DefaultOutputPathResolver;

/**
 * TableDefinitionWriterDomainService のテーブル一覧・テーブル定義書き込みに関するテスト
 */
public class TableDefinitionWriterDomainServiceTest {

    private static final Path OUT = Path.of("output");

    /** 書き込み内容・ディレクトリ作成呼び出しをメモリ上に収集するFileRepositoryのスタブ */
    private static class InMemoryFileRepository implements FileRepository {
        private final Map<Path, String> files = new LinkedHashMap<>();
        private final List<Path> createdDirectories = new ArrayList<>();

        @Override
        public void writeFile(Path filePath, List<String> contents) {
            files.put(filePath, String.join("", contents));
        }

        @Override
        public void createDirectory(Path filePath) {
            createdDirectories.add(filePath);
        }
    }

    private InMemoryFileRepository fileRepository;
    private TableDefinitionWriterDomainService writer;

    private BaseInfoEntity baseInfo() {
        return new BaseInfoEntity("testdb", "| pg | testdb | 2026-09-24 |");
    }

    private TableEntity table(String physical) {
        return new TableEntity("testdb", "public", "受注", physical, "table",
                "|1|public|受注|" + physical + "|table|[link](x)||", "|public|受注|" + physical + "|table|", "");
    }

    @BeforeEach
    void setUp() {
        fileRepository = new InMemoryFileRepository();
        writer = new TableDefinitionWriterDomainService(fileRepository, new DefaultOutputPathResolver(),
                new PagedSectionWriter(fileRepository));
    }

    @Test
    @DisplayName("writeTableDefinitionList: ヘッダー・基本情報・関連ドキュメント・テーブル一覧行が出力される")
    void testWriteTableDefinitionListWritesAllSections() {
        Map<String, String> relatedDocuments = new LinkedHashMap<>();
        relatedDocuments.put("ER図一覧", "erDiagram");

        writer.writeTableDefinitionList(List.of(table("orders")), baseInfo(), OUT, relatedDocuments);

        Path file = OUT.resolve("tableList_testdb.md");
        assertTrue(fileRepository.files.containsKey(file));
        String content = fileRepository.files.get(file);
        assertTrue(content.contains("# テーブル一覧（DB名：testdb）"));
        assertTrue(content.contains("| pg | testdb | 2026-09-24 |"));
        assertTrue(content.contains("## 関連ドキュメント"));
        assertTrue(content.contains("[ER図一覧](./erDiagramList_testdb.md)"));
        assertTrue(content.contains("|public|受注|orders|table|"));
        assertTrue(fileRepository.createdDirectories.contains(OUT));
    }

    @Test
    @DisplayName("writeTableDefinitionList: 関連ドキュメントが空の場合はセクション自体が出力されない")
    void testWriteTableDefinitionListOmitsRelatedDocumentsWhenEmpty() {
        writer.writeTableDefinitionList(List.of(table("orders")), baseInfo(), OUT, Map.of());

        String content = fileRepository.files.get(OUT.resolve("tableList_testdb.md"));
        assertFalse(content.contains("## 関連ドキュメント"));
    }

    @Test
    @DisplayName("writeTableDefinitionList: 行数が多い場合はページ分割され、本体ページにはリンクのみ掲載される")
    void testWriteTableDefinitionListSplitsWhenExceedingMaxPageSize() {
        List<TableEntity> tables = IntStream.rangeClosed(1, 3001).mapToObj(i -> table("t" + i)).toList();

        writer.writeTableDefinitionList(tables, baseInfo(), OUT, Map.of());

        assertTrue(fileRepository.files.containsKey(OUT.resolve("tableList_testdb_1.md")));
        assertTrue(fileRepository.files.containsKey(OUT.resolve("tableList_testdb_2.md")));
        String main = fileRepository.files.get(OUT.resolve("tableList_testdb.md"));
        assertFalse(main.contains("|t1|"), "本体ページには行そのものは含まれない");
        assertTrue(main.contains("./tableList_testdb_1.md"));
        assertTrue(main.contains("./tableList_testdb_2.md"));
    }

    @Test
    @DisplayName("writeTableDefinition: 解決されたパスに、カラム・インデックス・制約・外部キー・トリガー・ER図の全セクションを出力する")
    void testWriteTableDefinitionWritesAllSections() {
        TableEntity table = table("orders");
        var column = new ColumnEntity("public", "orders", "|1|受注ID|order_id|int|Y|N||", "order_id", "int", "○");
        var index = new IndexEntity("public", "orders", "|1|idx_orders_1|order_id|");
        var constraint = new ConstraintEntity("public", "orders", "|1|pk_orders|PRIMARY KEY|(order_id)|");
        var outgoingFk = new ForeignKeyEntity("public", "orders", "|1|fk_orders_customer|customer_id|customers|id|",
                "fk_orders_customer", "public", "customers");
        var incomingFk = new ForeignKeyEntity("public", "items", "unused", "fk_items_orders", "public", "orders");
        var trigger = new TriggerEntity("public", "orders", "unused", "|1|trg_orders|BEFORE|INSERT|ROW|...|");

        var annotation = new TableAnnotation("受注を管理するテーブル", "個人情報を含む", Map.of("order_id", "受注の主キー"));
        var content = new TableDefinitionContent(baseInfo(), table, List.of(column), List.of(index),
                List.of(constraint), List.of(outgoingFk), List.of(incomingFk), List.of(trigger), annotation, OUT);

        writer.writeTableDefinition(content);

        Path expectedFile = OUT.resolve("testdb").resolve("public").resolve("table").resolve("orders.md");
        assertTrue(fileRepository.createdDirectories.contains(OUT.resolve("testdb").resolve("public").resolve("table")));
        assertTrue(fileRepository.files.containsKey(expectedFile));
        String fileContent = fileRepository.files.get(expectedFile);

        assertTrue(fileContent.contains("# orders（受注）"));
        assertTrue(fileContent.contains("## カラム情報"));
        assertTrue(fileContent.contains("order_id"));
        assertTrue(fileContent.contains("## インデックス情報"));
        assertTrue(fileContent.contains("idx_orders_1"));
        assertTrue(fileContent.contains("## 制約情報"));
        assertTrue(fileContent.contains("pk_orders"));
        assertTrue(fileContent.contains("## 外部キー情報"));
        assertTrue(fileContent.contains("fk_orders_customer"));
        assertTrue(fileContent.contains("## トリガー情報"));
        assertTrue(fileContent.contains("trg_orders"));
        assertTrue(fileContent.contains("## ER図"));
        assertTrue(fileContent.contains("```mermaid"));
        assertTrue(fileContent.contains("public_customers ||--o{ public_orders : \"fk_orders_customer\""));
        assertTrue(fileContent.contains("public_orders ||--o{ public_items : \"fk_items_orders\""));
        assertTrue(fileContent.contains("[テーブル一覧へ](../../../tableList_testdb.md)"));
        // サイドカー由来の付帯情報（テーブル説明・テーブル備考・カラム備考）がマージされる
        assertTrue(fileContent.contains("受注を管理するテーブル"));
        assertTrue(fileContent.contains("|public|受注|orders|table|個人情報を含む|"));
        assertTrue(fileContent.contains("|1|受注ID|order_id|int|Y|N||受注の主キー|"));
    }
}
