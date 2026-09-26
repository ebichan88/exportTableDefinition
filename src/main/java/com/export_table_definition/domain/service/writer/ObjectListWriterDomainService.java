package com.export_table_definition.domain.service.writer;

import com.export_table_definition.domain.model.document.ListDocumentType;
import com.export_table_definition.domain.model.schemaobject.FunctionEntity;
import com.export_table_definition.domain.model.schemaobject.SequenceEntity;
import com.export_table_definition.domain.model.schemaobject.TypeEntity;
import com.export_table_definition.domain.model.table.TriggerEntity;
import com.export_table_definition.domain.repository.FileRepository;
import com.export_table_definition.domain.service.path.DocumentLocations;
import com.export_table_definition.domain.service.path.OutputPathResolver;
import com.export_table_definition.domain.service.path.OutputRoot;
import com.export_table_definition.domain.service.writer.PagedSectionWriter.PageLayout;
import com.export_table_definition.domain.service.writer.PagedSectionWriter.PagedSection;
import com.export_table_definition.domain.service.writer.template.ObjectDefinitionTemplates;
import com.export_table_definition.domain.service.writer.template.ObjectListTemplates;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * トリガー・関数/プロシージャ・シーケンス・ユーザー定義型の一覧および個別定義を書き込むクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class ObjectListWriterDomainService {

  private static final Logger logger = LogManager.getLogger(ObjectListWriterDomainService.class);
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
  public ObjectListWriterDomainService(
      FileRepository fileRepository,
      OutputPathResolver outputPathResolver,
      PagedSectionWriter pagedSectionWriter) {
    this.fileRepository = fileRepository;
    this.outputPathResolver = outputPathResolver;
    this.pagedSectionWriter = pagedSectionWriter;
  }

  /**
   * トリガー一覧の書き込み処理を行うメソッド
   *
   * @param triggers トリガー情報リスト
   * @param outputRoot 出力先ベースディレクトリとデータベース基本情報
   */
  public void writeTriggerList(List<TriggerEntity> triggers, OutputRoot outputRoot) {
    writeObjectList(
        ListDocumentType.TRIGGER,
        ObjectListTemplates.triggerTableHeader(),
        triggers,
        ObjectListTemplates::triggerListLine,
        outputRoot);
  }

  /**
   * 関数・プロシージャ一覧の書き込み処理を行うメソッド
   *
   * @param functions 関数・プロシージャの一覧情報リスト
   * @param outputRoot 出力先ベースディレクトリとデータベース基本情報
   */
  public void writeFunctionList(List<FunctionEntity> functions, OutputRoot outputRoot) {
    writeObjectList(
        ListDocumentType.FUNCTION,
        ObjectListTemplates.functionTableHeader(),
        functions,
        ObjectListTemplates::functionListLine,
        outputRoot);
  }

  /**
   * シーケンス一覧の書き込み処理を行うメソッド
   *
   * @param sequences シーケンス情報リスト
   * @param outputRoot 出力先ベースディレクトリとデータベース基本情報
   */
  public void writeSequenceList(List<SequenceEntity> sequences, OutputRoot outputRoot) {
    writeObjectList(
        ListDocumentType.SEQUENCE,
        ObjectListTemplates.sequenceTableHeader(),
        sequences,
        ObjectListTemplates::sequenceListLine,
        outputRoot);
  }

  /**
   * ユーザー定義型一覧の書き込み処理を行うメソッド
   *
   * @param types ユーザー定義型情報リスト
   * @param outputRoot 出力先ベースディレクトリとデータベース基本情報
   */
  public void writeTypeList(List<TypeEntity> types, OutputRoot outputRoot) {
    writeObjectList(
        ListDocumentType.TYPE,
        ObjectListTemplates.typeTableHeader(),
        types,
        ObjectListTemplates::typeListLine,
        outputRoot);
  }

  /**
   * オブジェクト一覧（トリガー/関数/シーケンス/型）の書き込み処理を行う共通メソッド<br>
   * 行数がMarkdownの表に表示できる最大件数を超える場合は、別ファイルへ分割する。 対象が存在しない一覧を出力しないことの判定は呼び出し側（出力する一覧の決定）が行う
   *
   * @param <T> エンティティの型
   * @param type 一覧の種別
   * @param tableHeader 表のヘッダー行
   * @param objects エンティティのリスト
   * @param lineMapper 行番号とエンティティから一覧1行分の文字列を生成する関数
   * @param outputRoot 出力先ベースディレクトリとデータベース基本情報
   */
  private <T> void writeObjectList(
      ListDocumentType type,
      String tableHeader,
      List<T> objects,
      BiFunction<Integer, T, String> lineMapper,
      OutputRoot outputRoot) {
    final PagedSection<T> section =
        new PagedSection<>(type.getTitle(), tableHeader, objects, lineMapper);
    final PageLayout layout =
        new PageLayout(
            ObjectListTemplates.fileHeader(type.getTitle(), outputRoot.baseInfo()),
            outputPathResolver.resolveListFile(outputRoot, type),
            type.getBackLinkLabel());
    final String objectListSection = pagedSectionWriter.writePagedSection(section, layout);
    final List<String> contents =
        List.of(
            layout.fileHeader(), // ヘッダー
            ObjectListTemplates.baseInfo(outputRoot.baseInfo()), // 基本情報
            objectListSection, // 一覧
            ObjectListTemplates.footer(outputRoot.baseInfo()) // フッター
            );
    fileRepository.writeFile(layout.file(), contents);
  }

  /**
   * 関数・プロシージャの個別定義書き込み処理を行うメソッド
   *
   * @param function 関数・プロシージャ情報（定義本体を含む）
   * @param outputRoot 出力先ベースディレクトリとデータベース基本情報
   */
  public void writeFunctionDefinition(FunctionEntity function, OutputRoot outputRoot) {
    writeSchemaObjectDefinition(
        ListDocumentType.FUNCTION,
        function.schemaName(),
        DocumentLocations.functionDefinitionName(function),
        ObjectDefinitionTemplates.functionFile(function, outputRoot.baseInfo()),
        outputRoot);
  }

  /**
   * シーケンスの個別定義書き込み処理を行うメソッド
   *
   * @param sequence シーケンス情報
   * @param outputRoot 出力先ベースディレクトリとデータベース基本情報
   */
  public void writeSequenceDefinition(SequenceEntity sequence, OutputRoot outputRoot) {
    writeSchemaObjectDefinition(
        ListDocumentType.SEQUENCE,
        sequence.schemaName(),
        sequence.sequenceName(),
        ObjectDefinitionTemplates.sequenceFile(sequence, outputRoot.baseInfo()),
        outputRoot);
  }

  /**
   * ユーザー定義型の個別定義書き込み処理を行うメソッド
   *
   * @param type ユーザー定義型情報
   * @param outputRoot 出力先ベースディレクトリとデータベース基本情報
   */
  public void writeTypeDefinition(TypeEntity type, OutputRoot outputRoot) {
    writeSchemaObjectDefinition(
        ListDocumentType.TYPE,
        type.schemaName(),
        type.typeName(),
        ObjectDefinitionTemplates.typeFile(type, outputRoot.baseInfo()),
        outputRoot);
  }

  /**
   * スキーマ配下オブジェクト（関数/シーケンス/型）の個別定義書き込み処理を行う共通メソッド
   *
   * @param kind オブジェクトの区分
   * @param schemaName スキーマ名
   * @param name 個別定義ファイル名（拡張子を除く）
   * @param content 個別定義ファイルの内容
   * @param outputRoot 出力先ベースディレクトリとデータベース基本情報
   */
  private void writeSchemaObjectDefinition(
      ListDocumentType kind,
      String schemaName,
      String name,
      String content,
      OutputRoot outputRoot) {
    final Path filePath =
        outputPathResolver.resolveSchemaObjectFile(outputRoot, schemaName, kind, name);
    fileRepository.createDirectory(
        outputPathResolver.resolveSchemaObjectDirectory(outputRoot, schemaName, kind));
    fileRepository.writeFile(filePath, List.of(content));
    logger.debug("exportSchemaObjectDefinition complete. [kind={}, filePath={}]", kind, filePath);
  }
}
