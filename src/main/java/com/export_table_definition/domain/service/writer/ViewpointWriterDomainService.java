package com.export_table_definition.domain.service.writer;

import com.export_table_definition.domain.model.document.ListDocumentType;
import com.export_table_definition.domain.model.relation.ForeignKeyGroup;
import com.export_table_definition.domain.model.relation.ForeignKeys;
import com.export_table_definition.domain.model.table.Tables;
import com.export_table_definition.domain.model.viewpoint.ViewpointContent;
import com.export_table_definition.domain.model.viewpoint.Viewpoints;
import com.export_table_definition.domain.repository.FileRepository;
import com.export_table_definition.domain.service.path.OutputPathResolver;
import com.export_table_definition.domain.service.path.OutputRoot;
import com.export_table_definition.domain.service.writer.PagedSectionWriter.PageLayout;
import com.export_table_definition.domain.service.writer.PagedSectionWriter.PagedSection;
import com.export_table_definition.domain.service.writer.template.ViewpointTemplates;
import jakarta.inject.Inject;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 観点ページ（観点ごとのER図・所属テーブル）と観点一覧を書き込むクラス<br>
 * 利用する情報はテーブル一覧と関連の一覧のみで、テーブル詳細を必要としない（スキーマ別ER図と同じ）
 */
public class ViewpointWriterDomainService {

  /** 観点ページの分割ページから本体ページへ戻るリンクの表示名 */
  private static final String VIEWPOINT_BACK_LABEL = "観点へ";

  private static final Logger logger = LogManager.getLogger(ViewpointWriterDomainService.class);
  private final FileRepository fileRepository;
  private final OutputPathResolver outputPathResolver;
  private final PagedSectionWriter pagedSectionWriter;

  /**
   * コンストラクタ
   *
   * @param fileRepository ファイルリポジトリ
   * @param outputPathResolver 出力パス解決クラス
   * @param pagedSectionWriter 行数の多い表のページ分割書き込みを行うクラス
   */
  @Inject
  public ViewpointWriterDomainService(
      FileRepository fileRepository,
      OutputPathResolver outputPathResolver,
      PagedSectionWriter pagedSectionWriter) {
    this.fileRepository = fileRepository;
    this.outputPathResolver = outputPathResolver;
    this.pagedSectionWriter = pagedSectionWriter;
  }

  /**
   * 観点ページと観点一覧の書き込み処理を行うメソッド<br>
   * 観点が1件も無い場合に出力しないことの判定は呼び出し側（出力する一覧の決定）が行う
   *
   * @param viewpoints サイドカーYAMLで宣言された観点
   * @param tables 出力対象のテーブル
   * @param foreignKeys 出力対象のテーブル同士の関連（外部キー・論理リレーション）
   * @param outputRoot 出力先ベースディレクトリとデータベース基本情報
   * @param maxNodes 1つの図に描画するノード数の上限。0以下の場合は上限なし
   */
  public void writeViewpoints(
      Viewpoints viewpoints,
      Tables tables,
      ForeignKeys foreignKeys,
      OutputRoot outputRoot,
      int maxNodes) {
    // 観点ページと観点一覧（テーブル数）の双方が同じ出力内容を用いるため、観点ごとに1回だけ求める
    final List<ViewpointContent> contents =
        viewpoints.asList().stream()
            .map(viewpoint -> viewpoint.resolve(tables, foreignKeys))
            .toList();
    contents.forEach(content -> writeViewpointPage(content, outputRoot, maxNodes));
    writeViewpointIndex(contents, outputRoot);
  }

  /**
   * 観点1つ分のページを書き込むメソッド<br>
   * 所属テーブル同士の関連をER図に描き、所属テーブルと、観点外のテーブルとの関連を一覧で掲載する。 ER図の描画を省略した場合は、代替として所属テーブル同士の関連を一覧で掲載する。<br>
   * 行数の多い表の分割は所属テーブルの一覧のみで行う（分割ページのファイル名は本体ページから決まるため、1ページで分割できる表は1つに限られる）。
   * 関連の一覧は、人が選んだテーブルのまとまりに関わるものに限られ、分割が必要になる規模にはならない想定とする
   *
   * @param content 1観点分の出力内容
   * @param outputRoot 出力先ベースディレクトリとデータベース基本情報
   * @param maxNodes 1つの図に描画するノード数の上限
   */
  private void writeViewpointPage(ViewpointContent content, OutputRoot outputRoot, int maxNodes) {
    final PageLayout layout =
        new PageLayout(
            ViewpointTemplates.fileHeader(content.viewpoint(), outputRoot.baseInfo()),
            outputPathResolver.resolveViewpointFile(outputRoot, content.viewpoint()),
            VIEWPOINT_BACK_LABEL);
    final String tableSection =
        content.tables().isEmpty()
            ? ViewpointTemplates.noTables()
            : pagedSectionWriter.writePagedSection(
                new PagedSection<>(
                    ViewpointTemplates.tableHeading(),
                    ViewpointTemplates.tableHeader(),
                    content.tables(),
                    ViewpointTemplates::tableLine),
                layout);
    final ForeignKeyGroup relations = content.relations();
    final List<String> contents =
        List.of(
            layout.fileHeader(), // ヘッダー
            ViewpointTemplates.baseInfo(outputRoot.baseInfo()), // 基本情報
            ViewpointTemplates.description(content.viewpoint()), // 説明
            ViewpointTemplates.erDiagram(relations, maxNodes), // ER図（描画結果または省略メッセージ）
            relations.exceeds(maxNodes)
                ? ViewpointTemplates.relations(relations.foreignKeys())
                : "", // ER図の代替の関連一覧
            tableSection, // 所属テーブル
            ViewpointTemplates.outsideRelations(content.outsideRelations()), // 観点外のテーブルとの関連
            ViewpointTemplates.footer(outputRoot.baseInfo()) // フッター
            );
    fileRepository.writeFile(layout.file(), contents);
    logger.debug("exportViewpoint complete. [filePath={}]", layout.file());
  }

  /**
   * 観点一覧を書き込むメソッド
   *
   * @param contents 観点ごとの出力内容（宣言順）
   * @param outputRoot 出力先ベースディレクトリとデータベース基本情報
   */
  private void writeViewpointIndex(List<ViewpointContent> contents, OutputRoot outputRoot) {
    final PageLayout layout =
        new PageLayout(
            ViewpointTemplates.indexFileHeader(outputRoot.baseInfo()),
            outputPathResolver.resolveListFile(outputRoot, ListDocumentType.VIEWPOINT),
            ListDocumentType.VIEWPOINT.getBackLinkLabel());
    final String indexSection =
        pagedSectionWriter.writePagedSection(
            new PagedSection<>(
                ViewpointTemplates.indexHeading(),
                ViewpointTemplates.indexHeader(),
                contents,
                (no, content) -> ViewpointTemplates.indexLine(no, content, outputRoot.baseInfo())),
            layout);
    final List<String> fileContents =
        List.of(
            layout.fileHeader(), // ヘッダー
            ViewpointTemplates.baseInfo(outputRoot.baseInfo()), // 基本情報
            indexSection, // 観点一覧
            ViewpointTemplates.indexFooter(outputRoot.baseInfo()) // フッター
            );
    fileRepository.writeFile(layout.file(), fileContents);
  }
}
