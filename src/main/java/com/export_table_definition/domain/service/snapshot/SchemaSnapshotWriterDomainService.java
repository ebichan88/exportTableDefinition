package com.export_table_definition.domain.service.snapshot;

import com.export_table_definition.domain.model.TableDefinitionContent;
import com.export_table_definition.domain.model.entity.FunctionEntity;
import com.export_table_definition.domain.model.entity.SequenceEntity;
import com.export_table_definition.domain.model.entity.TypeEntity;
import com.export_table_definition.domain.model.snapshot.DatabaseSnapshot;
import com.export_table_definition.domain.model.snapshot.FunctionSnapshot;
import com.export_table_definition.domain.model.snapshot.SequenceSnapshot;
import com.export_table_definition.domain.model.snapshot.SnapshotKind;
import com.export_table_definition.domain.model.snapshot.TableSnapshot;
import com.export_table_definition.domain.model.snapshot.TypeSnapshot;
import com.export_table_definition.domain.repository.FileRepository;
import com.export_table_definition.domain.service.path.OutputPathResolver;
import com.export_table_definition.domain.service.path.OutputRoot;
import com.google.inject.Inject;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * スキーマのスナップショット（DBから取得したスキーマ情報を構造化した中間表現）を書き込むクラス<br>
 * Markdownのテーブル定義書と同じ取得結果から、機械可読なJSON Lines形式（1行1オブジェクト）で出力する。 テーブルはチャンク単位の取得・破棄（{@code
 * chunkSize}）のメモリプロファイルを変えないよう、 1テーブル書き終えるごとにスキーマ単位のファイルへ追記する
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class SchemaSnapshotWriterDomainService {

  /** JSON Linesの仕様に合わせ、OSに依らず行区切りはLFとする */
  private static final String LINE_SEPARATOR = "\n";

  private static final Logger logger =
      LogManager.getLogger(SchemaSnapshotWriterDomainService.class);
  private final FileRepository fileRepository;
  private final OutputPathResolver outputPathResolver;
  private final SnapshotSerializer serializer;

  /**
   * コンストラクタ
   *
   * @param fileRepository ファイルリポジトリ
   * @param outputPathResolver 出力パス解決クラス
   * @param serializer スナップショットのJSON変換を行うクラス
   */
  @Inject
  public SchemaSnapshotWriterDomainService(
      FileRepository fileRepository,
      OutputPathResolver outputPathResolver,
      SnapshotSerializer serializer) {
    this.fileRepository = fileRepository;
    this.outputPathResolver = outputPathResolver;
    this.serializer = serializer;
  }

  /**
   * DB全体の情報（{@code database.json}）の書き込み処理を行うメソッド
   *
   * @param outputRoot 出力先ベースディレクトリとデータベース基本情報
   */
  public void writeDatabase(OutputRoot outputRoot) {
    write(
        outputPathResolver.resolveSnapshotDatabaseFile(outputRoot),
        List.of(DatabaseSnapshot.of(outputRoot.baseInfo())));
  }

  /**
   * シーケンスの書き込み処理を行うメソッド<br>
   * スキーマごとに1ファイルへ出力する。対象が存在しないスキーマのファイルは出力しない
   *
   * @param sequences シーケンス情報リスト
   * @param outputRoot 出力先ベースディレクトリとデータベース基本情報
   */
  public void writeSequences(List<SequenceEntity> sequences, OutputRoot outputRoot) {
    writeBySchema(
        sequences,
        SequenceEntity::schemaName,
        SequenceSnapshot::of,
        SnapshotKind.SEQUENCE,
        outputRoot);
  }

  /**
   * ユーザー定義型の書き込み処理を行うメソッド<br>
   * スキーマごとに1ファイルへ出力する。対象が存在しないスキーマのファイルは出力しない
   *
   * @param types ユーザー定義型情報リスト
   * @param outputRoot 出力先ベースディレクトリとデータベース基本情報
   */
  public void writeTypes(List<TypeEntity> types, OutputRoot outputRoot) {
    writeBySchema(types, TypeEntity::schemaName, TypeSnapshot::of, SnapshotKind.TYPE, outputRoot);
  }

  /**
   * 指定スキーマに属する関数・プロシージャの書き込み処理を行うメソッド<br>
   * 定義本体が大きくなり得るため、スキーマ単位で取得したものを受け取って1ファイルへ出力する
   *
   * @param schemaName スキーマ名
   * @param functions 当該スキーマの関数・プロシージャ情報（定義本体を含む）のリスト
   * @param outputRoot 出力先ベースディレクトリとデータベース基本情報
   */
  public void writeFunctions(
      String schemaName, List<FunctionEntity> functions, OutputRoot outputRoot) {
    write(
        outputPathResolver.resolveSnapshotFile(outputRoot, schemaName, SnapshotKind.FUNCTION),
        functions.stream().map(FunctionSnapshot::of).toList());
  }

  /**
   * 指定スキーマのテーブル情報の追記先ファイルを、空の状態で作成するメソッド<br>
   * 前回実行時のファイルが残っている場合でも、その内容へ追記してしまわないよう上書きで空にする。 当該スキーマの{@link #appendTable}より前に1回だけ呼び出すこと
   *
   * @param schemaName スキーマ名
   * @param outputRoot 出力先ベースディレクトリとデータベース基本情報
   */
  public void initTableFile(String schemaName, OutputRoot outputRoot) {
    write(
        outputPathResolver.resolveSnapshotFile(outputRoot, schemaName, SnapshotKind.TABLE),
        List.of());
  }

  /**
   * 1テーブル分の情報を、スキーマ単位のファイルへ1行追記するメソッド<br>
   * 追記先は{@link #initTableFile}で作成済みであること
   *
   * @param content 1テーブル分の定義書出力に必要な情報
   * @param outputBaseDir 出力先のベースディレクトリパス
   */
  public void appendTable(TableDefinitionContent content, Path outputBaseDir) {
    final OutputRoot outputRoot = new OutputRoot(outputBaseDir, content.baseInfo());
    final Path filePath =
        outputPathResolver.resolveSnapshotFile(
            outputRoot, content.table().schemaName(), SnapshotKind.TABLE);
    fileRepository.appendFile(filePath, List.of(toLine(TableSnapshot.of(content))));
  }

  /**
   * エンティティをスキーマごとにまとめ、スキーマ単位のファイルへ出力する共通メソッド
   *
   * @param <T> エンティティの型
   * @param entities エンティティのリスト
   * @param schemaNameGetter エンティティからスキーマ名を取得する関数
   * @param toSnapshot エンティティをスナップショットへ変換する関数
   * @param kind オブジェクトの種別
   * @param outputRoot 出力先ベースディレクトリとデータベース基本情報
   */
  private <T> void writeBySchema(
      List<T> entities,
      Function<T, String> schemaNameGetter,
      Function<T, ?> toSnapshot,
      SnapshotKind kind,
      OutputRoot outputRoot) {
    entities.stream()
        .collect(Collectors.groupingBy(schemaNameGetter, LinkedHashMap::new, Collectors.toList()))
        .forEach(
            (schemaName, entitiesInSchema) ->
                write(
                    outputPathResolver.resolveSnapshotFile(outputRoot, schemaName, kind),
                    entitiesInSchema.stream().map(toSnapshot).toList()));
  }

  /**
   * スナップショットを1行1オブジェクトとしてファイルへ上書きで書き込むメソッド
   *
   * @param filePath 書き込み先のファイルパス
   * @param snapshots スナップショットのリスト
   */
  private void write(Path filePath, List<?> snapshots) {
    fileRepository.createDirectory(filePath.getParent());
    fileRepository.writeFile(filePath, snapshots.stream().map(this::toLine).toList());
    logger.debug("exportSchemaSnapshot complete. [filePath={}]", filePath);
  }

  /**
   * スナップショットを、行区切り付きの1行のJSON文字列へ変換するメソッド
   *
   * @param snapshot スナップショット
   * @return 行区切り付きの1行のJSON文字列
   */
  private String toLine(Object snapshot) {
    return serializer.serialize(snapshot) + LINE_SEPARATOR;
  }
}
