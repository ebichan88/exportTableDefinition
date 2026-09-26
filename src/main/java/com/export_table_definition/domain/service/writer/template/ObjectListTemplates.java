package com.export_table_definition.domain.service.writer.template;

import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.LINE_SEPARATOR;
import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.row;

import com.export_table_definition.domain.model.database.BaseInfoEntity;
import com.export_table_definition.domain.model.document.ListDocumentType;
import com.export_table_definition.domain.model.schemaobject.FunctionEntity;
import com.export_table_definition.domain.model.schemaobject.SequenceEntity;
import com.export_table_definition.domain.model.schemaobject.TypeEntity;
import com.export_table_definition.domain.model.table.TriggerEntity;
import com.export_table_definition.domain.service.path.DocumentLocations;

/**
 * トリガー・関数/プロシージャ・シーケンス・ユーザー定義型の一覧書き込みに利用する Markdownのテンプレートを扱うクラス<br>
 * 表のセクションはヘッダーと1行分を個別に生成できるようにしている。 行数が多い場合に呼び出し側がページ単位で切り出して書き込めるようにするためで、 テーブル一覧（{@link
 * TableDefinitionListTemplates}）と同じ方針である
 */
public class ObjectListTemplates {

  /** 一覧ファイルヘッダー */
  public static String fileHeader(String title, BaseInfoEntity baseInfo) {
    return MarkdownTemplateSupport.titledFileHeader(title, baseInfo);
  }

  /** 基本情報セクション */
  public static String baseInfo(BaseInfoEntity baseInfo) {
    return MarkdownTemplateSupport.baseInfoSection(baseInfo);
  }

  /** トリガー一覧セクションの表ヘッダー */
  public static String triggerTableHeader() {
    return """
                | No. | スキーマ名 | テーブル名 | トリガー名 | タイミング | イベント | 実行関数 |
                |:---|:---|:---|:---|:---|:---|:---|
                """;
  }

  /** 関数・プロシージャ一覧セクションの表ヘッダー */
  public static String functionTableHeader() {
    return """
                | No. | スキーマ名 | 種別 | 名前 | 引数 | 戻り値 | 言語 | Link |
                |:---|:---|:---|:---|:---|:---|:---|:---|
                """;
  }

  /** シーケンス一覧セクションの表ヘッダー */
  public static String sequenceTableHeader() {
    return """
                | No. | スキーマ名 | シーケンス名 | 増分 | 最小値 | 最大値 | キャッシュ | 開始値 | 循環 | 所有カラム | Link |
                |:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|
                """;
  }

  /** ユーザー定義型一覧セクションの表ヘッダー */
  public static String typeTableHeader() {
    return """
                | No. | スキーマ名 | 型名 | 種別 | 定義 | Link |
                |:---|:---|:---|:---|:---|:---|
                """;
  }

  /**
   * @return 一覧1行分の文字列（末尾の改行を含む）
   */
  public static String triggerListLine(int no, TriggerEntity trigger) {
    return row(
            no,
            trigger.schemaName(),
            trigger.tableName(),
            trigger.triggerName(),
            trigger.timing(),
            String.join("/", trigger.events()),
            trigger.functionName())
        + LINE_SEPARATOR;
  }

  /**
   * 引数・戻り値の型表記は{@code |}を含みうるためエスケープする
   *
   * @return 一覧1行分の文字列（末尾の改行を含む）
   */
  public static String functionListLine(int no, FunctionEntity function) {
    return row(
            no,
            function.schemaName(),
            function.functionKind(),
            function.functionName(),
            MarkdownTemplateSupport.escapePipe(function.functionArguments()),
            MarkdownTemplateSupport.escapePipe(function.functionResult()),
            function.languageName(),
            objectLink(
                function.dbName(),
                function.schemaName(),
                ListDocumentType.FUNCTION,
                DocumentLocations.functionDefinitionName(function)))
        + LINE_SEPARATOR;
  }

  /**
   * @return 一覧1行分の文字列（末尾の改行を含む）
   */
  public static String sequenceListLine(int no, SequenceEntity sequence) {
    return row(
            no,
            sequence.schemaName(),
            sequence.sequenceName(),
            sequence.incrementBy(),
            sequence.minValue(),
            sequence.maxValue(),
            sequence.cacheSize(),
            sequence.startValue(),
            MarkdownTemplateSupport.marker(sequence.cycle()),
            sequence.ownedBy(),
            objectLink(
                sequence.dbName(),
                sequence.schemaName(),
                ListDocumentType.SEQUENCE,
                sequence.sequenceName()))
        + LINE_SEPARATOR;
  }

  /**
   * 定義はENUMのラベル等に{@code |}を含みうるためエスケープする
   *
   * @return 一覧1行分の文字列（末尾の改行を含む）
   */
  public static String typeListLine(int no, TypeEntity type) {
    return row(
            no,
            type.schemaName(),
            type.typeName(),
            type.typeCategory(),
            MarkdownTemplateSupport.escapePipe(type.definition()),
            objectLink(type.dbName(), type.schemaName(), ListDocumentType.TYPE, type.typeName()))
        + LINE_SEPARATOR;
  }

  /**
   * 一覧は出力ベースディレクトリ直下に配置されるため、出力ベースディレクトリからの相対パスで参照する
   *
   * @param kind オブジェクトの区分（関数・プロシージャ／シーケンス／ユーザー定義型）
   */
  private static String objectLink(
      String dbName, String schemaName, ListDocumentType kind, String fileName) {
    return MarkdownTemplateSupport.linkCell(
        DocumentLocations.linkFromBase(
            DocumentLocations.schemaObjectFile(dbName, schemaName, kind, fileName)));
  }

  /** 個別定義書から一覧へ戻る導線（{@link ObjectDefinitionTemplates}）と対になるよう、 一覧からテーブル一覧へ戻る導線を設ける */
  public static String footer(BaseInfoEntity baseInfo) {
    return PagedSectionTemplates.backOnlyFooter(
        DocumentLocations.linkFromBase(
            DocumentLocations.listFile(ListDocumentType.TABLE, baseInfo.dbName())),
        ListDocumentType.TABLE.getBackLinkLabel());
  }
}
