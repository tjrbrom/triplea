package games.strategy.triplea.delegate.battle.casualty.power.calculator;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.UnitType;
import games.strategy.engine.data.UnitType.CombatModifiers;
import games.strategy.triplea.attachments.UnitAttachment;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.Builder;

@Builder
public class StrengthCalculator {
  @Nonnull private final GameData gameData;
  @Nonnull private final Map<UnitType, Integer> unitCounts;
  @Nonnull private final Map<UnitType, Map<GamePlayer, Integer>> enemyUnits;
  @Nonnull private final GamePlayer gamePlayer;
  @Nonnull private final CombatModifiers combatModifiers;

  public int totalPower() {
    return SupportCalculator.builder()
        .units(computeUnitData())
        .enemyUnits(computeEnemyData())
        .defending(combatModifiers.isDefending())
        .supportAttachments(gameData.getUnitTypeList().getSupportRules())
        .build()
        .computeSupportBonus();
  }

  private Collection<SupportCalculator.UnitData> computeUnitData() {
    return unitCounts.entrySet().stream()
        .map(
            entry ->
                SupportCalculator.UnitData.builder()
                    .type(entry.getKey())
                    .count(entry.getValue())
                    .power(entry.getKey().getStrength(gamePlayer, combatModifiers))
                    .rolls(
                        combatModifiers.isDefending()
                            ? UnitAttachment.get(entry.getKey()).getDefenseRolls(gamePlayer)
                            : UnitAttachment.get(entry.getKey()).getAttackRolls(gamePlayer))
                    .build())
        .collect(Collectors.toList());
  }

  private Collection<SupportCalculator.UnitData> computeEnemyData() {
    return enemyUnits.entrySet().stream()
        .flatMap(
            typeToPlayerUnitCounts ->
                typeToPlayerUnitCounts.getValue().entrySet().stream()
                    .map(
                        playerToCountEntry -> {
                          final var unitType = typeToPlayerUnitCounts.getKey();
                          final var unitCount = playerToCountEntry.getValue();
                          final var player = playerToCountEntry.getKey();
                          final var rolls =
                              combatModifiers.isDefending()
                                  ? UnitAttachment.get(unitType).getDefenseRolls(player)
                                  : UnitAttachment.get(unitType).getAttackRolls(player);

                          return SupportCalculator.UnitData.builder()
                              .type(unitType)
                              .count(unitCount)
                              .power(unitType.getStrength(player, combatModifiers))
                              .rolls(rolls)
                              .build();
                        })
                    .collect(Collectors.toList())
                    .stream())
        .collect(Collectors.toList());
  }
}
