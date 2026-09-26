package com.export_table_definition.domain.repository;

import com.export_table_definition.domain.model.sidecar.Sidecar;

/**
 * サイドカーYAML（手動付帯情報・論理リレーション）の読み込みに関するリポジトリインターフェース
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public interface SidecarRepository {

  /**
   * サイドカーの内容を読み込むメソッド<br>
   * パスが未指定（null・空）の場合は空の{@link Sidecar}を返す。個々の記述の誤りは読み飛ばし、警告する
   *
   * @param sidecarPath サイドカーファイルのパス（未指定可）
   * @return 読み込んだサイドカーの内容。未指定の場合は空のSidecar
   * @throws com.export_table_definition.domain.UserCorrectableException ファイルが存在しない場合や、
   *     ファイルを解釈できない場合（YAMLの構文誤り等）
   */
  Sidecar load(String sidecarPath);
}
