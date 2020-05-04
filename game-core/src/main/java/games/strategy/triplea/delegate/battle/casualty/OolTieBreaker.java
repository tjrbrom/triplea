package games.strategy.triplea.delegate.battle.casualty;

import games.strategy.triplea.attachments.UnitAttachment;
import games.strategy.triplea.delegate.battle.casualty.power.model.UnitTypeByPlayer;
import java.util.Collection;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Builder;

/**
 * Assuming we have multiple unit types all with the same effective combat power, this function will
 * decide which one is the best to choose for a casualty.
 */
@Builder
public class OolTieBreaker implements Function<Collection<UnitTypeByPlayer>, UnitTypeByPlayer> {

  @Override
  public UnitTypeByPlayer apply(final Collection<UnitTypeByPlayer> unitTypes) {
    return unitTypes.stream()
        .sorted(Comparator.comparingInt(OolTieBreaker::totalAttackAndDefensivePower))
        .collect(Collectors.toList())
        .iterator()
        .next();
  }

  private static int totalAttackAndDefensivePower(final UnitTypeByPlayer unitTypeByPlayer) {
    final var unitAttachment = UnitAttachment.get(unitTypeByPlayer.getUnitType());
    return unitAttachment.getAttack(unitTypeByPlayer.getGamePlayer())
        + unitAttachment.getDefense(unitTypeByPlayer.getGamePlayer());
  }
}
