package com.export_table_definition.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 配布するjarを子プロセスで起動し、MCPクライアントからstdioで呼び出すE2Eテスト<br>
 * マニフェスト・依存の同梱（ServiceLoaderの登録を含む）・標準出力にプロトコル以外を出さないことをまとめて確かめる
 */
class McpServerProcessTest {

  private static final String JAVA = ProcessHandle.current().info().command().orElse("java");
  private static final String JAR = System.getProperty("mcpServerJar");
  private static final String SAMPLE_SNAPSHOT = System.getProperty("sampleSnapshotDir");

  @Test
  @DisplayName("MCPクライアントから初期化・ツールの一覧の取得・ツールの呼び出しができる")
  void servesToolsOverStdio() throws Exception {
    final StdioClientTransport transport =
        new StdioClientTransport(
            ServerParameters.builder(JAVA)
                .args("-jar", JAR, "--snapshot=" + SAMPLE_SNAPSHOT)
                .build(),
            new JacksonMcpJsonMapper(new ObjectMapper()));
    try (McpSyncClient client =
        McpClient.sync(transport).requestTimeout(Duration.ofSeconds(30)).build()) {
      client.initialize();

      assertEquals("exportTableDefinition", client.getServerInfo().name());
      assertEquals(
          List.of("search_tables", "get_table", "get_related_tables"),
          client.listTools().tools().stream().map(Tool::name).toList());

      final CallToolResult search =
          client.callTool(
              CallToolRequest.builder("search_tables").arguments(Map.of("query", "従業員")).build());
      assertFalse(Boolean.TRUE.equals(search.isError()));
      assertTrue(text(search).contains("\"name\":\"employee\""), text(search));

      final CallToolResult related =
          client.callTool(
              CallToolRequest.builder("get_related_tables")
                  .arguments(Map.of("table", "audit_log"))
                  .build());
      assertTrue(text(related).contains("\"kind\":\"logicalRelation\""), text(related));

      final CallToolResult notFound =
          client.callTool(
              CallToolRequest.builder("get_table")
                  .arguments(Map.of("table", "no_such_table"))
                  .build());
      assertTrue(notFound.isError());
    }
  }

  @Test
  @DisplayName("スナップショットのディレクトリを読めない場合は、標準エラーに理由を出して終了コード2で終了する")
  void exitsWithMessageOnInvalidSnapshot() throws IOException, InterruptedException {
    final Process process =
        new ProcessBuilder(JAVA, "-jar", JAR, "--snapshot=no/such/directory").start();
    process.getOutputStream().close();

    assertTrue(process.waitFor(30, TimeUnit.SECONDS), "起動に失敗したら待たずに終了する");
    assertEquals(McpServerMain.EXIT_USER_CORRECTABLE, process.exitValue());
    assertEquals("", new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    final String stderr =
        new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    assertTrue(stderr.contains("[error]:スナップショットのディレクトリが見つかりません。"), stderr);
  }

  private static String text(CallToolResult result) {
    return ((TextContent) result.content().get(0)).text();
  }
}
