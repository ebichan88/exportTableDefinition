package com.export_table_definition.domain.service.path;

import java.nio.file.Path;

import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.TableEntity;

/**
 * テーブル定義および一覧出力用のパス生成戦略インタフェース <br>
 * 物理レイアウト（ディレクトリ構造・ファイル命名規則）を抽象化する
 * 
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public interface OutputPathResolver {

    /**
     * テーブル定義書の出力ディレクトリを返す。 <br>
     * 例: {base}/{DB名}/{スキーマ名}/{テーブル種別}/
     * 
     * @param baseInfo 基本情報エンティティ
     * @param table テーブルエンティティ
     * @param baseOutputDir 基本出力ディレクトリ
     * @return テーブル定義書の出力ディレクトリパス
     */
    Path resolveTableDefinitionDirectory(BaseInfoEntity baseInfo, TableEntity table, Path baseOutputDir);

    /**
     * テーブル定義書の出力ファイルパスを返す。 <br>
     * 例: {base}/{DB名}/{スキーマ名}/{テーブル種別}/{物理テーブル名}.md
     * 
     * @param baseInfo 基本情報エンティティ
     * @param table テーブルエンティティ
     * @param baseOutputDir 基本出力ディレクトリ
     * @return テーブル定義書の出力ファイルパス
     */
    Path resolveTableDefinitionFile(BaseInfoEntity baseInfo, TableEntity table, Path baseOutputDir);

    /**
     * テーブル一覧（単一ファイルモード）のパス。 <br>
     * 例: {base}/tableList_{DB名}.md
     * 
     * @param baseInfo 基本情報エンティティ
     * @param baseOutputDir 基本出力ディレクトリ
     * @return テーブル一覧ファイルのパス
     */
    Path resolveTableListFile(BaseInfoEntity baseInfo, Path baseOutputDir);

    /**
     * テーブル一覧（分割ページモード）のパス。 <br>
     * 例: {base}/tableList_{DB名}_{pageIndex}.md
     *
     * @param baseInfo 基本情報エンティティ
     * @param baseOutputDir 基本出力ディレクトリ
     * @param pageIndex ページインデックス（1始まり）
     * @return テーブル一覧ファイルのパス
     */
    Path resolveTableListFile(BaseInfoEntity baseInfo, Path baseOutputDir, int pageIndex);

    /**
     * オブジェクト一覧（トリガー/関数/シーケンス/型）のパス。 <br>
     * 例: {base}/{prefix}List_{DB名}.md
     *
     * @param baseInfo      基本情報エンティティ
     * @param baseOutputDir 基本出力ディレクトリ
     * @param prefix        一覧ファイル名の接頭辞（例: trigger, function, sequence, type）
     * @return オブジェクト一覧ファイルのパス
     */
    Path resolveObjectListFile(BaseInfoEntity baseInfo, Path baseOutputDir, String prefix);

    /**
     * スキーマ配下オブジェクト（関数/シーケンス/型）の出力ディレクトリを返す。 <br>
     * 例: {base}/{DB名}/{スキーマ名}/{kind}/
     *
     * @param baseInfo      基本情報エンティティ
     * @param baseOutputDir 基本出力ディレクトリ
     * @param schemaName    スキーマ名
     * @param kind          オブジェクト種別ディレクトリ名（例: function, sequence, type）
     * @return 出力ディレクトリパス
     */
    Path resolveSchemaObjectDirectory(BaseInfoEntity baseInfo, Path baseOutputDir, String schemaName, String kind);

    /**
     * スキーマ配下オブジェクト（関数/シーケンス/型）の出力ファイルパスを返す。 <br>
     * 例: {base}/{DB名}/{スキーマ名}/{kind}/{name}.md
     *
     * @param baseInfo      基本情報エンティティ
     * @param baseOutputDir 基本出力ディレクトリ
     * @param schemaName    スキーマ名
     * @param kind          オブジェクト種別ディレクトリ名（例: function, sequence, type）
     * @param name          ファイル名（拡張子を除く）
     * @return 出力ファイルパス
     */
    Path resolveSchemaObjectFile(BaseInfoEntity baseInfo, Path baseOutputDir, String schemaName, String kind,
            String name);
}