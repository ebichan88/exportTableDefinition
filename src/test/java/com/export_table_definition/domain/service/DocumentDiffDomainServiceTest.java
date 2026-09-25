package com.export_table_definition.domain.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.export_table_definition.domain.model.DiffResult;
import com.export_table_definition.infrastructure.file.repository.TableDefinitionFileRepository;

/**
 * DocumentDiffDomainService の生成ドキュメントとコミット済みドキュメントの比較に関するテスト
 */
public class DocumentDiffDomainServiceTest {

    private final DocumentDiffDomainService service = new DocumentDiffDomainService(new TableDefinitionFileRepository());

    private void write(Path dir, String relativePath, String content) throws IOException {
        Path file = dir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("compare: 生成側にのみ存在するファイルはonlyInGeneratedとして検出される（コミット漏れの想定）")
    void testOnlyInGenerated(@TempDir Path generatedDir, @TempDir Path committedDir) throws IOException {
        write(generatedDir, "new.md", "content");

        DiffResult result = service.compare(generatedDir, committedDir);

        assertTrue(result.hasDifference());
        assertEquals(List.of(Path.of("new.md")), result.onlyInGenerated());
        assertTrue(result.onlyInCommitted().isEmpty());
        assertTrue(result.contentDiffer().isEmpty());
    }

    @Test
    @DisplayName("compare: コミット側にのみ存在するファイルはonlyInCommittedとして検出される（削除漏れの想定）")
    void testOnlyInCommitted(@TempDir Path generatedDir, @TempDir Path committedDir) throws IOException {
        write(committedDir, "stale.md", "content");

        DiffResult result = service.compare(generatedDir, committedDir);

        assertTrue(result.hasDifference());
        assertEquals(List.of(Path.of("stale.md")), result.onlyInCommitted());
        assertTrue(result.onlyInGenerated().isEmpty());
        assertTrue(result.contentDiffer().isEmpty());
    }

    @Test
    @DisplayName("compare: 同名ファイルの内容が異なる場合はcontentDifferとして検出される")
    void testContentDiffer(@TempDir Path generatedDir, @TempDir Path committedDir) throws IOException {
        write(generatedDir, "t1.md", "new content");
        write(committedDir, "t1.md", "old content");

        DiffResult result = service.compare(generatedDir, committedDir);

        assertTrue(result.hasDifference());
        assertEquals(List.of(Path.of("t1.md")), result.contentDiffer());
        assertTrue(result.onlyInGenerated().isEmpty());
        assertTrue(result.onlyInCommitted().isEmpty());
    }

    @Test
    @DisplayName("compare: サブディレクトリを含め全ファイルの内容が一致する場合は差分なしと判定する")
    void testNoDifferenceWhenContentsMatch(@TempDir Path generatedDir, @TempDir Path committedDir) throws IOException {
        write(generatedDir, "testdb/public/table/t1.md", "same content");
        write(committedDir, "testdb/public/table/t1.md", "same content");

        DiffResult result = service.compare(generatedDir, committedDir);

        assertFalse(result.hasDifference());
    }

    @Test
    @DisplayName("compare: コミット側ディレクトリが未作成（初回実行）の場合は生成物すべてがonlyInGeneratedとなる")
    void testCommittedDirectoryDoesNotExist(@TempDir Path generatedDir) throws IOException {
        write(generatedDir, "t1.md", "content");
        Path notExistCommittedDir = generatedDir.resolveSibling(generatedDir.getFileName() + "-not-exist");

        DiffResult result = service.compare(generatedDir, notExistCommittedDir);

        assertTrue(result.hasDifference());
        assertEquals(List.of(Path.of("t1.md")), result.onlyInGenerated());
    }
}
