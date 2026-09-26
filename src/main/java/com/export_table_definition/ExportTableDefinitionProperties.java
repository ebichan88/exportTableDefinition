package com.export_table_definition;

import com.export_table_definition.application.CheckDiffRequest;
import com.export_table_definition.application.ExportRequest;
import com.export_table_definition.application.TargetSelection;
import com.export_table_definition.config.InvalidConfigurationException;
import com.export_table_definition.config.PropertyLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 実行時設定ファイル（{@code conf/ExportTableDefinition.properties}）の設定項目の仕様を持ち、設定値を検証・変換するクラス<br>
 * 設定項目の仕様（キー・既定値・値の形式。READMEの「ExportTableDefinition.propertiesの記載内容」）をこのクラスに集める。
 * ファイルの探索・読み込みは{@link PropertyLoader}に委ね、このクラスは読み込んだキーと値を、CLI引数・環境変数による上書き値 （{@link
 * CliArguments}が解決する）で上書きしたうえで仕様に照らして検証し、型へ変換する。
 *
 * <ul>
 *   <li>キーの省略と値が空は、同じ「未指定」として扱い既定値を用いる
 *   <li>CLI引数・環境変数で上書きした値も、設定ファイルに書いた値と同じ仕様で検証する
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

  /**
   * 設定ファイルに書けるキー（READMEの記載順）<br>
   * CLI引数・環境変数による上書きも、このキーから名前を導く（{@link CliArguments}）ため、キーを追加すれば上書きにも自動で対応する
   */
  static final List<String> KEYS =
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
   * {@code conf/ExportTableDefinition.properties}を読み込み、CLI引数・環境変数による上書き値で上書きして検証するメソッド<br>
   * 上書きする値をすべて指定する場合でも、設定ファイル自体は必要とする（実行するディレクトリを誤った場合に、既定の出力先 （{@code ./output}）へ黙って出力しないよう、{@code
   * conf}ディレクトリ・設定ファイルが見つからないことを誤りとして報告するため）
   *
   * @param overrides 設定ファイルのキーをキー、上書きする値とその指定元を値とするマップ（未指定のキーは含まない）
   * @return 検証済みの設定
   * @throws InvalidConfigurationException {@code conf}ディレクトリ・設定ファイルが見つからない場合や、設定に誤りがある場合
   */
  static ExportTableDefinitionProperties load(Map<String, SettingOverride> overrides) {
    return of(PropertyLoader.load(FILE_NAME), overrides);
  }

  /**
   * 設定ファイルのキーと値から、検証済みの設定を生成するメソッド
   *
   * @param values 設定ファイルのキーと値
   * @return 検証済みの設定
   * @throws InvalidConfigurationException 設定に誤りがある場合（見つかった誤りをすべて示す）
   */
  static ExportTableDefinitionProperties of(Map<String, String> values) {
    return of(values, Map.of());
  }

  /**
   * 設定ファイルのキーと値を上書き値で上書きし、検証済みの設定を生成するメソッド<br>
   * 誤りの報告には、どの値を上書きしたか（指定元のCLI引数名・環境変数名）を添える。誤った値が設定ファイルではなく
   * CLI引数・環境変数から来ている場合に、設定ファイルだけを見直して原因が見つからない、とならないようにするため
   *
   * @param fileValues 設定ファイルのキーと値
   * @param overrides 設定ファイルのキーをキー、上書きする値とその指定元を値とするマップ（未指定のキーは含まない）
   * @return 検証済みの設定
   * @throws InvalidConfigurationException 設定に誤りがある場合（見つかった誤りをすべて示す）
   */
  static ExportTableDefinitionProperties of(
      Map<String, String> fileValues, Map<String, SettingOverride> overrides) {
    final Map<String, String> values = new HashMap<>(fileValues);
    overrides.forEach((key, override) -> values.put(key, override.value()));
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
              + ".properties"
              + overriddenBy(overrides)
              + "."
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
   * 誤りの報告に添える、上書きした値の指定元の説明を組み立てるメソッド
   *
   * @param overrides 上書き値
   * @return 上書きした値がある場合は{@code " (overridden by --output-path, ETD_TABLE)"}の形式の文字列、無い場合は空文字
   */
  private static String overriddenBy(Map<String, SettingOverride> overrides) {
    if (overrides.isEmpty()) {
      return "";
    }
    return overrides.values().stream()
        .map(SettingOverride::source)
        .collect(Collectors.joining(", ", " (overridden by ", ")"));
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

  /**
   * CLI引数・環境変数による、設定値1項目分の上書き
   *
   * @param value 上書きする値
   * @param source 値の指定元（CLI引数名（例: {@code --output-path}）または環境変数名（例: {@code ETD_OUTPUT_PATH}））
   */
  record SettingOverride(String value, String source) {}
}
