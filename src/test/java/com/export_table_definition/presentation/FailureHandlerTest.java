package com.export_table_definition.presentation;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.config.InvalidConfigurationException;
import com.export_table_definition.domain.UserCorrectableException;
import com.export_table_definition.presentation.type.ExitStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** FailureHandler の失敗の捕捉・報告・終了状態への変換に関するテスト */
public class FailureHandlerTest {

  private static final String SUMMARY = "Failed to output table definition document.";

  private final List<String> console = new ArrayList<>();
  private final FailureHandler handler = new FailureHandler(SUMMARY, console::add);

  private String report() {
    assertEquals(1, console.size());
    return console.get(0);
  }

  @Test
  @DisplayName("処理が成功した場合は、処理が返した終了状態をそのまま返し、何も報告しない")
  void testReturnsCommandExitStatusOnSuccess() {
    assertEquals(ExitStatus.SUCCESS, handler.run(() -> ExitStatus.SUCCESS));
    assertEquals(ExitStatus.DIFFERENCE_FOUND, handler.run(() -> ExitStatus.DIFFERENCE_FOUND));
    assertTrue(console.isEmpty());
  }

  @Test
  @DisplayName("利用者が直せる誤りは、FAILと要旨・メッセージを報告し、ログの案内は付けずにFAILUREを返す")
  void testReportsUserCorrectableFailureWithoutLogGuidance() {
    final ExitStatus status =
        handler.run(
            () -> {
              throw new UserCorrectableException("Refusing to run --rm-dist. [outputBaseDir=/]");
            });

    assertEquals(ExitStatus.FAILURE, status);
    final String report = report();
    assertTrue(report.startsWith("[result]:FAIL"));
    assertTrue(report.contains(SUMMARY));
    assertTrue(report.contains(" [errmsg]:Refusing to run --rm-dist. [outputBaseDir=/]"));
    assertFalse(report.contains("[cause]"));
    assertFalse(report.contains("See the log file"));
  }

  @Test
  @DisplayName("設定の誤り（InvalidConfigurationException）も利用者が直せる誤りとして報告する")
  void testReportsInvalidConfigurationAsUserCorrectable() {
    final ExitStatus status =
        handler.run(
            () -> {
              throw new InvalidConfigurationException("Required property is not set.");
            });

    assertEquals(ExitStatus.FAILURE, status);
    assertTrue(report().contains(" [errmsg]:Required property is not set."));
    assertFalse(report().contains("See the log file"));
  }

  @Test
  @DisplayName("利用者が直せる誤りの原因（DBの接続エラー等）を併記し、原因を繰り返しているだけの例外（MyBatisの例外等）は省く")
  void testReportsCauseOfUserCorrectableFailure() {
    handler.run(
        () -> {
          throw new UserCorrectableException(
              "Could not connect to the database.",
              new RuntimeException(
                  "### Error getting a new connection. Cause: Connection to 127.0.0.1:1 refused.",
                  new SQLException("Connection to 127.0.0.1:1 refused.")));
        });

    final String report = report();
    assertTrue(report.contains(" [errmsg]:Could not connect to the database."));
    assertTrue(
        report.contains(" [cause]:java.sql.SQLException: Connection to 127.0.0.1:1 refused."));
    assertFalse(report.contains("### Error getting a new connection."));
  }

  @Test
  @DisplayName("原因を包む例外が独自の情報を持つ場合は、原因を浅い順にすべて併記する")
  void testReportsEveryInformativeCauseInOrder() {
    handler.run(
        () -> {
          throw new UserCorrectableException(
              "Could not connect to the database.",
              new SQLException(
                  "Connection to 127.0.0.1:1 refused.",
                  new ConnectException("Connection refused")));
        });

    final String report = report();
    final int shallower =
        report.indexOf(" [cause]:java.sql.SQLException: Connection to 127.0.0.1:1");
    final int deeper = report.indexOf(" [cause]:java.net.ConnectException: Connection refused");
    assertTrue(shallower >= 0 && deeper > shallower);
  }

  @Test
  @DisplayName("想定外の失敗は、包まれた原因（DBが返したエラー等）とログの場所を報告し、FAILUREを返す")
  void testReportsUnexpectedFailureWithCauseAndLogGuidance() {
    final ExitStatus status =
        handler.run(
            () -> {
              throw new RuntimeException(
                  "Failed to select: selectAllTableInfo",
                  new SQLException("ERROR: permission denied for table pg_description"));
            });

    assertEquals(ExitStatus.FAILURE, status);
    final String report = report();
    assertTrue(report.startsWith("[result]:FAIL"));
    assertTrue(report.contains(" [errmsg]:Failed to select: selectAllTableInfo"));
    assertTrue(
        report.contains(
            " [cause]:java.sql.SQLException: ERROR: permission denied for table pg_description"));
    assertTrue(report.contains("See the log file for details."));
  }

  @Test
  @DisplayName("原因のメッセージが既にメッセージに含まれている場合は、原因を重ねて表示しない")
  void testOmitsCauseAlreadyInMessage() {
    handler.run(
        () -> {
          throw new UncheckedIOException(new IOException("/out: Not a directory"));
        });

    assertTrue(report().contains("/out: Not a directory"));
    assertFalse(report().contains("[cause]"));
  }

  @Test
  @DisplayName("メッセージを持たない例外は、クラス名で報告する")
  void testDescribesExceptionWithoutMessageByClassName() {
    handler.run(
        () -> {
          throw new NullPointerException();
        });

    assertTrue(report().contains(" [errmsg]:java.lang.NullPointerException"));
  }

  @Test
  @DisplayName("JVMのエラー（Error）も捕捉してFAILUREを返す（終了コード1＝差分ありと区別するため）")
  void testConvertsErrorToFailure() {
    final ExitStatus status =
        handler.run(
            () -> {
              throw new StackOverflowError("too deep");
            });

    assertEquals(ExitStatus.FAILURE, status);
    assertTrue(report().contains(" [errmsg]:too deep"));
  }

  @Test
  @DisplayName("原因が循環している例外でも、原因の探索は終了する")
  void testTerminatesOnCircularCause() {
    final RuntimeException first = new RuntimeException("first");
    final RuntimeException second = new RuntimeException("second", first);
    first.initCause(second);

    final ExitStatus status =
        handler.run(
            () -> {
              throw first;
            });

    assertEquals(ExitStatus.FAILURE, status);
    assertTrue(report().contains(" [errmsg]:first"));
    assertTrue(report().contains(" [cause]:java.lang.RuntimeException: second"));
  }
}
