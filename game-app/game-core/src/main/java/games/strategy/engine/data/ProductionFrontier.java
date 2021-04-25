package games.strategy.engine.data;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** A collection of {@link ProductionRule}s. */
public class ProductionFrontier extends DefaultNamed implements Iterable<ProductionRule> {
  private static final long serialVersionUID = -5967251608158552892L;

  private final List<ProductionRule> rules;
  private List<ProductionRule> cachedRules;

  public ProductionFrontier(final String name, final GameData data) {
    this(name, data, List.of());
  }

  public ProductionFrontier(
      final String name, final GameData data, final List<ProductionRule> rules) {
    super(name, data);

    checkNotNull(rules);

    this.rules = new ArrayList<>(rules);
  }

  public void addRule(final ProductionRule rule) {
    if (rules.contains(rule)) {
      throw new IllegalStateException("Rule already added:" + rule);
    }
    rules.add(rule);
    cachedRules = null;
  }

  public void removeRule(final ProductionRule rule) {
    if (!rules.contains(rule)) {
      throw new IllegalStateException("Rule not present:" + rule);
    }
    rules.remove(rule);
    cachedRules = null;
  }

  public List<ProductionRule> getRules() {
    if (cachedRules == null) {
      cachedRules = Collections.unmodifiableList(rules);
    }
    return cachedRules;
  }

  @Override
  public Iterator<ProductionRule> iterator() {
    return getRules().iterator();
  }
}
