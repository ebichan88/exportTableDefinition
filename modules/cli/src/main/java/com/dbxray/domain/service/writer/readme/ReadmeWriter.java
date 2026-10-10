package com.dbxray.domain.service.writer.readme;

import com.dbxray.domain.model.document.ListDocumentType;
import com.dbxray.domain.repository.FileRepository;
import com.dbxray.domain.service.path.OutputPathResolver;
import com.dbxray.domain.service.path.OutputRoot;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * データベース単位ディレクトリのREADMEを書き込むクラス<br>
 * 複数のデータベースを同じ出力先へ出力した場合に、GitHub等でそのディレクトリを開いた際の入り口となるよう、 出力される一覧ドキュメントへのリンクをまとめる
 */
public class ReadmeWriter {

  private final FileRepository fileRepository;
  private final OutputPathResolver outputPathResolver;

  @Inject
  public ReadmeWriter(FileRepository fileRepository, OutputPathResolver outputPathResolver) {
    this.fileRepository = fileRepository;
    this.outputPathResolver = outputPathResolver;
  }

  /**
   * READMEの書き込み処理を行うメソッド
   *
   * @param documents 出力する一覧ドキュメントの種別（掲載順。テーブル一覧は必ず含まれる）
   * @param outputRoot 出力先ベースディレクトリとデータベース基本情報
   */
  public void writeReadme(Set<ListDocumentType> documents, OutputRoot outputRoot) {
    final Path file = outputPathResolver.resolveReadmeFile(outputRoot);
    fileRepository.createDirectory(file.getParent());
    final List<String> contents =
        List.of(
            ReadmeTemplates.fileHeader(outputRoot.baseInfo()), // ヘッダー
            ReadmeTemplates.baseInfo(outputRoot.baseInfo()), // 基本情報
            ReadmeTemplates.documentLinks(outputRoot.baseInfo(), documents) // ドキュメント一覧
            );
    fileRepository.writeFile(file, contents);
  }
}
