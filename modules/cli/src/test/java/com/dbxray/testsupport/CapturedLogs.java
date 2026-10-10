package com.dbxray.testsupport;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/** 指定したクラスのロガーへ出力されたログを、テストで確かめられるよう記録するクラス（try-with-resourcesで使う） */
public final class CapturedLogs implements AutoCloseable {

  private final Logger logger;
  private final AbstractAppender appender;
  private final List<LogEvent> events = new CopyOnWriteArrayList<>();

  private CapturedLogs(Class<?> loggerClass) {
    logger = (Logger) LogManager.getLogger(loggerClass);
    appender =
        new AbstractAppender(
            "captured-" + loggerClass.getSimpleName(), null, null, true, Property.EMPTY_ARRAY) {
          @Override
          public void append(LogEvent event) {
            events.add(event.toImmutable());
          }
        };
    appender.start();
    logger.addAppender(appender);
  }

  /** 指定したクラスのロガーへの出力の記録を始めるメソッド */
  public static CapturedLogs of(Class<?> loggerClass) {
    return new CapturedLogs(loggerClass);
  }

  /**
   * 記録したログのうち、指定したレベルのもののメッセージを返すメソッド
   *
   * @return 出力された順のメッセージ（引数を埋め込んだ後のもの）
   */
  public List<String> messages(Level level) {
    return events.stream()
        .filter(event -> event.getLevel().equals(level))
        .map(event -> event.getMessage().getFormattedMessage())
        .toList();
  }

  /** {@inheritDoc} */
  @Override
  public void close() {
    logger.removeAppender(appender);
    appender.stop();
  }
}
