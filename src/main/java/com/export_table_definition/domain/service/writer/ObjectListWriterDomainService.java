package com.export_table_definition.domain.service.writer;

import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.FunctionEntity;
import com.export_table_definition.domain.model.entity.SequenceEntity;
import com.export_table_definition.domain.model.entity.TriggerEntity;
import com.export_table_definition.domain.model.entity.TypeEntity;
import com.export_table_definition.domain.model.type.ListDocumentType;
import com.export_table_definition.domain.repository.FileRepository;
import com.export_table_definition.domain.service.path.OutputPathResolver;
import com.export_table_definition.domain.service.writer.PagedSectionWriter.PageLayout;
import com.export_table_definition.domain.service.writer.PagedSectionWriter.PagedSection;
import com.export_table_definition.domain.service.writer.template.ObjectDefinitionTemplates;
import com.export_table_definition.domain.service.writer.template.ObjectListTemplates;
import com.google.inject.Inject;
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
   * トリガー一覧の書き込み処理を行うメソッド<br>
   * トリガーが存在しない場合は何も出力しない
   *
   * @param triggers トリガー情報リスト
   * @param baseInfo データベースの基本情報
   * @param outputDirectoryPath 出力ディレクトリのパス
   */
  public void writeTriggerList(
      List<TriggerEntity> triggers, BaseInfoEntity baseInfo, Path outputDirectoryPath) {
    writeObjectList(
        ListDocumentType.TRIGGER,
        ObjectListTemplates.triggerTableHeader(),
        triggers,
        ObjectListTemplates::triggerListLine,
        baseInfo,
        outputDirectoryPath);
  }

  /**
   * 関数・プロシージャ一覧の書き込み処理を行うメソッド<br>
   * 対象が存在しない場合は何も出力しない
   *
   * @param functions 関数・プロシージャの一覧情報リスト
   * @param baseInfo データベースの基本情報
   * @param outputDirectoryPath 出力ディレクトリのパス
   */
  public void writeFunctionList(
      List<FunctionEntity> functions, BaseInfoEntity baseInfo, Path outputDirectoryPath) {
    writeObjectList(
        ListDocumentType.FUNCTION,
        ObjectListTemplates.functionTableHeader(),
        functions,
        ObjectListTemplates::functionListLine,
        baseInfo,
        outputDirectoryPath);
  }

  /**
   * シーケンス一覧の書き込み処理を行うメソッド<br>
   * 対象が存在しない場合は何も出力しない
   *
   * @param sequences シーケンス情報リスト
   * @param baseInfo データベースの基本情報
   * @param outputDirectoryPath 出力ディレクトリのパス
   */
  public void writeSequenceList(
      List<SequenceEntity> sequences, BaseInfoEntity baseInfo, Path outputDirectoryPath) {
    writeObjectList(
        ListDocumentType.SEQUENCE,
        ObjectListTemplates.sequenceTableHeader(),
        sequences,
        ObjectListTemplates::sequenceListLine,
        baseInfo,
        outputDirectoryPath);
  }

  /**
   * ユーザー定義型一覧の書き込み処理を行うメソッド<br>
   * 対象が存在しない場合は何も出力しない
   *
   * @param types ユーザー定義型情報リスト
   * @param baseInfo データベースの基本情報
   * @param outputDirectoryPath 出力ディレクトリのパス
   */
  public void writeTypeList(
      List<TypeEntity> types, BaseInfoEntity baseInfo, Path outputDirectoryPath) {
    writeObjectList(
        ListDocumentType.TYPE,
        ObjectListTemplates.typeTableHeader(),
        types,
        ObjectListTemplates::typeListLine,
        baseInfo,
        outputDirectoryPath);
  }

  /**
   * オブジェクト一覧（トリガー/関数/シーケンス/型）の書き込み処理を行う共通メソッド<br>
   * 対象が存在しない場合は何も出力しない。 行数がMarkdownの表に表示できる最大件数を超える場合は、別ファイルへ分割する
   *
   * @param <T> エンティティの型
   * @param type 一覧の種別
   * @param tableHeader 表のヘッダー行
   * @param objects エンティティのリスト
   * @param lineMapper 行番号とエンティティから一覧1行分の文字列を生成する関数
   * @param baseInfo データベースの基本情報
   * @param outputDirectoryPath 出力ディレクトリのパス
   */
  private <T> void writeObjectList(
      ListDocumentType type,
      String tableHeader,
      List<T> objects,
      BiFunction<Integer, T, String> lineMapper,
      BaseInfoEntity baseInfo,
      Path outputDirectoryPath) {
    if (objects.isEmpty()) {
      return;
    }
    final PagedSection<T> section =
        new PagedSection<>(type.getTitle(), tableHeader, objects, lineMapper);
    final PageLayout layout =
        new PageLayout(
            ObjectListTemplates.fileHeader(type.getTitle(), baseInfo),
            outputPathResolver.resolveListFile(baseInfo, outputDirectoryPath, type),
            type.getBackLinkLabel());
    final List<String> contents =
        List.of(
            layout.fileHeader(), // ヘッダー
            ObjectListTemplates.baseInfo(baseInfo), // 基本情報
            pagedSectionWriter.writePagedSection(section, layout), // 一覧
            ObjectListTemplates.footer(baseInfo) // フッター
            );
    fileRepository.writeFile(layout.file(), contents);
  }

  /**
   * 関数・プロシージャの個別定義書き込み処理を行うメソッド
   *
   * @param function 関数・プロシージャ情報（定義本体を含む）
   * @param baseInfo データベースの基本情報
   * @param outputDirectoryPath 出力ディレクトリのパス
   */
  public void writeFunctionDefinition(
      FunctionEntity function, BaseInfoEntity baseInfo, Path outputDirectoryPath) {
    writeSchemaObjectDefinition(
        ListDocumentType.FUNCTION,
        function.schemaName(),
        function.fileName(),
        ObjectDefinitionTemplates.functionFile(function, baseInfo),
        baseInfo,
        outputDirectoryPath);
  }

  /**
   * シーケンスの個別定義書き込み処理を行うメソッド
   *
   * @param sequence シーケンス情報
   * @param baseInfo データベースの基本情報
   * @param outputDirectoryPath 出力ディレクトリのパス
   */
  public void writeSequenceDefinition(
      SequenceEntity sequence, BaseInfoEntity baseInfo, Path outputDirectoryPath) {
    writeSchemaObjectDefinition(
        ListDocumentType.SEQUENCE,
        sequence.schemaName(),
        sequence.sequenceName(),
        ObjectDefinitionTemplates.sequenceFile(sequence, baseInfo),
        baseInfo,
        outputDirectoryPath);
  }

  /**
   * ユーザー定義型の個別定義書き込み処理を行うメソッド
   *
   * @param type ユーザー定義型情報
   * @param baseInfo データベースの基本情報
   * @param outputDirectoryPath 出力ディレクトリのパス
   */
  public void writeTypeDefinition(
      TypeEntity type, BaseInfoEntity baseInfo, Path outputDirectoryPath) {
    writeSchemaObjectDefinition(
        ListDocumentType.TYPE,
        type.schemaName(),
        type.typeName(),
        ObjectDefinitionTemplates.typeFile(type, baseInfo),
        baseInfo,
        outputDirectoryPath);
  }

  /**
   * スキーマ配下オブジェクト（関数/シーケンス/型）の個別定義書き込み処理を行う共通メソッド
   *
   * @param kind オブジェクトの区分
   * @param schemaName スキーマ名
   * @param name 個別定義ファイル名（拡張子を除く）
   * @param content 個別定義ファイルの内容
   * @param baseInfo データベースの基本情報
   * @param outputDirectoryPath 出力ディレクトリのパス
   */
  private void writeSchemaObjectDefinition(
      ListDocumentType kind,
      String schemaName,
      String name,
      String content,
      BaseInfoEntity baseInfo,
      Path outputDirectoryPath) {
    final Path filePath =
        outputPathResolver.resolveSchemaObjectFile(
            baseInfo, outputDirectoryPath, schemaName, kind, name);
    fileRepository.createDirectory(
        outputPathResolver.resolveSchemaObjectDirectory(
            baseInfo, outputDirectoryPath, schemaName, kind));
    fileRepository.writeFile(filePath, List.of(content));
    logger.debug("exportSchemaObjectDefinition complete. [kind={}, filePath={}]", kind, filePath);
  }
}
