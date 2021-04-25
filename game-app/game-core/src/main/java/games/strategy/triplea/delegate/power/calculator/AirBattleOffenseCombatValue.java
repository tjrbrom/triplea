package games.strategy.triplea.delegate.power.calculator;

import games.strategy.engine.data.Unit;
import games.strategy.triplea.delegate.battle.BattleState;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import org.triplea.java.collections.IntegerMap;

/**
 * Calculates offense strength and roll for Air Battle dice
 *
 * <p>Air Battles don't have support and are not affected by territory. The unit's offense strength
 * is determined by the airAttack attribute.
 */
@Builder
@Value
class AirBattleOffenseCombatValue implements CombatValue {

  @Nonnull Integer gameDiceSides;

  @Nonnull Boolean lhtrHeavyBombers;

  @Getter(onMethod_ = @Override)
  @Nonnull
  @Builder.Default
  Collection<Unit> friendUnits = List.of();

  @Getter(onMethod_ = @Override)
  @Nonnull
  @Builder.Default
  Collection<Unit> enemyUnits = List.of();

  @Override
  public RollCalculator getRoll() {
    return new AirBattleOffenseRoll();
  }

  @Override
  public StrengthCalculator getStrength() {
    return new AirBattleOffenseStrength(gameDiceSides);
  }

  @Override
  public BattleState.Side getBattleSide() {
    return BattleState.Side.OFFENSE;
  }

  @Override
  public int getDiceSides(final Unit unit) {
    return gameDiceSides;
  }

  @Override
  public boolean chooseBestRoll(final Unit unit) {
    return lhtrHeavyBombers || unit.getUnitAttachment().getChooseBestRoll();
  }

  @Override
  public CombatValue buildWithNoUnitSupports() {
    return AirBattleOffenseCombatValue.builder()
        .gameDiceSides(gameDiceSides)
        .lhtrHeavyBombers(lhtrHeavyBombers)
        .build();
  }

  @Override
  public CombatValue buildOppositeCombatValue() {
    return AirBattleDefenseCombatValue.builder()
        .gameDiceSides(gameDiceSides)
        .lhtrHeavyBombers(lhtrHeavyBombers)
        .build();
  }

  @Value
  static class AirBattleOffenseRoll implements RollCalculator {

    @Override
    public RollValue getRoll(final Unit unit) {
      return RollValue.of(unit.getUnitAttachment().getAttackRolls(unit.getOwner()));
    }

    @Override
    public Map<Unit, IntegerMap<Unit>> getSupportGiven() {
      return Map.of();
    }
  }

  @Value
  static class AirBattleOffenseStrength implements StrengthCalculator {

    int diceSides;

    @Override
    public StrengthValue getStrength(final Unit unit) {
      return StrengthValue.of(diceSides, unit.getUnitAttachment().getAirAttack(unit.getOwner()));
    }

    @Override
    public Map<Unit, IntegerMap<Unit>> getSupportGiven() {
      return Map.of();
    }
  }
}
