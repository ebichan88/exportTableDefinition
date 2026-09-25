package com.export_table_definition.infrastructure.file.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TableDefinitionFileRepository の listFiles／readFile に関するテスト
 */
public class TableDefinitionFileRepositoryTest {

    private final TableDefinitionFileRepository repository = new TableDefinitionFileRepository();

    @Test
    @DisplayName("listFiles: ディレクトリ配下のファイルをサブディレクトリを含め再帰的に列挙する")
    void testListFilesRecursively(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("sub"));
        Files.writeString(dir.resolve("a.md"), "a", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("sub/b.md"), "b", StandardCharsets.UTF_8);

        List<Path> files = repository.listFiles(dir);

        assertEquals(List.of(dir.resolve("a.md"), dir.resolve("sub/b.md")), files);
    }

    @Test
    @DisplayName("listFiles: ディレクトリが存在しない場合は空リストを返す")
    void testListFilesReturnsEmptyWhenDirectoryDoesNotExist(@TempDir Path dir) {
        assertEquals(List.of(), repository.listFiles(dir.resolve("not-exist")));
    }

    @Test
    @DisplayName("readFile: ファイルの内容を行リストとして読み込む")
    void testReadFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("t1.md");
        Files.writeString(file, "line1\nline2\n", StandardCharsets.UTF_8);

        assertEquals(List.of("line1", "line2"), repository.readFile(file));
    }

    @Test
    @DisplayName("writeFile -> readFile: writeFileで書き込んだ内容がreadFileでそのまま読み込める")
    void testWriteThenReadRoundTrip(@TempDir Path dir) {
        Path file = dir.resolve("t2.md");
        repository.writeFile(file, List.of("line1\n", "line2\n"));

        assertEquals(List.of("line1", "line2"), repository.readFile(file));
    }
}
