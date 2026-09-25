package com.export_table_definition.domain.repository;

import java.nio.file.Path;
import java.util.List;

/**
 * ファイル操作に関するリポジトリインターフェース
 * 
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public interface FileRepository {
    /**
     * ファイルに書き込むメソッド
     * 
     * @param filePath 書き込み先のファイルパス
     * @param contents 書き込む内容のリスト
     */
    public void writeFile(Path filePath, List<String> contents);
    
    /**
     * ディレクトリを作成するメソッド
     *
     * @param filePath 作成するディレクトリのパス
     */
    public void createDirectory(Path filePath);

    /**
     * ディレクトリ配下のファイルを再帰的に列挙するメソッド
     *
     * @param directory 列挙対象のディレクトリのパス
     * @return ディレクトリ配下に存在する全ファイルのパスのリスト。ディレクトリが存在しない場合は空リスト
     */
    public List<Path> listFiles(Path directory);

    /**
     * ファイルを行リストとして読み込むメソッド（{@link #writeFile(Path, List)}の逆）
     *
     * @param filePath 読み込み対象のファイルパス
     * @return ファイルの内容を1行ずつ格納したリスト
     */
    public List<String> readFile(Path filePath);

    /**
     * 一時ディレクトリを作成するメソッド
     *
     * @param prefix 一時ディレクトリ名の接頭辞
     * @return 作成した一時ディレクトリのパス
     */
    public Path createTempDirectory(String prefix);

    /**
     * ディレクトリを配下のファイルごと再帰的に削除するメソッド<br>
     * ディレクトリが存在しない場合は何もしない
     *
     * @param directory 削除対象のディレクトリ
     */
    public void deleteDirectory(Path directory);

}
