package com.dbxray.mcp.tool;

import com.dbxray.mcp.catalog.SchemaCatalog;
import com.dbxray.mcp.catalog.ViewpointEntry;
import java.util.List;
import java.util.Optional;

/** ツールの引数{@code viewpoint}（観点のid）で指定された観点の解決 */
final class ViewpointResolver {

  private ViewpointResolver() {}

  /**
   * 引数{@code viewpoint}・{@code database}で指定された観点を解決するメソッド
   *
   * @return {@code viewpoint}が未指定の場合は空
   * @throws InvalidToolArgumentException 指定した観点が見つからない場合（宣言済みの観点のidをメッセージに含める）
   */
  static Optional<ViewpointEntry> resolve(SchemaCatalog catalog, ToolArguments arguments) {
    final String id = arguments.optionalString("viewpoint");
    if (id.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        catalog
            .findViewpoint(arguments.scope(), id)
            .orElseThrow(() -> notFound(catalog, id, arguments)));
  }

  private static InvalidToolArgumentException notFound(
      SchemaCatalog catalog, String id, ToolArguments arguments) {
    final List<String> ids =
        catalog.listViewpoints(arguments.scope()).stream().map(ViewpointEntry::id).toList();
    if (ids.isEmpty()) {
      return new InvalidToolArgumentException("観点" + id + "が見つかりません。観点は1件も宣言されていません。");
    }
    return new InvalidToolArgumentException("観点" + id + "が見つかりません。観点: " + String.join(", ", ids));
  }
}
