package com.export_table_definition.presentation;

import com.export_table_definition.domain.UserCorrectableException;
import com.export_table_definition.presentation.type.ExitStatus;
import com.export_table_definition.presentation.type.ProcessResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 処理の失敗（例外）を1箇所で捕捉し、利用者への報告と終了状態へ変換する共通クラス<br>
 * エントリーポイントが、設定の読み込み・DBへの接続・DIコンテナの組み立てを含む処理全体をこのクラス経由で実行する。
 * コントローラー・ユースケース等の途中の層では例外を捕捉せず、ここまで伝える。失敗は次の2つに分けて報告する。
 *
 * <ul>
 *   <li>利用者が直せる誤り（{@link UserCorrectableException}）: 何を直せばよいかをメッセージで伝える。
 *       不具合ではないため、ログにもスタックトレースは残さない
 *   <li>想定外の失敗（それ以外）: 原因の調査に要るスタックトレースをログへ残し、画面にはログの場所を案内する
 * </ul>
 *
 * いずれの場合も、例外の連鎖（原因）のうち表示に含まれていない情報を持つものを{@code [cause]}として併記する
 * （例外を包んだ箇所で、DBが返したエラー等の原因のメッセージが失われないようにするため）
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public final class FailureHandler {

  private static final Logger logger = LogManager.getLogger(FailureHandler.class);

  /** ログファイルの場所（log4j2.xmlのlogfileと揃える）。想定外の失敗の報告で、詳細の確認先として案内する */
  private static final String LOG_FILE = "./var/log/exportTableDefinition.log";

  private final String failureSummary;
  private final Consumer<String> console;

  /**
   * コンストラクタ
   *
   * @param failureSummary 失敗した場合に報告の先頭に示す要旨（どの処理が失敗したか）
   * @param console 報告の出力先（通常は標準出力）
   */
  public FailureHandler(String failureSummary, Consumer<String> console) {
    this.failureSummary = failureSummary;
    this.console = console;
  }

  /**
   * 処理を実行し、失敗した場合は報告して{@link ExitStatus#FAILURE}へ変換するメソッド<br>
   * JVMのエラー（{@link Error}）も含めてすべて捕捉する。捕捉しないとJVMが終了コード1で終了し、 {@code --check}の「差分あり」と区別できなくなるため
   *
   * @param command 実行する処理（成功した場合の終了状態を返す）
   * @return 処理が返した終了状態。失敗した場合は{@link ExitStatus#FAILURE}
   */
  public ExitStatus run(Supplier<ExitStatus> command) {
    try {
      return command.get();
    } catch (UserCorrectableException e) {
      final List<String> report = report(e);
      logger.error("{}", String.join(System.lineSeparator(), report));
      console.accept(ProcessResult.FAIL.formatMessage(String.join(System.lineSeparator(), report)));
      return ExitStatus.FAILURE;
    } catch (Throwable e) {
      final List<String> report = report(e);
      logger.error(String.join(System.lineSeparator(), report), e);
      report.add(
          " An unexpected error occurred. See the log file for details. [logFile="
              + LOG_FILE
              + "]");
      console.accept(ProcessResult.FAIL.formatMessage(String.join(System.lineSeparator(), report)));
      return ExitStatus.FAILURE;
    }
  }

  /**
   * 失敗の報告（要旨・例外のメッセージ・原因）を行単位で組み立てるメソッド
   *
   * @param failure 発生した例外
   * @return 報告の行のリスト（呼び出し側で行を追加できるよう変更可能なリストで返す）
   */
  private List<String> report(Throwable failure) {
    final List<String> lines = new ArrayList<>();
    lines.add(failureSummary);
    final String message = describe(failure);
    lines.add(" [errmsg]:" + message);
    causesToShow(failure, message).forEach(cause -> lines.add(" [cause]:" + cause));
    return lines;
  }

  /**
   * 報告に併記する原因を求めるメソッド<br>
   * 例外の連鎖のうち、既に表示する内容に含まれるものと、より深い原因のメッセージを繰り返しているだけのもの
   * （MyBatisの例外等）を除き、浅い順に返す。例えば「SQLの失敗」を包んだ例外では、DBが返したエラーだけが残る
   *
   * @param failure 発生した例外
   * @param message 表示する例外のメッセージ
   * @return 併記する原因のリスト（浅い順）
   */
  private static List<Throwable> causesToShow(Throwable failure, String message) {
    final List<Throwable> causes = causeChain(failure);
    final List<String> shownMessages = new ArrayList<>(List.of(message));
    final List<Throwable> result = new ArrayList<>();
    for (int i = 0; i < causes.size(); i++) {
      final String causeMessage = describe(causes.get(i));
      final boolean alreadyShown =
          shownMessages.stream().anyMatch(shown -> shown.contains(causeMessage));
      final boolean repeatsDeeperCause =
          causes.subList(i + 1, causes.size()).stream()
              .anyMatch(deeper -> causeMessage.contains(describe(deeper)));
      if (!alreadyShown && !repeatsDeeperCause) {
        shownMessages.add(causeMessage);
        result.add(causes.get(i));
      }
    }
    return result;
  }

  /**
   * 例外の原因を浅い順に列挙するメソッド（原因が循環している場合も終了する）
   *
   * @param failure 発生した例外
   * @return 原因のリスト（{@code failure}自身は含まない）
   */
  private static List<Throwable> causeChain(Throwable failure) {
    final Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    visited.add(failure);
    final List<Throwable> causes = new ArrayList<>();
    for (Throwable cause = failure.getCause();
        cause != null && visited.add(cause);
        cause = cause.getCause()) {
      causes.add(cause);
    }
    return causes;
  }

  /**
   * 例外を表示用の文字列にするメソッド
   *
   * @param throwable 例外
   * @return 例外のメッセージ。メッセージを持たない場合は例外のクラス名
   */
  private static String describe(Throwable throwable) {
    final String message = throwable.getMessage();
    return message == null || message.isBlank() ? throwable.getClass().getName() : message;
  }
}
