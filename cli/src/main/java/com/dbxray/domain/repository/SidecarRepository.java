package com.dbxray.domain.repository;

import com.dbxray.domain.model.sidecar.Sidecar;

/** サイドカーYAML（手動付帯情報・論理リレーション・観点）の読み込みに関するリポジトリインターフェース */
public interface SidecarRepository {

  /**
   * サイドカーの内容を読み込むメソッド<br>
   * パスが未指定（null・空）の場合は空の{@link Sidecar}を返す。個々の記述の誤りは読み飛ばし、警告する
   *
   * @param sidecarPath サイドカーファイルのパス（未指定可）
   * @return 読み込んだサイドカーの内容。未指定の場合は空のSidecar
   * @throws com.dbxray.shared.exception.UserCorrectableException ファイルが存在しない場合や、
   *     ファイルを解釈できない場合（YAMLの構文誤り等）
   */
  Sidecar load(String sidecarPath);
}
