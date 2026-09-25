package com.export_table_definition.domain.model.annotation;

import java.util.List;

import com.export_table_definition.domain.model.entity.ForeignKeyEntity;

/**
 * サイドカーYAMLから読み込んだ内容全体を保持するrecordクラス<br>
 * サイドカーには性質の異なる2種類の情報が含まれる。
 * <ul>
 * <li>{@link Annotations}：テーブル単位の手動付帯情報（説明・備考・カラム備考）。
 * DBのメタ情報に「文章を足す」もので、テーブル定義書の各セルへマージされる</li>
 * <li>論理リレーション：DBに外部キー制約が存在しないテーブル間の関連。
 * 「関連という構造を足す」もので、ER図と専用セクションへ反映される</li>
 * </ul>
 *
 * @param annotations       テーブル単位の手動付帯情報
 * @param logicalRelations  サイドカーYAMLで宣言された論理リレーションのリスト
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record Sidecar(Annotations annotations, List<ForeignKeyEntity> logicalRelations) {

    /**
     * コンパクトコンストラクタ<br>
     * null安全のため、各値がnullの場合は空の状態に正規化する
     */
    public Sidecar {
        annotations = annotations == null ? Annotations.empty() : annotations;
        logicalRelations = logicalRelations == null ? List.of() : List.copyOf(logicalRelations);
    }

    /**
     * 内容を持たない空のインスタンスを生成する静的ファクトリメソッド<br>
     * サイドカー機能が無効（未設定）の場合やファイルが存在しない場合に利用する
     *
     * @return 空のSidecarインスタンス
     */
    public static Sidecar empty() {
        return new Sidecar(Annotations.empty(), List.of());
    }
}
