package org.triplea.java.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Scheduler;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import lombok.Builder;

/**
 * Cache that expires values when a TTL (time to live) expires. TTL timer starts when the value is
 * written and is renewed if the value is 'refreshed'. The cache will reliably invoke a
 * 'removeListener' at least once when cache items are removed or expired.
 *
 * @param <IdT> Type that identifies the keys of the map.
 * @param <ValueT> Type that is placed as a value in the map.
 */
public class ExpiringAfterWriteCache<IdT, ValueT> implements TtlCache<IdT, ValueT> {

  private final Cache<IdT, ValueT> cache;
  private final BiConsumer<IdT, ValueT> removalListener;

  @Builder
  public ExpiringAfterWriteCache(
      final long duration, final TimeUnit timeUnit, final BiConsumer<IdT, ValueT> removalListener) {
    cache =
        Caffeine.newBuilder()
            .expireAfterWrite(duration, timeUnit)
            .scheduler(Scheduler.systemScheduler())
            .removalListener(
                (IdT key, ValueT value, RemovalCause cause) -> {
                  if (cause == RemovalCause.EXPIRED || cause == RemovalCause.EXPLICIT) {
                    removalListener.accept(key, value);
                  }
                })
            .build();

    this.removalListener = removalListener;
  }

  @Override
  public boolean refresh(final IdT id) {
    final Optional<ValueT> value = get(id);

    if (value.isPresent()) {
      put(id, value.get());
      return true;
    }

    return false;
  }

  @Override
  public Optional<ValueT> get(final IdT id) {
    return Optional.ofNullable(cache.getIfPresent(id));
  }

  @Override
  public void put(final IdT id, final ValueT value) {
    cache.put(id, value);
  }

  @Override
  public Optional<ValueT> invalidate(final IdT id) {
    final Optional<ValueT> value = Optional.ofNullable(cache.asMap().remove(id));
    value.ifPresent(valueT -> removalListener.accept(id, valueT));
    return value;
  }

  @Override
  public Optional<ValueT> replace(final IdT id, final ValueT newValue) {
    return Optional.ofNullable(cache.asMap().replace(id, newValue));
  }

  @Override
  public Map<IdT, ValueT> asMap() {
    return Map.copyOf(cache.asMap());
  }
}
