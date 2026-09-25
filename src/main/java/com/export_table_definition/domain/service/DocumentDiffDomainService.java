package com.export_table_definition.domain.service;

import com.export_table_definition.domain.model.DiffResult;
import com.export_table_definition.domain.repository.FileRepository;
import com.google.inject.Inject;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * DBから生成したドキュメントと、既にコミット済みのドキュメントをファイル単位で比較するドメインサービス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class DocumentDiffDomainService {

  private final FileRepository fileRepository;

  /**
   * コンストラクタ
   *
   * @param fileRepository ファイルリポジトリ
   */
  @Inject
  public DocumentDiffDomainService(FileRepository fileRepository) {
    this.fileRepository = fileRepository;
  }

  /**
   * 2つのディレクトリ配下のファイルを比較するメソッド<br>
   * 比較は「同じツールが同じロジックで生成した2つのMarkdown」同士を前提とし、 ファイル単位の追加/削除/内容不一致の判定のみを行う
   *
   * @param generatedDir DBから生成したドキュメントのディレクトリ
   * @param committedDir 既にコミット済みのドキュメントのディレクトリ
   * @return 比較結果
   */
  public DiffResult compare(Path generatedDir, Path committedDir) {
    final Map<Path, Path> generatedFiles =
        relativize(generatedDir, fileRepository.listFiles(generatedDir));
    final Map<Path, Path> committedFiles =
        relativize(committedDir, fileRepository.listFiles(committedDir));

    final List<Path> onlyInGenerated =
        generatedFiles.keySet().stream()
            .filter(relativePath -> !committedFiles.containsKey(relativePath))
            .sorted()
            .toList();
    final List<Path> onlyInCommitted =
        committedFiles.keySet().stream()
            .filter(relativePath -> !generatedFiles.containsKey(relativePath))
            .sorted()
            .toList();
    final List<Path> contentDiffer =
        generatedFiles.keySet().stream()
            .filter(committedFiles::containsKey)
            .filter(
                relativePath ->
                    !fileRepository
                        .readFile(generatedFiles.get(relativePath))
                        .equals(fileRepository.readFile(committedFiles.get(relativePath))))
            .sorted()
            .toList();

    return new DiffResult(onlyInGenerated, onlyInCommitted, contentDiffer);
  }

  /**
   * ファイルパスのリストを、ベースディレクトリからの相対パスをキーとするマップへ変換するメソッド
   *
   * @param baseDir 相対化の基準ディレクトリ
   * @param files ファイルパスのリスト
   * @return 相対パスをキー、元のパス（絶対パス）を値とするマップ
   */
  private Map<Path, Path> relativize(Path baseDir, List<Path> files) {
    return files.stream()
        .collect(
            Collectors.toMap(
                baseDir::relativize, Function.identity(), (a, b) -> a, LinkedHashMap::new));
  }
}
