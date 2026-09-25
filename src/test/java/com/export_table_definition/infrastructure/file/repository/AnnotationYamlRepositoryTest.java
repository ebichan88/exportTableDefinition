package com.export_table_definition.infrastructure.file.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.export_table_definition.domain.model.annotation.Annotations;
import com.export_table_definition.domain.model.annotation.TableAnnotation;
import com.export_table_definition.domain.model.entity.TableEntity;

/**
 * AnnotationYamlRepository のサイドカーYAML読み込みに関するテスト
 */
public class AnnotationYamlRepositoryTest {

    private final AnnotationYamlRepository repository = new AnnotationYamlRepository();

    private TableEntity table(String schema, String physical) {
        return new TableEntity("testdb", schema, "", physical, "table", "", "", "");
    }

    private Path writeYaml(Path dir, String content) throws IOException {
        Path file = dir.resolve("annotations.yml");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    @DisplayName("load: 説明（複数行）・テーブル備考・カラム備考を読み込む")
    void testLoadFull(@TempDir Path dir) throws IOException {
        Path file = writeYaml(dir, """
                tables:
                  public.users:
                    description: |
                      ユーザー基本情報。
                      認証と紐づく。
                    remarks: 個人情報を含む
                    columns:
                      email: ログインID兼用
                      status: 0=無効 1=有効
                """);

        Annotations annotations = repository.load(file.toString());
        TableAnnotation users = annotations.of(table("public", "users"));

        assertEquals("ユーザー基本情報。\n認証と紐づく。\n", users.description().replace("\r\n", "\n"));
        assertEquals("個人情報を含む", users.remarks());
        assertEquals("ログインID兼用", users.columnRemark("email"));
        assertEquals("0=無効 1=有効", users.columnRemark("status"));
    }

    @Test
    @DisplayName("load: パスが空・null・空白の場合は空のAnnotationsを返す")
    void testLoadBlankPath() {
        assertTrue(repository.load(null).isEmpty());
        assertTrue(repository.load("").isEmpty());
        assertTrue(repository.load("   ").isEmpty());
    }

    @Test
    @DisplayName("load: ファイルが存在しない場合は空のAnnotationsを返す")
    void testLoadMissingFile(@TempDir Path dir) {
        assertTrue(repository.load(dir.resolve("not_exist.yml").toString()).isEmpty());
    }

    @Test
    @DisplayName("load: 'schema.table'形式でないキーは読み飛ばす")
    void testLoadIgnoresInvalidKey(@TempDir Path dir) throws IOException {
        Path file = writeYaml(dir, """
                tables:
                  invalidkey:
                    remarks: 無視される
                  public.orders:
                    remarks: 有効
                """);

        Annotations annotations = repository.load(file.toString());

        assertEquals(1, annotations.tableKeys().size());
        assertEquals("有効", annotations.of(table("public", "orders")).remarks());
    }

    @Test
    @DisplayName("load: tablesキーが存在しない場合は空のAnnotationsを返す")
    void testLoadNoTablesKey(@TempDir Path dir) throws IOException {
        Path file = writeYaml(dir, "other: value\n");
        assertTrue(repository.load(file.toString()).isEmpty());
    }

    @Test
    @DisplayName("load: スキーマ名にドットが無いテーブル名も、最初のドットで分割して解釈する")
    void testLoadSplitsOnFirstDot(@TempDir Path dir) throws IOException {
        Path file = writeYaml(dir, """
                tables:
                  public.my.table:
                    remarks: ドット入りテーブル名
                """);

        Annotations annotations = repository.load(file.toString());

        assertEquals("ドット入りテーブル名", annotations.of(table("public", "my.table")).remarks());
    }
}
