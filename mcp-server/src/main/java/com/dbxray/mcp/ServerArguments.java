package com.dbxray.mcp;

import java.nio.file.Path;

/**
 * MCPサーバーの起動引数
 *
 * @param snapshotDirectory cliの出力先（output.path）配下の{@code snapshot}ディレクトリ
 */
public record ServerArguments(Path snapshotDirectory) {

  private static final String SNAPSHOT_OPTION = "--snapshot=";

  /** 起動引数の書式（誤りを伝えるメッセージに添える） */
  static final String USAGE = "使い方: java -jar dbxray-mcp.jar --snapshot=<cliの出力先のsnapshotディレクトリ>";

  /**
   * 起動引数を解釈するメソッド
   *
   * @throws UserCorrectableException {@code --snapshot}が無い・空・複数ある場合、未知の引数がある場合
   */
  public static ServerArguments parse(String... args) {
    Path snapshotDirectory = null;
    for (final String arg : args) {
      if (!arg.startsWith(SNAPSHOT_OPTION)) {
        throw new UserCorrectableException("未知の引数です: " + arg + "。" + USAGE);
      }
      if (snapshotDirectory != null) {
        throw new UserCorrectableException("--snapshotが複数指定されています。" + USAGE);
      }
      final String value = arg.substring(SNAPSHOT_OPTION.length()).strip();
      if (value.isEmpty()) {
        throw new UserCorrectableException("--snapshotの値が空です。" + USAGE);
      }
      snapshotDirectory = Path.of(value);
    }
    if (snapshotDirectory == null) {
      throw new UserCorrectableException("--snapshotを指定してください。" + USAGE);
    }
    return new ServerArguments(snapshotDirectory);
  }
}
