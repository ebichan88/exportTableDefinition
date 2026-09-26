package com.export_table_definition;

import com.export_table_definition.application.CheckDiffRequest;
import com.export_table_definition.application.ExportRequest;
import com.export_table_definition.application.TargetSelection;
import com.export_table_definition.config.InvalidConfigurationException;
import com.export_table_definition.config.PropertyLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 実行時設定ファイル（{@code conf/ExportTableDefinition.properties}）の読み込み・検証を行うクラス<br>
 * 設定項目の仕様（キー・既定値・値の形式。READMEの「ExportTableDefinition.propertiesの記載内容」）をこのクラスに集める。
 *
 * <ul>
 *   <li>キーの省略と値が空は、同じ「未指定」として扱い既定値を用いる
 *   <li>未知のキー（キー名の書き誤り等）・整数として解釈できない値・出力対象の条件として解釈できない値は誤りとする
 *   <li>見つかった誤りは、1件ずつではなくまとめて{@link InvalidConfigurationException}で報告する
 * </ul>
 *
 * 設定値は生の文字列のまま後続へ渡さず、ここで前後の空白を除去し、型へ変換・検証する （出力対象の条件の変換・検証は{@link TargetSelection#of}に委ねる）
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
final class ExportTableDefinitionProperties {

  /** 設定ファイル名（{@code conf/}配下。拡張子を除く） */
  private static final String FILE_NAME = "ExportTableDefinition";

  private static final String SCHEMA = "schema";
  private static final String TABLE = "table";
  private static final String OUTPUT_PATH = "outputPath";
  private static final String CHUNK_SIZE = "chunkSize";
  private static final String ER_DIAGRAM_MAX_NODES = "erDiagramMaxNodes";
  private static final String OUTPUT_OBJECTS = "outputObjects";

  /** サイドカーYAMLのパスのキー（既存の設定ファイルとの互換のため、コード上の呼び方sidecarPathではなくannotationPathとする） */
  private static final String ANNOTATION_PATH = "annotationPath";

  /** 設定ファイルに書けるキー（READMEの記載順） */
  private static final List<String> KEYS =
      List.of(
          SCHEMA,
          TABLE,
          OUTPUT_PATH,
          CHUNK_SIZE,
          ER_DIAGRAM_MAX_NODES,
          OUTPUT_OBJECTS,
          ANNOTATION_PATH);

  /** chunkSize未指定時の既定値（1スキーマあたりこの件数ごとに詳細情報を取得・出力する） */
  private static final int DEFAULT_CHUNK_SIZE = 3000;

  /** erDiagramMaxNodes未指定時の既定値（スキーマ別ER図1枚に描画するテーブル数の上限） */
  private static final int DEFAULT_ER_DIAGRAM_MAX_NODES = 80;

  /** カンマ区切りで複数指定する項目の区切り文字 */
  private static final String LIST_SEPARATOR = ",";

  private final TargetSelection targetSelection;
  private final String outputPath;
  private final int chunkSize;
  private final int erDiagramMaxNodes;

  private ExportTableDefinitionProperties(
      TargetSelection targetSelection, String outputPath, int chunkSize, int erDiagramMaxNodes) {
    this.targetSelection = targetSelection;
    this.outputPath = outputPath;
    this.chunkSize = chunkSize;
    this.erDiagramMaxNodes = erDiagramMaxNodes;
  }

  /**
   * {@code conf/ExportTableDefinition.properties}を読み込み、検証するメソッド
   *
   * @return 検証済みの設定
   * @throws InvalidConfigurationException {@code conf}ディレクトリ・設定ファイルが見つからない場合や、設定に誤りがある場合
   */
  static ExportTableDefinitionProperties load() {
    final ResourceBundle bundle = PropertyLoader.getResourceBundle(FILE_NAME);
    return of(
        bundle.keySet().stream().collect(Collectors.toMap(Function.identity(), bundle::getString)));
  }

  /**
   * 設定ファイルのキーと値から、検証済みの設定を生成するメソッド
   *
   * @param values 設定ファイルのキーと値
   * @return 検証済みの設定
   * @throws InvalidConfigurationException 設定に誤りがある場合（見つかった誤りをすべて示す）
   */
  static ExportTableDefinitionProperties of(Map<String, String> values) {
    final List<String> errors = new ArrayList<>();
    final List<String> unknownKeys =
        values.keySet().stream().filter(key -> !KEYS.contains(key)).sorted().toList();
    if (!unknownKeys.isEmpty()) {
      errors.add(
          "Unknown key: "
              + String.join(", ", unknownKeys)
              + " (available keys: "
              + String.join(", ", KEYS)
              + ")");
    }
    final int chunkSize = parseInt(values, CHUNK_SIZE, DEFAULT_CHUNK_SIZE, errors);
    final int erDiagramMaxNodes =
        parseInt(values, ER_DIAGRAM_MAX_NODES, DEFAULT_ER_DIAGRAM_MAX_NODES, errors);
    TargetSelection targetSelection = null;
    IllegalArgumentException targetSelectionError = null;
    try {
      targetSelection =
          TargetSelection.of(
              list(values, SCHEMA),
              list(values, TABLE),
              list(values, OUTPUT_OBJECTS),
              text(values, ANNOTATION_PATH));
    } catch (IllegalArgumentException e) {
      // 出力対象の条件の誤りは、1行に1件ずつ示される
      targetSelectionError = e;
      errors.addAll(e.getMessage().lines().toList());
    }
    if (!errors.isEmpty()) {
      throw new InvalidConfigurationException(
          "Invalid configuration in "
              + FILE_NAME
              + ".properties."
              + errors.stream()
                  .map(error -> System.lineSeparator() + "  - " + error)
                  .collect(Collectors.joining()),
          targetSelectionError);
    }
    return new ExportTableDefinitionProperties(
        targetSelection, text(values, OUTPUT_PATH), chunkSize, erDiagramMaxNodes);
  }

  /**
   * テーブル定義出力（通常実行）の入力へ変換するメソッド
   *
   * @param rmDist trueの場合、書き込みを開始する前に出力先ディレクトリを再帰的に削除する（{@code --rm-dist}）
   * @return テーブル定義出力の入力
   */
  ExportRequest toExportRequest(boolean rmDist) {
    return new ExportRequest(targetSelection, outputPath, chunkSize, erDiagramMaxNodes, rmDist);
  }

  /**
   * DB vs ドキュメントの差分検知（{@code --check}モード）の入力へ変換するメソッド<br>
   * 通常実行と異なり、Markdownの描画・ER図の生成を行わないため{@code erDiagramMaxNodes}は含めない
   *
   * @return 差分検知の入力
   */
  CheckDiffRequest toCheckDiffRequest() {
    return new CheckDiffRequest(targetSelection, outputPath, chunkSize);
  }

  /**
   * 設定値を、前後の空白を除いた文字列として取得するメソッド
   *
   * @param values 設定ファイルのキーと値
   * @param key キー
   * @return 設定値。未指定（キーの省略・空）の場合は空文字
   */
  private static String text(Map<String, String> values, String key) {
    return values.getOrDefault(key, "").strip();
  }

  /**
   * カンマ区切りの設定値を、各要素の前後の空白を除いたリストとして取得するメソッド<br>
   * {@code schema=public, sample}のようにカンマの後に空白を入れた場合に、{@code " sample"}が
   * 別の名前として扱われ、対象から黙って外れてしまうことを防ぐため、各要素の前後の空白を除去する
   *
   * @param values 設定ファイルのキーと値
   * @param key キー
   * @return 空要素を除いたリスト。未指定の場合は空リスト
   */
  private static List<String> list(Map<String, String> values, String key) {
    return Arrays.stream(text(values, key).split(LIST_SEPARATOR))
        .map(String::strip)
        .filter(element -> !element.isEmpty())
        .toList();
  }

  /**
   * 設定値を整数として取得するメソッド<br>
   * 整数として解釈できない場合は、既定値へ黙って置き換えず、誤りとして記録する
   *
   * @param values 設定ファイルのキーと値
   * @param key キー
   * @param defaultValue 未指定の場合の既定値
   * @param errors 誤りの記録先
   * @return 設定値。未指定の場合と、解釈できない場合（誤りを記録済み）は既定値
   */
  private static int parseInt(
      Map<String, String> values, String key, int defaultValue, List<String> errors) {
    final String value = text(values, key);
    if (value.isEmpty()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      errors.add(key + " must be an integer: " + value);
      return defaultValue;
    }
  }
}
