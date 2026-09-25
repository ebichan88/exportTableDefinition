package com.export_table_definition.infrastructure.file;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * テーブル定義書き込み用のBufferedWriterを扱うクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class TableDefinitionBufferedWriter implements AutoCloseable {

  private final BufferedWriter bw;

  /**
   * コンストラクタ
   *
   * @param path 書き込み先のファイルパス
   */
  public TableDefinitionBufferedWriter(Path path) {
    this(path, StandardCharsets.UTF_8);
  }

  /**
   * コンストラクタ
   *
   * @param path 書き込み先のファイルパス
   * @param charset 文字エンコード
   */
  public TableDefinitionBufferedWriter(Path path, Charset charset) {
    this(path, charset, false);
  }

  /**
   * コンストラクタ
   *
   * @param path 書き込み先のファイルパス
   * @param charset 文字エンコード
   * @param append trueの場合は既存ファイルの末尾へ追記し、falseの場合は上書きする
   */
  public TableDefinitionBufferedWriter(Path path, Charset charset, boolean append) {
    try {
      this.bw =
          new BufferedWriter(
              new OutputStreamWriter(new FileOutputStream(path.toFile(), append), charset));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * 書き込み処理を行うメソッド<br>
   * {@link BufferedWriter#write(String)}をラップし、例外時にUncheckedIOExceptionをスローする
   *
   * @param content 書き込む内容
   */
  public void write(String content) {
    try {
      bw.write(content);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** クローズ処理を行うメソッド */
  @Override
  public void close() {
    try {
      bw.close();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
