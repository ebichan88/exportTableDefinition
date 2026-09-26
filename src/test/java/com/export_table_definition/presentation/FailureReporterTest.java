package com.export_table_definition.presentation;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.config.InvalidConfigurationException;
import com.export_table_definition.domain.UserCorrectableException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** FailureReporter の失敗の報告（利用者が直せる誤りと想定外の失敗の区別、原因の併記）に関するテスト */
public class FailureReporterTest {

  private static final String SUMMARY = "Failed to output table definition document.";

  private final List<String> console = new ArrayList<>();
  private final FailureReporter reporter = new FailureReporter(SUMMARY, console::add);

  /**
   * 報告して、画面へ出した報告を返す
   *
   * @param failure 報告する例外
   * @return 画面へ出した報告（1回だけ出力されること）
   */
  private String report(Throwable failure) {
    reporter.report(failure);
    assertEquals(1, console.size());
    return console.get(0);
  }

  @Test
  @DisplayName("利用者が直せる誤りは、FAILと要旨・メッセージを報告し、ログの案内は付けない")
  void testReportsUserCorrectableFailureWithoutLogGuidance() {
    final String report =
        report(new UserCorrectableException("Refusing to run --rm-dist. [outputBaseDir=/]"));

    assertTrue(report.startsWith("[result]:FAIL"));
    assertTrue(report.contains(SUMMARY));
    assertTrue(report.contains(" [errmsg]:Refusing to run --rm-dist. [outputBaseDir=/]"));
    assertFalse(report.contains("[cause]"));
    assertFalse(report.contains("See the log file"));
  }

  @Test
  @DisplayName("設定の誤り（InvalidConfigurationException）も利用者が直せる誤りとして報告する")
  void testReportsInvalidConfigurationAsUserCorrectable() {
    final String report =
        report(new InvalidConfigurationException("Required property is not set."));

    assertTrue(report.contains(" [errmsg]:Required property is not set."));
    assertFalse(report.contains("See the log file"));
  }

  @Test
  @DisplayName("利用者が直せる誤りの原因（DBの接続エラー等）を併記し、原因を繰り返しているだけの例外（MyBatisの例外等）は省く")
  void testReportsCauseOfUserCorrectableFailure() {
    final String report =
        report(
            new UserCorrectableException(
                "Could not connect to the database.",
                new RuntimeException(
                    "### Error getting a new connection. Cause: Connection to 127.0.0.1:1 refused.",
                    new SQLException("Connection to 127.0.0.1:1 refused."))));

    assertTrue(report.contains(" [errmsg]:Could not connect to the database."));
    assertTrue(
        report.contains(" [cause]:java.sql.SQLException: Connection to 127.0.0.1:1 refused."));
    assertFalse(report.contains("### Error getting a new connection."));
  }

  @Test
  @DisplayName("原因を包む例外が独自の情報を持つ場合は、原因を浅い順にすべて併記する")
  void testReportsEveryInformativeCauseInOrder() {
    final String report =
        report(
            new UserCorrectableException(
                "Could not connect to the database.",
                new SQLException(
                    "Connection to 127.0.0.1:1 refused.",
                    new ConnectException("Connection refused"))));

    final int shallower =
        report.indexOf(" [cause]:java.sql.SQLException: Connection to 127.0.0.1:1");
    final int deeper = report.indexOf(" [cause]:java.net.ConnectException: Connection refused");
    assertTrue(shallower >= 0 && deeper > shallower);
  }

  @Test
  @DisplayName("想定外の失敗は、包まれた原因（DBが返したエラー等）とログの場所を報告する")
  void testReportsUnexpectedFailureWithCauseAndLogGuidance() {
    final String report =
        report(
            new RuntimeException(
                "Failed to select: selectAllTableInfo",
                new SQLException("ERROR: permission denied for table pg_description")));

    assertTrue(report.startsWith("[result]:FAIL"));
    assertTrue(report.contains(" [errmsg]:Failed to select: selectAllTableInfo"));
    assertTrue(
        report.contains(
            " [cause]:java.sql.SQLException: ERROR: permission denied for table pg_description"));
    assertTrue(report.contains("See the log file for details."));
  }

  @Test
  @DisplayName("JVMのエラー（Error）も、想定外の失敗として報告する")
  void testReportsErrorAsUnexpectedFailure() {
    final String report = report(new StackOverflowError("too deep"));

    assertTrue(report.contains(" [errmsg]:too deep"));
    assertTrue(report.contains("See the log file for details."));
  }

  @Test
  @DisplayName("原因のメッセージが既にメッセージに含まれている場合は、原因を重ねて表示しない")
  void testOmitsCauseAlreadyInMessage() {
    final String report =
        report(new UncheckedIOException(new IOException("/out: Not a directory")));

    assertTrue(report.contains("/out: Not a directory"));
    assertFalse(report.contains("[cause]"));
  }

  @Test
  @DisplayName("複数行の原因のメッセージは、各行が箇条書き等に整形されて表示済みであれば重ねて表示しない")
  void testOmitsMultiLineCauseAlreadyReformattedInMessage() {
    final String report =
        report(
            new InvalidConfigurationException(
                "Invalid configuration.\n  - Invalid table pattern: x.\n  - Unknown output object type: y",
                new IllegalArgumentException(
                    "Invalid table pattern: x."
                        + System.lineSeparator()
                        + "Unknown output object type: y")));

    assertFalse(report.contains("[cause]"));
  }

  @Test
  @DisplayName("メッセージを持たない例外は、クラス名で報告する")
  void testDescribesExceptionWithoutMessageByClassName() {
    assertTrue(
        report(new NullPointerException()).contains(" [errmsg]:java.lang.NullPointerException"));
  }

  @Test
  @DisplayName("原因が循環している例外でも、原因の探索は終了する")
  void testTerminatesOnCircularCause() {
    final RuntimeException first = new RuntimeException("first");
    final RuntimeException second = new RuntimeException("second", first);
    first.initCause(second);

    final String report = report(first);

    assertTrue(report.contains(" [errmsg]:first"));
    assertTrue(report.contains(" [cause]:java.lang.RuntimeException: second"));
  }
}
