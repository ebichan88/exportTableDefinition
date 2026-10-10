package com.dbxray.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** {@link ServerArguments}のテスト */
class ServerArgumentsTest {

  @Test
  @DisplayName("--snapshot=の値をディレクトリとして受け取る（前後の空白は除く）")
  void parsesSnapshotDirectory() {
    assertEquals(
        Path.of("/work/docs/snapshot"),
        ServerArguments.parse("--snapshot= /work/docs/snapshot ").snapshotDirectory());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "--snapshot=", "--snapshot", "--other=x"})
  @DisplayName("--snapshotが無い・空・未知の引数の場合は、使い方を示して失敗にする")
  void rejectsInvalidArguments(String arg) {
    final String[] args = arg.isEmpty() ? new String[0] : new String[] {arg};

    final UserCorrectableException e =
        assertThrows(UserCorrectableException.class, () -> ServerArguments.parse(args));

    assertTrue(e.getMessage().endsWith(ServerArguments.USAGE), e.getMessage());
  }

  @Test
  @DisplayName("--snapshotが複数ある場合は失敗にする")
  void rejectsDuplicatedSnapshot() {
    assertThrows(
        UserCorrectableException.class,
        () -> ServerArguments.parse("--snapshot=a", "--snapshot=b"));
  }
}
