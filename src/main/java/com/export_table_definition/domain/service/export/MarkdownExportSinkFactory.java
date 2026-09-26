package com.export_table_definition.domain.service.export;

import com.export_table_definition.domain.model.ExportTargets;
import com.export_table_definition.domain.model.TableDefinitionContent;
import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.FunctionEntity;
import com.export_table_definition.domain.model.type.ListDocumentType;
import com.export_table_definition.domain.service.path.OutputRoot;
import com.export_table_definition.domain.service.writer.ErDiagramWriterDomainService;
import com.export_table_definition.domain.service.writer.ObjectListWriterDomainService;
import com.export_table_definition.domain.service.writer.TableDefinitionWriterDomainService;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Markdownのドキュメント（テーブル一覧・テーブル定義書・ER図・各種一覧と個別定義）を書き出す{@link ExportSink}を生成するクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class MarkdownExportSinkFactory {

  private final TableDefinitionWriterDomainService tableDefinitionWriter;
  private final ErDiagramWriterDomainService erDiagramWriter;
  private final ObjectListWriterDomainService objectListWriter;

  /**
   * コンストラクタ
   *
   * @param tableDefinitionWriter テーブル一覧・テーブル定義書を書き込むクラス
   * @param erDiagramWriter ER図を書き込むクラス
   * @param objectListWriter トリガー・関数・シーケンス・型の一覧および個別定義を書き込むクラス
   */
  @Inject
  public MarkdownExportSinkFactory(
      TableDefinitionWriterDomainService tableDefinitionWriter,
      ErDiagramWriterDomainService erDiagramWriter,
      ObjectListWriterDomainService objectListWriter) {
    this.tableDefinitionWriter = tableDefinitionWriter;
    this.erDiagramWriter = erDiagramWriter;
    this.objectListWriter = objectListWriter;
  }

  /**
   * 指定したディレクトリへMarkdownのドキュメントを書き出す{@link ExportSink}を生成するメソッド
   *
   * @param outputBaseDir 出力先のベースディレクトリパス
   * @param erDiagramMaxNodes スキーマ別ER図1枚に描画するノード数の上限。0以下の場合は上限なし
   * @return Markdownのドキュメントを書き出す{@link ExportSink}
   */
  public ExportSink create(Path outputBaseDir, int erDiagramMaxNodes) {
    return new MarkdownExportSink(outputBaseDir, erDiagramMaxNodes);
  }

  /**
   * 出力する一覧ドキュメントの種別を決めるメソッド<br>
   * テーブル一覧は常に出力し、それ以外の一覧は対象が1件以上存在する場合のみ出力する（空の一覧は出力しない）。
   * 一覧の書き出しと、テーブル一覧に掲載する関連ドキュメントへのリンクの双方がこの結果を用いるため、両者が食い違わない
   *
   * @param targets 一括取得した出力対象の情報
   * @return 出力する一覧ドキュメントの種別の集合
   */
  private static Set<ListDocumentType> listDocuments(ExportTargets targets) {
    final Set<ListDocumentType> documents = EnumSet.of(ListDocumentType.TABLE);
    if (!targets.tables().isEmpty()) {
      documents.add(ListDocumentType.ER_DIAGRAM);
    }
    if (!targets.functions().isEmpty()) {
      documents.add(ListDocumentType.FUNCTION);
    }
    if (!targets.sequences().isEmpty()) {
      documents.add(ListDocumentType.SEQUENCE);
    }
    if (!targets.types().isEmpty()) {
      documents.add(ListDocumentType.TYPE);
    }
    if (!targets.triggers().isEmpty()) {
      documents.add(ListDocumentType.TRIGGER);
    }
    return documents;
  }

  /**
   * テーブル一覧に掲載する関連ドキュメント（テーブル一覧以外の一覧へのリンク）を決めるメソッド
   *
   * @param documents 出力する一覧ドキュメントの種別の集合
   * @return リンクを掲載する一覧の種別（掲載順＝{@link ListDocumentType}の宣言順）
   */
  private static List<ListDocumentType> relatedDocuments(Set<ListDocumentType> documents) {
    return documents.stream().filter(type -> type != ListDocumentType.TABLE).toList();
  }

  /** 1回の出力先・設定に紐づく、Markdownのドキュメントの{@link ExportSink}実装 */
  private final class MarkdownExportSink implements ExportSink {

    private final Path outputBaseDir;
    private final int erDiagramMaxNodes;

    /**
     * コンストラクタ
     *
     * @param outputBaseDir 出力先のベースディレクトリパス
     * @param erDiagramMaxNodes スキーマ別ER図1枚に描画するノード数の上限
     */
    private MarkdownExportSink(Path outputBaseDir, int erDiagramMaxNodes) {
      this.outputBaseDir = outputBaseDir;
      this.erDiagramMaxNodes = erDiagramMaxNodes;
    }

    /**
     * {@inheritDoc}<br>
     * テーブル一覧・ER図・各種一覧・シーケンス/型の個別定義を書き出す。ER図はテーブル一覧と外部キー一覧のみで 生成できるため、テーブル詳細をチャンク単位で取得する前のこの時点で書き出せる
     */
    @Override
    public void writeOverview(ExportTargets targets) {
      final OutputRoot outputRoot = new OutputRoot(outputBaseDir, targets.baseInfo());
      final Set<ListDocumentType> documents = listDocuments(targets);
      // テーブル一覧出力 -> {outputBaseDir}/tableList_{DB名}.md
      tableDefinitionWriter.writeTableDefinitionList(
          targets.tables().asList(), outputRoot, relatedDocuments(documents));
      // スキーマ別ER図と、その索引の出力
      if (documents.contains(ListDocumentType.ER_DIAGRAM)) {
        erDiagramWriter.writeErDiagram(
            targets.tables(), targets.foreignKeys(), outputRoot, erDiagramMaxNodes);
      }
      // トリガー・関数・シーケンス・型の一覧出力（対象が存在しない一覧は出力しない）
      if (documents.contains(ListDocumentType.TRIGGER)) {
        objectListWriter.writeTriggerList(targets.triggers(), outputRoot);
      }
      if (documents.contains(ListDocumentType.FUNCTION)) {
        objectListWriter.writeFunctionList(targets.functions(), outputRoot);
      }
      if (documents.contains(ListDocumentType.SEQUENCE)) {
        objectListWriter.writeSequenceList(targets.sequences(), outputRoot);
      }
      if (documents.contains(ListDocumentType.TYPE)) {
        objectListWriter.writeTypeList(targets.types(), outputRoot);
      }
      // シーケンス・型の個別ファイル出力（情報が小さいため一覧取得結果をそのまま利用する）
      targets
          .sequences()
          .forEach(sequence -> objectListWriter.writeSequenceDefinition(sequence, outputRoot));
      targets.types().forEach(type -> objectListWriter.writeTypeDefinition(type, outputRoot));
    }

    /** {@inheritDoc} */
    @Override
    public void writeFunctionDefinitions(
        String schemaName, List<FunctionEntity> functions, BaseInfoEntity baseInfo) {
      final OutputRoot outputRoot = new OutputRoot(outputBaseDir, baseInfo);
      functions.forEach(function -> objectListWriter.writeFunctionDefinition(function, outputRoot));
    }

    /** {@inheritDoc} */
    @Override
    public void writeTableDefinition(TableDefinitionContent content) {
      // テーブル定義出力 -> {outputBaseDir}/{DB名}/{スキーマ名}/{TBL分類}/{物理テーブル名}.md
      tableDefinitionWriter.writeTableDefinition(content, outputBaseDir);
    }
  }
}
