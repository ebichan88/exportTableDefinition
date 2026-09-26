package com.export_table_definition.presentation.type;

/**
 * 処理の終了状態と、プロセスの終了コードを対応付ける列挙型<br>
 * {@code diff}コマンドと同じく、差分の有無と処理の失敗を終了コードで区別できるようにする （CIのジョブで「差分あり」と「比較自体の失敗」を見分けられるようにするため）
 */
public enum ExitStatus {
  /** 処理成功（通常実行の完了、{@code --check}で差分なし） */
  SUCCESS(0),
  /** {@code --check}で差分が見つかった */
  DIFFERENCE_FOUND(1),
  /** 処理失敗（利用者が直せる誤り・想定外の失敗のいずれも） */
  FAILURE(2);

  private final int code;

  /**
   * コンストラクタ
   *
   * @param code プロセスの終了コード
   */
  ExitStatus(int code) {
    this.code = code;
  }

  /**
   * プロセスの終了コードを返却する。
   *
   * @return プロセスの終了コード
   */
  public int code() {
    return code;
  }
}
