package com.export_table_definition.domain.service.writer;

import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.FunctionEntity;
import com.export_table_definition.domain.model.entity.SequenceEntity;
import com.export_table_definition.domain.model.entity.TriggerEntity;
import com.export_table_definition.domain.model.entity.TypeEntity;
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
        "トリガー一覧",
        "trigger",
        ObjectListTemplates.triggerTableHeader(),
        triggers,
        ObjectListTemplates::triggerListLine,
        baseInfo,
        outputDirectoryPath);
  }

  /**
   * オブジェクト一覧（トリガー/関数/シーケンス/型）の書き込み処理を行う共通メソッド<br>
   * 対象が存在しない場合は何も出力しない。 行数がMarkdownの表に表示できる最大件数を超える場合は、別ファイルへ分割する
   *
   * @param <T> エンティティの型
   * @param title 一覧のタイトル
   * @param prefix 一覧ファイル名の接頭辞（例: trigger, function, sequence, type）
   * @param tableHeader 表のヘッダー行
   * @param objects エンティティのリスト
   * @param lineMapper 行番号とエンティティから一覧1行分の文字列を生成する関数
   * @param baseInfo データベースの基本情報
   * @param outputDirectoryPath 出力ディレクトリのパス
   */
  private <T> void writeObjectList(
      String title,
      String prefix,
      String tableHeader,
      List<T> objects,
      BiFunction<Integer, T, String> lineMapper,
      BaseInfoEntity baseInfo,
      Path outputDirectoryPath) {
    if (objects.isEmpty()) {
      return;
    }
    final PagedSection<T> section = new PagedSection<>(title, tableHeader, objects, lineMapper);
    final PageLayout layout =
        new PageLayout(
            ObjectListTemplates.fileHeader(title, baseInfo),
            page ->
                outputPathResolver.resolveObjectListFile(
                    baseInfo, outputDirectoryPath, prefix, page),
            page -> String.format("./%sList_%s_%d.md", prefix, baseInfo.dbName(), page),
            String.format("./%sList_%s.md", prefix, baseInfo.dbName()),
            title + "へ");
    final List<String> contents =
        List.of(
            ObjectListTemplates.fileHeader(title, baseInfo), // ヘッダー
            ObjectListTemplates.baseInfo(baseInfo), // 基本情報
            pagedSectionWriter.writePagedSection(section, layout), // 一覧
            ObjectListTemplates.footer(baseInfo) // フッター
            );
    fileRepository.writeFile(
        outputPathResolver.resolveObjectListFile(baseInfo, outputDirectoryPath, prefix), contents);
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
        "関数・プロシージャ一覧",
        "function",
        ObjectListTemplates.functionTableHeader(),
        functions,
        ObjectListTemplates::functionListLine,
        baseInfo,
        outputDirectoryPath);
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
    final Path directoryPath =
        outputPathResolver.resolveSchemaObjectDirectory(
            baseInfo, outputDirectoryPath, function.schemaName(), "function");
    final Path filePath =
        outputPathResolver.resolveSchemaObjectFile(
            baseInfo, outputDirectoryPath, function.schemaName(), "function", function.fileName());
    fileRepository.createDirectory(directoryPath);
    fileRepository.writeFile(
        filePath, List.of(ObjectDefinitionTemplates.functionFile(function, baseInfo)));
    logger.debug("exportFunctionDefinition complete. [filePath={}]", filePath.toString());
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
        "シーケンス一覧",
        "sequence",
        ObjectListTemplates.sequenceTableHeader(),
        sequences,
        ObjectListTemplates::sequenceListLine,
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
    final Path directoryPath =
        outputPathResolver.resolveSchemaObjectDirectory(
            baseInfo, outputDirectoryPath, sequence.schemaName(), "sequence");
    final Path filePath =
        outputPathResolver.resolveSchemaObjectFile(
            baseInfo,
            outputDirectoryPath,
            sequence.schemaName(),
            "sequence",
            sequence.sequenceName());
    fileRepository.createDirectory(directoryPath);
    fileRepository.writeFile(
        filePath, List.of(ObjectDefinitionTemplates.sequenceFile(sequence, baseInfo)));
    logger.debug("exportSequenceDefinition complete. [filePath={}]", filePath.toString());
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
        "ユーザー定義型一覧",
        "type",
        ObjectListTemplates.typeTableHeader(),
        types,
        ObjectListTemplates::typeListLine,
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
    final Path directoryPath =
        outputPathResolver.resolveSchemaObjectDirectory(
            baseInfo, outputDirectoryPath, type.schemaName(), "type");
    final Path filePath =
        outputPathResolver.resolveSchemaObjectFile(
            baseInfo, outputDirectoryPath, type.schemaName(), "type", type.typeName());
    fileRepository.createDirectory(directoryPath);
    fileRepository.writeFile(filePath, List.of(ObjectDefinitionTemplates.typeFile(type, baseInfo)));
    logger.debug("exportTypeDefinition complete. [filePath={}]", filePath.toString());
  }
}
