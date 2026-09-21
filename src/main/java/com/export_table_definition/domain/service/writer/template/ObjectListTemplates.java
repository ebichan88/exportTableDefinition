package com.export_table_definition.domain.service.writer.template;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.FunctionEntity;
import com.export_table_definition.domain.model.entity.SequenceEntity;
import com.export_table_definition.domain.model.entity.TriggerEntity;
import com.export_table_definition.domain.model.entity.TypeEntity;

/**
 * トリガー・関数/プロシージャ・シーケンス・ユーザー定義型の一覧書き込みに利用する
 * Markdownのテンプレートを扱うクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class ObjectListTemplates {
    private static final String LINE_SEPARATOR = System.lineSeparator();
    private static final String LINE_SEPARATOR_DOUBLE = LINE_SEPARATOR + LINE_SEPARATOR;

    /**
     * 一覧ファイルヘッダー
     *
     * @param title    一覧のタイトル
     * @param baseInfo データベース基本情報
     * @return ヘッダー文字列
     */
    public static String fileHeader(String title, BaseInfoEntity baseInfo) {
        return "# " + String.format("%s（DB名：%s）", title, baseInfo.dbName()) + LINE_SEPARATOR_DOUBLE;
    }

    /**
     * 基本情報セクション
     *
     * @param baseInfo データベース基本情報
     * @return 基本情報セクション文字列
     */
    public static String baseInfo(BaseInfoEntity baseInfo) {
        return """
                ## 基本情報

                | RDBMS | データベース名 | 作成日 |
                |:---|:---|:---|
                """ + baseInfo.baseInfo() + LINE_SEPARATOR_DOUBLE;
    }

    /**
     * トリガー一覧セクション
     *
     * @param triggers トリガー情報のリスト
     * @return トリガー一覧セクション文字列
     */
    public static String triggerList(List<TriggerEntity> triggers) {
        String header = """
                ## トリガー一覧

                | No. | スキーマ名 | テーブル名 | トリガー名 | タイミング | イベント | 実行関数 |
                |:---|:---|:---|:---|:---|:---|:---|
                """;
        return listSection(triggers, header, TriggerEntity::triggerListInfo);
    }

    /**
     * 関数・プロシージャ一覧セクション
     *
     * @param functions 関数・プロシージャ情報のリスト
     * @return 関数・プロシージャ一覧セクション文字列
     */
    public static String functionList(List<FunctionEntity> functions) {
        String header = """
                ## 関数・プロシージャ一覧

                | No. | スキーマ名 | 種別 | 名前 | 引数 | 戻り値 | 言語 | Link |
                |:---|:---|:---|:---|:---|:---|:---|:---|
                """;
        return listSection(functions, header, FunctionEntity::functionListInfo);
    }

    /**
     * シーケンス一覧セクション
     *
     * @param sequences シーケンス情報のリスト
     * @return シーケンス一覧セクション文字列
     */
    public static String sequenceList(List<SequenceEntity> sequences) {
        String header = """
                ## シーケンス一覧

                | No. | スキーマ名 | シーケンス名 | 増分 | 最小値 | 最大値 | キャッシュ | 開始値 | 循環 | 所有カラム | Link |
                |:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|
                """;
        return listSection(sequences, header, SequenceEntity::sequenceListInfo);
    }

    /**
     * ユーザー定義型一覧セクション
     *
     * @param types ユーザー定義型情報のリスト
     * @return ユーザー定義型一覧セクション文字列
     */
    public static String typeList(List<TypeEntity> types) {
        String header = """
                ## ユーザー定義型一覧

                | No. | スキーマ名 | 型名 | 種別 | 定義 | Link |
                |:---|:---|:---|:---|:---|:---|
                """;
        return listSection(types, header, TypeEntity::typeListInfo);
    }

    /**
     * 一覧セクションを生成する共通メソッド
     *
     * @param <T>        エンティティの型
     * @param list       エンティティのリスト
     * @param header     セクションのヘッダー文字列
     * @param infoMapper エンティティから一覧行文字列を生成する関数
     * @return 一覧セクション文字列
     */
    private static <T> String listSection(List<T> list, String header, Function<T, String> infoMapper) {
        return header + list.stream().map(infoMapper).collect(Collectors.joining(LINE_SEPARATOR)) + LINE_SEPARATOR_DOUBLE;
    }
}
