package com.export_table_definition.infrastructure.file.repository;

import com.export_table_definition.domain.repository.FileRepository;
import com.export_table_definition.infrastructure.file.TableDefinitionBufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * ファイル操作に関するリポジトリ実装クラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class TableDefinitionFileRepository implements FileRepository {

  /** {@inheritDoc} */
  @Override
  public void writeFile(Path filePath, List<String> contents) {
    try (final var bw = new TableDefinitionBufferedWriter(filePath)) {
      contents.stream().forEach(content -> bw.write(content));
    }
  }

  /** {@inheritDoc} */
  @Override
  public void appendFile(Path filePath, List<String> contents) {
    try (final var bw = new TableDefinitionBufferedWriter(filePath, StandardCharsets.UTF_8, true)) {
      contents.stream().forEach(content -> bw.write(content));
    }
  }

  /** {@inheritDoc} */
  @Override
  public void createDirectory(Path filePath) {
    try {
      Files.createDirectories(filePath);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public List<Path> listFiles(Path directory) {
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    try (var stream = Files.walk(directory)) {
      return stream.filter(Files::isRegularFile).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public List<String> readFile(Path filePath) {
    try {
      return Files.readAllLines(filePath, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public Path createTempDirectory(String prefix) {
    try {
      return Files.createTempDirectory(prefix);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void deleteDirectory(Path directory) {
    if (!Files.exists(directory)) {
      return;
    }
    try (var stream = Files.walk(directory)) {
      stream
          .sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.delete(path);
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
