package com.export_table_definition.domain.repository;

import com.export_table_definition.domain.model.annotation.Annotations;

/**
 * 手動付帯情報（サイドカーYAML）の読み込みに関するリポジトリインターフェース
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public interface AnnotationRepository {

    /**
     * サイドカーの付帯情報を読み込むメソッド<br>
     * パスが未指定（null・空）の場合や、ファイルが存在しない場合は空の{@link Annotations}を返す
     *
     * @param annotationPath サイドカーファイルのパス（未指定可）
     * @return 読み込んだ付帯情報。無効な指定の場合は空のAnnotations
     */
    Annotations load(String annotationPath);
}
