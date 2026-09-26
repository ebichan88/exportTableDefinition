package com.export_table_definition.domain.service.path;

import com.export_table_definition.domain.model.entity.FunctionEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.type.ListDocumentType;

/**
 * 出力するMarkdownドキュメントの配置（ファイル名と、出力ベースディレクトリからの相対パス）を一元的に定めるクラス<br>
 * 出力先の絶対パス（{@link OutputPathResolver}）と、ドキュメント間の相対リンク（テンプレート）の双方が
 * このクラスの規則を参照することで、ファイル名を変更した場合にパスとリンクが食い違わないようにする。<br>
 * 配置は次のとおり。一覧・ER図は出力ベースディレクトリ直下に、テーブル定義書・関数等の個別定義書は {@code {DB名}/{スキーマ名}/{区分}/}配下に置く
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public final class DocumentLocations {

  private static final String MARKDOWN_EXTENSION = ".md";
  private static final String LIST_FILENAME_PATTERN = "%sList_%s" + MARKDOWN_EXTENSION;
  private static final String ER_DIAGRAM_FILENAME_PATTERN = "erDiagram_%s_%s" + MARKDOWN_EXTENSION;
  private static final String ER_DIAGRAM_GROUP_FILENAME_PATTERN =
      "erDiagram_%s_%s_group%d" + MARKDOWN_EXTENSION;
  private static final String PATH_SEPARATOR = "/";

  /** オーバーロードされた関数・プロシージャの個別定義ファイル名で、名前と番号を区切る文字 */
  private static final String OVERLOAD_SEPARATOR = "_";

  /** 出力ベースディレクトリ直下のドキュメントから、出力ベースディレクトリを指す相対パス */
  private static final String FROM_BASE = "./";

  /** 個別定義書（{@code {DB名}/{スキーマ名}/{区分}/}配下）から、出力ベースディレクトリを指す相対パス */
  private static final String FROM_DEFINITION = "../../../";

  /** コンストラクタ（インスタンス化不可） */
  private DocumentLocations() {}

  /**
   * 一覧ファイル名を取得するメソッド
   *
   * @param type 一覧の種別
   * @param dbName データベース名
   * @return {@code {接頭辞}List_{DB名}.md}
   */
  public static String listFile(ListDocumentType type, String dbName) {
    return String.format(LIST_FILENAME_PATTERN, type.getPrefix(), dbName);
  }

  /**
   * スキーマ別ER図のファイル名を取得するメソッド
   *
   * @param dbName データベース名
   * @param schemaName スキーマ名
   * @return {@code erDiagram_{DB名}_{スキーマ名}.md}
   */
  public static String erDiagramFile(String dbName, String schemaName) {
    return String.format(ER_DIAGRAM_FILENAME_PATTERN, dbName, schemaName);
  }

  /**
   * スキーマ別ER図をグループに分割した場合の、グループ別ER図のファイル名を取得するメソッド
   *
   * @param dbName データベース名
   * @param schemaName スキーマ名
   * @param groupNo グループ番号（1始まり）
   * @return {@code erDiagram_{DB名}_{スキーマ名}_group{グループ番号}.md}
   */
  public static String erDiagramGroupFile(String dbName, String schemaName, int groupNo) {
    return String.format(ER_DIAGRAM_GROUP_FILENAME_PATTERN, dbName, schemaName, groupNo);
  }

  /**
   * 行数の多い表を分割した場合の、分割ページのファイル名を取得するメソッド<br>
   * 分割ページは本体ページと同じディレクトリに置く
   *
   * @param fileName 本体ページのファイル名
   * @param pageIndex ページ番号（1始まり）
   * @return 本体ページのファイル名の拡張子の前に{@code _{ページ番号}}を付けたファイル名
   * @throws IllegalArgumentException 本体ページのファイル名がMarkdownの拡張子で終わらない場合
   */
  public static String pageFile(String fileName, int pageIndex) {
    if (!fileName.endsWith(MARKDOWN_EXTENSION)) {
      throw new IllegalArgumentException("Not a markdown file name: " + fileName);
    }
    return fileName.substring(0, fileName.length() - MARKDOWN_EXTENSION.length())
        + "_"
        + pageIndex
        + MARKDOWN_EXTENSION;
  }

  /**
   * テーブル定義書の、出力ベースディレクトリからの相対パスを取得するメソッド
   *
   * @param dbName データベース名
   * @param table テーブル情報
   * @return {@code {DB名}/{スキーマ名}/{テーブル区分}/{物理テーブル名}.md}
   */
  public static String tableDefinitionFile(String dbName, TableEntity table) {
    return String.join(
        PATH_SEPARATOR,
        dbName,
        table.schemaName(),
        table.tableType().getName(),
        table.physicalTableName() + MARKDOWN_EXTENSION);
  }

  /**
   * 関数・プロシージャの個別定義ファイル名（拡張子を除く）を取得するメソッド<br>
   * 同じスキーマに同名の関数・プロシージャ（オーバーロード）が複数存在する場合は、ファイル名が重複しないよう 作成順の番号を付ける（例: {@code calc_1}, {@code
   * calc_2}）。存在しない場合は関数・プロシージャ名をそのまま用いる
   *
   * @param function 関数・プロシージャ情報
   * @return 個別定義ファイル名（拡張子を除く）
   */
  public static String functionDefinitionName(FunctionEntity function) {
    if (!function.isOverloaded()) {
      return function.functionName();
    }
    return function.functionName() + OVERLOAD_SEPARATOR + function.overloadIndex();
  }

  /**
   * 関数・プロシージャ／シーケンス／ユーザー定義型の個別定義ファイルを置くディレクトリの、 出力ベースディレクトリからの相対パスを取得するメソッド
   *
   * @param dbName データベース名
   * @param schemaName スキーマ名
   * @param kind オブジェクトの区分（{@link ListDocumentType#FUNCTION}／{@link
   *     ListDocumentType#SEQUENCE}／{@link ListDocumentType#TYPE}）
   * @return {@code {DB名}/{スキーマ名}/{区分}}
   */
  public static String schemaObjectDirectory(
      String dbName, String schemaName, ListDocumentType kind) {
    return String.join(PATH_SEPARATOR, dbName, schemaName, kind.getPrefix());
  }

  /**
   * 関数・プロシージャ／シーケンス／ユーザー定義型の個別定義ファイルの、出力ベースディレクトリからの相対パスを取得するメソッド
   *
   * @param dbName データベース名
   * @param schemaName スキーマ名
   * @param kind オブジェクトの区分（{@link ListDocumentType#FUNCTION}／{@link
   *     ListDocumentType#SEQUENCE}／{@link ListDocumentType#TYPE}）
   * @param name 個別定義ファイル名（拡張子を除く）
   * @return {@code {DB名}/{スキーマ名}/{区分}/{名前}.md}
   */
  public static String schemaObjectFile(
      String dbName, String schemaName, ListDocumentType kind, String name) {
    return schemaObjectDirectory(dbName, schemaName, kind)
        + PATH_SEPARATOR
        + name
        + MARKDOWN_EXTENSION;
  }

  /**
   * 出力ベースディレクトリ直下のドキュメント（一覧・ER図）から、他のドキュメントを参照する相対リンクを取得するメソッド
   *
   * @param relativePath 参照先の、出力ベースディレクトリからの相対パス
   * @return 相対リンク（例: {@code ./tableList_testdb.md}）
   */
  public static String linkFromBase(String relativePath) {
    return FROM_BASE + relativePath;
  }

  /**
   * 個別定義書（テーブル定義書・関数等の個別定義）から、他のドキュメントを参照する相対リンクを取得するメソッド
   *
   * @param relativePath 参照先の、出力ベースディレクトリからの相対パス
   * @return 相対リンク（例: {@code ../../../tableList_testdb.md}）
   */
  public static String linkFromDefinition(String relativePath) {
    return FROM_DEFINITION + relativePath;
  }
}
