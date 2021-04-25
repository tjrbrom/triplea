package org.triplea.ai.flowfield.neighbors;

import games.strategy.engine.data.GameMap;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.UnitType;
import games.strategy.triplea.attachments.UnitAttachment;
import java.util.Collection;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.triplea.java.PredicateBuilder;

@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class NeighborGetter implements Function<Territory, Collection<Territory>> {

  UnitType unitType;
  GameMap gameMap;

  @Override
  public Collection<Territory> apply(final Territory territory) {
    final UnitAttachment unitAttachment = UnitAttachment.get(unitType);
    final PredicateBuilder<Territory> territoryPredicate = PredicateBuilder.trueBuilder();
    if (unitAttachment.getIsSea()) {
      territoryPredicate.and(Territory::isWater);
    } else if (!unitAttachment.getIsAir()) {
      territoryPredicate.and(Predicate.not(Territory::isWater));
    }
    return gameMap.getNeighbors(territory, territoryPredicate.build());
  }
}
