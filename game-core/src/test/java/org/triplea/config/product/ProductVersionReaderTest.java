package org.triplea.config.product;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import org.triplea.config.MemoryPropertyReader;
import org.triplea.config.product.ProductVersionReader.PropertyKeys;
import org.triplea.util.Version;

final class ProductVersionReaderTest {
  private final MemoryPropertyReader memoryPropertyReader = new MemoryPropertyReader();
  private final ProductVersionReader productVersionReader =
      new ProductVersionReader(memoryPropertyReader);

  @Test
  void getVersion() {
    memoryPropertyReader.setProperty(PropertyKeys.VERSION, "1.6.0");

    assertThat(productVersionReader.getVersion(), is(new Version(1, 6, 0)));
  }
}
