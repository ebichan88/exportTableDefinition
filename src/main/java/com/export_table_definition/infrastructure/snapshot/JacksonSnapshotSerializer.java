package com.export_table_definition.infrastructure.snapshot;

import com.export_table_definition.domain.service.snapshot.SnapshotSerializer;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;

/**
 * Jacksonを用いた{@link SnapshotSerializer}の実装クラス<br>
 * recordはコンポーネントの宣言順に出力されるため、同じ内容からは常に同じ文字列が得られる。 日本語等の非ASCII文字はエスケープせずそのまま出力する（git diffでの可読性のため）
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class JacksonSnapshotSerializer implements SnapshotSerializer {

  private final ObjectMapper objectMapper =
      new ObjectMapper().setDefaultPropertyInclusion(JsonInclude.Include.NON_EMPTY);

  /** {@inheritDoc} */
  @Override
  public String serialize(Object snapshot) {
    try {
      return objectMapper.writeValueAsString(snapshot);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }
}
