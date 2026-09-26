package com.export_table_definition.domain;

/**
 * 利用者が設定・入力・実行環境を見直せば解消する誤りを表す例外クラス<br>
 * 設定の誤り、サイドカーYAMLの構文誤り、DBに接続できない等、このツールの不具合ではない失敗を表す。
 * 検知した箇所で、何を直せばよいかが分かるメッセージを付けて投げる（元の例外がある場合は原因として渡す）。<br>
 * エントリーポイントの境界は、この例外かそれ以外（想定外の失敗）かだけで表示・ログ出力を切り替えるため、
 * 呼び出し側は個々の失敗の種類を知らなくてよい。設定・アプリケーション・インフラのいずれの層からも投げられるよう、 依存の向きの最も内側であるドメイン層に置く
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class UserCorrectableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * コンストラクタ
   *
   * @param message 誤りの内容と、何を直せばよいか（利用者向けのメッセージ）
   */
  public UserCorrectableException(String message) {
    super(message);
  }

  /**
   * コンストラクタ
   *
   * @param message 誤りの内容と、何を直せばよいか（利用者向けのメッセージ）
   * @param cause 誤りの原因となった例外
   */
  public UserCorrectableException(String message, Throwable cause) {
    super(message, cause);
  }
}
