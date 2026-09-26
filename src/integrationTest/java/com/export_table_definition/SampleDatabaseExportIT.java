package com.export_table_definition;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.application.CheckDiffRequest;
import com.export_table_definition.application.ExportRequest;
import com.export_table_definition.config.module.DatabaseDependentModule;
import com.export_table_definition.config.module.ExportTableDefinitionModule;
import com.export_table_definition.infrastructure.db.DatabaseTypeDetector;
import com.export_table_definition.presentation.ExportTableDefinitionController;
import com.export_table_definition.presentation.dto.DiffCheckResultDto;
import com.export_table_definition.testsupport.SampleDatabase;
import com.google.inject.Guice;
import com.google.inject.util.Modules;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * サンプルDBに対してツール全体（設定の解釈→DIコンテナ→SQL→ドキュメント・スナップショットの出力）を通す結合テスト<br>
 * 出力を、コミット済みのベースライン（{@code docs/sample/postgres/output}。verifyスキルの手順で出力したもの）と
 * ファイル単位で完全一致するか比べる。SQL・DTOからの変換・テンプレートのどこが変わっても検知できる。
 * 出力仕様を意図して変えた場合は、verifyスキルの手順でベースラインを出力し直してコミットする
 */
class SampleDatabaseExportIT {

  /** ベースライン（verifyスキルの手順で出力したもの） */
  private static final Path BASELINE = Path.of("docs/sample/postgres/output");

  /** verifyスキルの手順で指定しているサイドカーYAML */
  private static final String ANNOTATION_PATH = "docs/sample/postgres/annotations.sample.yml";

  /** 基本情報の作成日（実行日。ベースラインを出力した日と異なるため、比較の前に置き換える） */
  private static final String CREATED_DATE_PATTERN = "\\|\\d{4}/\\d{2}/\\d{2}\\|";

  private static final String CREATED_DATE_PLACEHOLDER = "|<作成日>|";

  /** 生成日を固定する時計 */
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2000-01-01T00:00:00Z"), ZoneOffset.UTC);

  @ParameterizedTest(name = "chunkSize={0}")
  @ValueSource(strings = {"", "1"})
  @DisplayName("通常実行: 出力がベースラインと一致する（詳細情報の分割取得の単位を変えても出力は変わらない）")
  void testExportMatchesBaseline(String chunkSize, @TempDir Path outputDir) {
    final ExportRequest request =
        properties(outputDir, Map.of("chunkSize", chunkSize)).toExportRequest(false);

    controller().execute(request);

    assertEquals(listFiles(BASELINE), listFiles(outputDir), "出力されるファイルの一覧");
    for (final Path file : listFiles(BASELINE)) {
      assertEquals(read(BASELINE.resolve(file)), read(outputDir.resolve(file)), "ファイルの内容: " + file);
    }
  }

  @Test
  @DisplayName("差分検知（--check）: DBとベースラインのスナップショットに差分が無い")
  void testCheckFindsNoDifferenceFromBaseline() {
    final CheckDiffRequest request = properties(BASELINE, Map.of()).toCheckDiffRequest();

    final DiffCheckResultDto result = controller().checkDiff(request);

    assertFalse(result.hasDifference(), result.getResultMessage());
  }

  /**
   * エントリーポイントと同じ手順（DB種別に依存しない部品のコンテナに、接続後に子のコンテナを足す）でコントローラーを取得する
   *
   * @return コントローラー
   */
  private static ExportTableDefinitionController controller() {
    final SqlSessionFactory sqlSessionFactory = SampleDatabase.sqlSessionFactory();
    return Guice.createInjector(
            Modules.override(new ExportTableDefinitionModule())
                .with(binder -> binder.bind(Clock.class).toInstance(FIXED_CLOCK)))
        .createChildInjector(
            new DatabaseDependentModule(
                DatabaseTypeDetector.detect(sqlSessionFactory), sqlSessionFactory))
        .getInstance(ExportTableDefinitionController.class);
  }

  /**
   * verifyスキルの手順と同じ設定（{@code conf/ExportTableDefinition.properties}）を組み立てる
   *
   * @param outputPath 出力先
   * @param overrides 上書きする設定
   * @return 検証済みの設定
   */
  private static ExportTableDefinitionProperties properties(
      Path outputPath, Map<String, String> overrides) {
    final Map<String, String> values = new HashMap<>();
    values.put("schema", "sample");
    values.put("outputPath", outputPath.toString());
    values.put("annotationPath", ANNOTATION_PATH);
    values.putAll(overrides);
    return ExportTableDefinitionProperties.of(values);
  }

  private static List<Path> listFiles(Path directory) {
    try (Stream<Path> paths = Files.walk(directory)) {
      return paths.filter(Files::isRegularFile).map(directory::relativize).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String read(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8)
          .replaceAll(CREATED_DATE_PATTERN, CREATED_DATE_PLACEHOLDER);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
