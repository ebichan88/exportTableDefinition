package com.export_table_definition.domain.service.insight;

import com.export_table_definition.domain.model.insight.ViewpointsInsight;
import com.export_table_definition.domain.model.viewpoint.ViewpointContent;
import com.export_table_definition.domain.repository.FileRepository;
import com.export_table_definition.domain.service.path.OutputPathResolver;
import com.export_table_definition.domain.service.path.OutputRoot;
import com.export_table_definition.domain.service.snapshot.SnapshotSerializer;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 参考情報（{@code insights}。スナップショットの事実とは別にAIへ渡す情報）を書き込むクラス<br>
 * {@code --check}の比較対象ではないため、スナップショットとは別の出力先（{@link
 * com.export_table_definition.domain.service.path.InsightLocations}）へ書き出す
 */
public class InsightWriter {

  /** JSON Linesの他のファイルと合わせ、OSに依らず行区切りはLFとする */
  private static final String LINE_SEPARATOR = "\n";

  private static final Logger logger = LogManager.getLogger(InsightWriter.class);
  private final FileRepository fileRepository;
  private final OutputPathResolver outputPathResolver;
  private final SnapshotSerializer serializer;

  @Inject
  public InsightWriter(
      FileRepository fileRepository,
      OutputPathResolver outputPathResolver,
      SnapshotSerializer serializer) {
    this.fileRepository = fileRepository;
    this.outputPathResolver = outputPathResolver;
    this.serializer = serializer;
  }

  /**
   * 観点の参考情報を書き込むメソッド<br>
   * 観点を宣言していない場合（{@code contents}が空）は、ファイル自体を出力しない
   *
   * @param contents 出力対象の観点ごとの出力内容（{@code Viewpoint.resolve}で求めたもの）
   */
  public void writeViewpoints(List<ViewpointContent> contents, OutputRoot root) {
    if (contents.isEmpty()) {
      return;
    }
    write(outputPathResolver.resolveViewpointsInsightFile(root), ViewpointsInsight.of(contents));
  }

  private void write(Path filePath, Object insight) {
    fileRepository.createDirectory(filePath.getParent());
    fileRepository.writeFile(filePath, List.of(serializer.serialize(insight) + LINE_SEPARATOR));
    logger.debug("exportInsight complete. [filePath={}]", filePath);
  }
}
