package com.dbxray.domain.service.path;

/**
 * 参考情報（{@code insights}。観点等、スナップショットの事実とは別にAIへ渡す情報）の配置を一元的に定めるクラス<br>
 * {@link SnapshotLocations}と対になるが、出力ベースディレクトリ直下では{@code snapshot}の**兄弟**に置く （{@code
 * --check}の比較対象はスナップショットのディレクトリ配下に限られるため、参考情報をその外に置くことで 比較対象から構造的に外れる）。配置は{@code
 * insights/{DB名}/viewpoints.json}（DB名は{@link PathSegments}で置き換える）
 */
public final class InsightLocations {

  /** 参考情報全体を置くディレクトリ名（出力ベースディレクトリ直下。{@code snapshot}の兄弟） */
  private static final String INSIGHTS_DIRECTORY = "insights";

  /** 観点の参考情報のファイル名 */
  private static final String VIEWPOINTS_FILE_NAME = "viewpoints.json";

  private static final String PATH_SEPARATOR = "/";

  private InsightLocations() {}

  /**
   * 参考情報全体を置くディレクトリの、出力ベースディレクトリからの相対パスを取得するメソッド
   *
   * @return {@code insights}
   */
  public static String insightsDirectory() {
    return INSIGHTS_DIRECTORY;
  }

  /**
   * 観点の参考情報のファイルの、参考情報のディレクトリからの相対パスを取得するメソッド
   *
   * @return {@code {DB名}/viewpoints.json}
   */
  public static String viewpointsFile(String dbName) {
    return PathSegments.encode(dbName) + PATH_SEPARATOR + VIEWPOINTS_FILE_NAME;
  }
}
