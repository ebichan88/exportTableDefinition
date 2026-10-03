package com.export_table_definition.mcp.snapshot;

import com.export_table_definition.mcp.UserCorrectableException;
import com.export_table_definition.mcp.catalog.ColumnEntry;
import com.export_table_definition.mcp.catalog.DatabaseEntry;
import com.export_table_definition.mcp.catalog.ObjectKey;
import com.export_table_definition.mcp.catalog.RelationEntry;
import com.export_table_definition.mcp.catalog.SchemaCatalog;
import com.export_table_definition.mcp.catalog.TableEntry;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * スナップショットのディレクトリを読み込み、{@link SchemaCatalog}を組み立てるクラス<br>
 * 配置はcliの出力と同じく{@code {DB名}/database.json}・{@code {DB名}/{スキーマ名}/tables.jsonl}。
 * 項目の追加に追従できるよう未知の項目は無視し、形式を互換性なく変えた場合は{@code formatVersion}で検知する
 */
public final class SnapshotDirectoryReader {

  /** 読み込める形式のバージョンの上限（cliの{@code DatabaseSnapshot.FORMAT_VERSION}） */
  static final int SUPPORTED_FORMAT_VERSION = 1;

  private static final String DATABASE_FILE_NAME = "database.json";
  private static final String TABLE_FILE_NAME = "tables.jsonl";

  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * スナップショットのディレクトリを読み込むメソッド
   *
   * @param snapshotDirectory cliの{@code outputPath}配下の{@code snapshot}ディレクトリ
   * @throws UserCorrectableException ディレクトリが無い・スナップショットが1つも無い・対応していない形式のバージョン・JSONとして読めない行がある場合
   */
  public SchemaCatalog read(Path snapshotDirectory) {
    if (!Files.isDirectory(snapshotDirectory)) {
      throw new UserCorrectableException(
          "スナップショットのディレクトリが見つかりません。--snapshotにはcliの出力先（outputPath）配下のsnapshotディレクトリを指定してください。 [path="
              + snapshotDirectory
              + "]");
    }
    final List<Path> databaseDirectories =
        subdirectories(snapshotDirectory).stream()
            .filter(directory -> Files.isRegularFile(directory.resolve(DATABASE_FILE_NAME)))
            .toList();
    if (databaseDirectories.isEmpty()) {
      throw new UserCorrectableException(
          "スナップショットが見つかりません（{DB名}/"
              + DATABASE_FILE_NAME
              + "がありません）。--snapshotにはcliの出力先（outputPath）配下のsnapshotディレクトリを指定してください。 [path="
              + snapshotDirectory
              + "]");
    }
    final List<DatabaseEntry> databases = new ArrayList<>();
    final List<TableEntry> tables = new ArrayList<>();
    for (final Path databaseDirectory : databaseDirectories) {
      final DatabaseLine databaseLine = readDatabase(databaseDirectory.resolve(DATABASE_FILE_NAME));
      databases.add(new DatabaseEntry(databaseLine.name(), databaseLine.dbms()));
      final String database = databaseLine.name();
      for (final Path schemaDirectory : subdirectories(databaseDirectory)) {
        final Path tableFile = schemaDirectory.resolve(TABLE_FILE_NAME);
        if (Files.isRegularFile(tableFile)) {
          tables.addAll(readTables(database, tableFile));
        }
      }
    }
    return SchemaCatalog.of(databases, tables);
  }

  private DatabaseLine readDatabase(Path file) {
    final DatabaseLine database = parse(file, 1, readString(file), DatabaseLine.class);
    if (database.formatVersion() == null || database.name() == null) {
      throw new UserCorrectableException(
          "スナップショットのDB情報にformatVersion・nameがありません。cliでスナップショットを出力し直してください。 [file=" + file + "]");
    }
    if (database.formatVersion() > SUPPORTED_FORMAT_VERSION) {
      throw new UserCorrectableException(
          "このMCPサーバーが対応していない新しい形式のスナップショットです（formatVersion="
              + database.formatVersion()
              + "、対応しているのは"
              + SUPPORTED_FORMAT_VERSION
              + "まで）。MCPサーバーをスナップショットを出力したcliと同じ版に更新してください。 [file="
              + file
              + "]");
    }
    return database;
  }

  private List<TableEntry> readTables(String database, Path file) {
    final List<String> lines = readLines(file);
    final List<TableEntry> tables = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      final String line = lines.get(i);
      if (line.isBlank()) {
        continue;
      }
      final TableLine table = parse(file, i + 1, line, TableLine.class);
      if (table.schema() == null || table.name() == null) {
        throw new UserCorrectableException(
            "スナップショットの行にschema・nameがありません。cliで出力し直してください。 [file="
                + file
                + ", line="
                + (i + 1)
                + "]");
      }
      tables.add(table.toEntry(database, line));
    }
    return tables;
  }

  /** JSONを読み込む。読めない場合は、利用者が該当箇所を探せるようファイル名と行番号を示す */
  private <T> T parse(Path file, int lineNumber, String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (JsonProcessingException e) {
      throw new UserCorrectableException(
          "スナップショットをJSONとして読み込めません。マージの衝突等でファイルが壊れていないか確認し、必要ならcliで出力し直してください。 [file="
              + file
              + ", line="
              + lineNumber
              + "]",
          e);
    }
  }

  private static List<Path> subdirectories(Path directory) {
    try (Stream<Path> children = Files.list(directory)) {
      return children.filter(Files::isDirectory).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String readString(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static List<String> readLines(Path file) {
    try {
      return Files.readAllLines(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** {@code database.json}のうち、読み込みに使う項目 */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record DatabaseLine(Integer formatVersion, String name, String dbms) {}

  /** {@code tables.jsonl}の1行のうち、検索・関連のたどり・逆引きに使う項目 */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record TableLine(
      String schema,
      String name,
      String logicalName,
      String type,
      String description,
      String remarks,
      List<ColumnLine> columns,
      List<RelationLine> foreignKeys,
      List<RelationLine> logicalRelations) {

    TableEntry toEntry(String database, String json) {
      return new TableEntry(
          new ObjectKey(database, schema, name),
          logicalName,
          type,
          description,
          remarks,
          columns == null ? null : columns.stream().map(ColumnLine::toEntry).toList(),
          relations(foreignKeys),
          relations(logicalRelations),
          json);
    }

    private static List<RelationEntry> relations(List<RelationLine> lines) {
      return lines == null ? null : lines.stream().map(RelationLine::toEntry).toList();
    }
  }

  /** カラムのうち、検索・逆引きに使う項目 */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record ColumnLine(
      String name,
      String logicalName,
      String type,
      boolean primaryKey,
      boolean notNull,
      String defaultValue,
      String remarks) {

    ColumnEntry toEntry() {
      return new ColumnEntry(name, logicalName, type, primaryKey, notNull, defaultValue, remarks);
    }
  }

  /** 外部キー・論理リレーション */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record RelationLine(
      String name,
      List<String> columns,
      String referenceSchema,
      String referenceTable,
      List<String> referenceColumns,
      String cardinality) {

    RelationEntry toEntry() {
      return new RelationEntry(
          name, columns, referenceSchema, referenceTable, referenceColumns, cardinality);
    }
  }
}
