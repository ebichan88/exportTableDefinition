package com.export_table_definition.domain.service.snapshot;

import com.export_table_definition.domain.model.DiffResult;
import com.export_table_definition.domain.model.snapshot.SnapshotKind;
import com.export_table_definition.domain.repository.FileRepository;
import com.export_table_definition.domain.service.path.OutputPathResolver;
import com.google.inject.Inject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * DBから生成したスキーマのスナップショットと、既にコミット済みのスナップショットを比較するドメインサービス<br>
 * スキーマ配下のオブジェクトのファイル（{@code tables.jsonl}等）は1行1オブジェクトのため、行をオブジェクト単位で突き合わせ、 追加/削除/内容不一致をオブジェクト単位（例:
 * {@code table sample.employee}）で報告する。 それ以外のファイル（{@code database.json}等）はファイル単位で比較する
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class SnapshotDiffDomainService {

  /** 差分の対象の並び順（表示名順。同名の場合はファイル順） */
  private static final Comparator<Target> TARGET_ORDER =
      Comparator.comparing(Target::label).thenComparing(Target::file);

  private final FileRepository fileRepository;
  private final OutputPathResolver outputPathResolver;
  private final SnapshotSerializer serializer;

  /**
   * コンストラクタ
   *
   * @param fileRepository ファイルリポジトリ
   * @param outputPathResolver 出力パス解決クラス
   * @param serializer スナップショットのJSON変換を行うクラス
   */
  @Inject
  public SnapshotDiffDomainService(
      FileRepository fileRepository,
      OutputPathResolver outputPathResolver,
      SnapshotSerializer serializer) {
    this.fileRepository = fileRepository;
    this.outputPathResolver = outputPathResolver;
    this.serializer = serializer;
  }

  /**
   * 2つのスナップショットのディレクトリ配下を比較するメソッド
   *
   * @param generatedDir DBから生成したスナップショットのディレクトリ
   * @param committedDir 既にコミット済みのスナップショットのディレクトリ
   * @return 比較結果
   */
  public DiffResult compare(Path generatedDir, Path committedDir) {
    final Map<Target, List<String>> generated = index(generatedDir);
    final Map<Target, List<String>> committed = index(committedDir);
    return new DiffResult(
        labels(generated, target -> !committed.containsKey(target)),
        labels(committed, target -> !generated.containsKey(target)),
        labels(
            generated,
            target ->
                committed.containsKey(target)
                    && !generated.get(target).equals(committed.get(target))));
  }

  /**
   * ディレクトリ配下のスナップショットを、差分の対象（オブジェクトまたはファイル）ごとの内容へ分解するメソッド
   *
   * @param directory スナップショットのディレクトリ
   * @return 差分の対象をキー、その内容（行のリスト）を値とするマップ
   */
  private Map<Target, List<String>> index(Path directory) {
    final Map<Target, List<String>> targets = new LinkedHashMap<>();
    for (final Path file : fileRepository.listFiles(directory)) {
      final Path relativePath = directory.relativize(file);
      final List<String> lines = fileRepository.readFile(file);
      final Optional<SnapshotKind> kind = outputPathResolver.resolveSnapshotKind(relativePath);
      if (kind.isEmpty()) {
        targets.put(new Target(relativePath, relativePath.toString()), lines);
        continue;
      }
      lines.stream()
          .filter(line -> !line.isBlank())
          .forEach(
              line ->
                  targets
                      .computeIfAbsent(
                          new Target(relativePath, label(kind.get(), line)),
                          target -> new ArrayList<>())
                      .add(line));
    }
    return targets;
  }

  /**
   * スナップショットの1行から、差分の報告に用いるオブジェクトの表示名を組み立てるメソッド
   *
   * @param kind オブジェクトの種別
   * @param line スナップショットの1行
   * @return 表示名（例: {@code table sample.employee}）
   */
  private String label(SnapshotKind kind, String line) {
    return kind.getLabel() + " " + kind.identify(serializer.deserialize(line));
  }

  /**
   * 条件に一致する差分の対象の表示名を、並び順を揃えて抽出するメソッド
   *
   * @param targets 差分の対象をキーとするマップ
   * @param condition 抽出条件
   * @return 表示名のリスト
   */
  private List<String> labels(Map<Target, List<String>> targets, Predicate<Target> condition) {
    return targets.keySet().stream()
        .filter(condition)
        .sorted(TARGET_ORDER)
        .map(Target::label)
        .toList();
  }

  /**
   * 差分の対象（オブジェクトまたはファイル）<br>
   * 同名のオブジェクトが別のファイル（別DB等）に存在しても取り違えないよう、所属するファイルも含めて識別する
   *
   * @param file スナップショットのディレクトリからの相対パス
   * @param label 差分の報告に用いる表示名
   */
  private record Target(Path file, String label) {}
}
