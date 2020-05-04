package games.strategy.triplea.delegate.battle.casualty.power.calculator;

import games.strategy.engine.data.UnitType;
import games.strategy.triplea.attachments.UnitSupportAttachment;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Singular;
import lombok.ToString;
import lombok.Value;
import org.triplea.performance.PerfTimer;

@Builder
public class SupportCalculator {
  @Nonnull private final Boolean defending;

  @Singular private final Collection<UnitData> units;

  @Singular private final Collection<UnitData> enemyUnits;

  @Nonnull private final Collection<UnitSupportAttachment> supportAttachments;

  @Value
  @Builder
  public static class UnitData {
    private final UnitType type;
    private final int count;
    private final int power;
    @Builder.Default private int rolls = 1;
  }

  @ToString
  @RequiredArgsConstructor
  @Builder
  private static class UnitBuffs {
    @Nonnull private final UnitType type;
    @Nonnull private Integer power;
    @Nonnull private Integer rolls;
  }

  Map<UnitType, Integer> groupUnitCountsByType(final Collection<UnitData> units) {
    final Map<UnitType, Integer> unitCounts = new HashMap<>();
    for (final UnitData unitData : units) {
      unitCounts.put(unitData.type, unitData.count);
    }

    return unitCounts;
  }

  int computeSupportBonus() {
    Map<UnitType, Integer> alliedUnitCounts = groupUnitCountsByType(units);
    Map<UnitType, Integer> enemyUnitCounts = groupUnitCountsByType(enemyUnits);

    // strength buffs
    final ApplicableSupportRules applicableSupportRules =
        ApplicableSupportRules.builder()
            .defending(defending)
            .allyData(units)
            .enemyUnitData(enemyUnits)
            .supportAttachments(supportAttachments)
            .build();

    final Map<UnitType, List<UnitBuffs>> unitBuffs;
    try (PerfTimer timer = PerfTimer.startTimer("computInitialBuffs")) {
      unitBuffs = computeInitialBuffs(units);
    }
    try (PerfTimer timer = PerfTimer.startTimer("everything else")) {

      final Map<UnitType, List<UnitBuffs>> strengthBuffs =
          computeBuffs(
              alliedUnitCounts,
              unitBuffs,
              (buff, bonus) -> buff.power += bonus,
              applicableSupportRules.getStrengthSupportRules(),
              BuffType.ALLIED);

      final Map<UnitType, List<UnitBuffs>> enemyModifiedStrengthBuffs =
          computeBuffs(
              enemyUnitCounts,
              strengthBuffs,
              (buff, bonus) -> buff.power += bonus,
              applicableSupportRules.getEnemyStrengthSupportRules(),
              BuffType.ENEMY);

      final Map<UnitType, List<UnitBuffs>> rollBuffs =
          computeBuffs(
              alliedUnitCounts,
              enemyModifiedStrengthBuffs,
              (buff, bonus) -> buff.rolls += bonus,
              applicableSupportRules.getDiceRollSupportRules(),
              BuffType.ALLIED);

      final Map<UnitType, List<UnitBuffs>> enemyModifiedRollBuffs =
          computeBuffs(
              enemyUnitCounts,
              rollBuffs,
              (buff, bonus) -> buff.rolls += bonus,
              applicableSupportRules.getEnemyDiceRollSupportRules(),
              BuffType.ENEMY);

      return enemyModifiedRollBuffs.values().stream()
          .flatMap(Collection::stream)
          .mapToInt(
              buff -> {
                final int power = Math.max(0, buff.power);
                final int rolls = Math.max(0, buff.rolls);
                return power * rolls;
              })
          .sum();
    }

    // TODO: document that we are assuming that added rolls will be applied once
    // TODO: document that we are assuming that added support will also apply
    // TODO: document we are doing a best fit
    //    eg: support arty and infantry
    //         and another supports only infantry
    //         if the arty & infantry is applied to infantry, then out of luck.
  }

  /** Returns zero'd out list of buffs */
  private Map<UnitType, List<UnitBuffs>> computeInitialBuffs(final Collection<UnitData> units) {
    final Map<UnitType, List<UnitBuffs>> buffs = new HashMap<>();
    for (final UnitData unitData : units) {
      buffs.put(unitData.type, new ArrayList<>(units.size()));
      for (int i = 0; i < unitData.count; i++) {
        buffs
            .get(unitData.type)
            .add(
                UnitBuffs.builder()
                    .type(unitData.type)
                    .power(unitData.power)
                    .rolls(unitData.rolls)
                    .build());
      }
    }
    return buffs;
  }

  private enum BuffType {
    ALLIED,
    ENEMY
  }

  private static Map<UnitType, List<UnitBuffs>> computeBuffs(
      final Map<UnitType, Integer> supportingUnitCounts,
      final Map<UnitType, List<UnitBuffs>> existingUnitBuffs,
      final BiConsumer<UnitBuffs, Integer> buffMutation,
      final Collection<UnitSupportAttachment> attachments,
      final BuffType buffType) {

    final List<UnitSupportAttachment> supportAttachments = new ArrayList<>(attachments.size());
    for (final UnitSupportAttachment unitSupportAttachment : attachments) {
      UnitType supportingUnitType = (UnitType) unitSupportAttachment.getAttachedTo();
      int unitCount =
          supportingUnitCounts.containsKey(supportingUnitType)
              ? supportingUnitCounts.get(supportingUnitType)
              : 0;
      int supportCount = unitSupportAttachment.getBonusType().getCount();
      int totalSupports = unitCount * supportCount;
      for (int i = 0; i < totalSupports; i++) {
        supportAttachments.add(unitSupportAttachment);
      }
    }

    final Comparator<UnitSupportAttachment> comparator =
        Comparator.comparingInt(UnitSupportAttachment::getBonus);

    // for enemies, get the smallest (most negative) buff.
    supportAttachments.sort(buffType == BuffType.ALLIED ? comparator.reversed() : comparator);

    // the list of units that have received a buff
    final Map<UnitType, List<UnitBuffs>> buffedUnits = new HashMap<>(existingUnitBuffs.size());
    for (final Map.Entry<UnitType, List<UnitBuffs>> entry : existingUnitBuffs.entrySet()) {
      buffedUnits.put(entry.getKey(), new ArrayList<>(entry.getValue()));
    }

    int totalUnits = existingUnitBuffs.values().stream().mapToInt(List::size).sum();

    for (int i = 0; i < Math.min(totalUnits, supportAttachments.size()); i++) {
      final UnitSupportAttachment supportAttachment = supportAttachments.get(i);

      for (final UnitType possibleSupport : supportAttachment.getUnitType()) {
        if (existingUnitBuffs.containsKey(possibleSupport)) {
          if (existingUnitBuffs.get(possibleSupport).isEmpty()) {
            existingUnitBuffs.remove(possibleSupport);
            continue;
          }
          final UnitBuffs buff = existingUnitBuffs.get(possibleSupport).remove(0);
          buffMutation.accept(buff, supportAttachment.getBonus());
          break;
        }
      }
    }
    return buffedUnits;
  }

  @Getter
  private static class ApplicableSupportRules {
    final Collection<UnitSupportAttachment> strengthSupportRules = new ArrayList<>();
    final Collection<UnitSupportAttachment> diceRollSupportRules = new ArrayList<>();
    final Collection<UnitSupportAttachment> enemyStrengthSupportRules = new ArrayList<>();
    final Collection<UnitSupportAttachment> enemyDiceRollSupportRules = new ArrayList<>();

    // map of units that can receive support to the count of how many of those units we have.
    final Map<UnitType, Integer> supportedUnits = new HashMap<>();

    @Builder
    ApplicableSupportRules(
        final boolean defending,
        final Collection<UnitSupportAttachment> supportAttachments,
        final Collection<UnitData> allyData,
        final Collection<UnitData> enemyUnitData) {

      final Collection<UnitType> enemies =
          enemyUnitData.stream().map(unitData -> unitData.type).collect(Collectors.toSet());

      final Map<UnitType, UnitData> allies =
          allyData.stream()
              .collect(Collectors.toMap(unitData -> unitData.type, Function.identity()));

      for (final UnitSupportAttachment supportAttachment : supportAttachments) {
        // support attachment can be both defending and offense, skip if it is neither
        // or if it provides a support
        if (supportAttachment.getDefence() != defending
            && supportAttachment.getOffence() == defending) {
          continue;
        }
        final UnitType supportingUnit = (UnitType) supportAttachment.getAttachedTo();
        if (!allies.containsKey(supportingUnit) && !enemies.contains(supportingUnit)) {
          continue;
        }

        // check if we have a supported unit present
        boolean supportedUnitFound = false;
        for (final UnitType supportedUnit : supportAttachment.getUnitType()) {
          if (allies.containsKey(supportedUnit)) {
            supportedUnitFound = true;
            supportedUnits.put(supportedUnit, allies.get(supportedUnit).count);
            break;
          }
        }
        if (!supportedUnitFound) {
          continue;
        }

        if (supportAttachment.getStrength() && supportAttachment.getAllied()) {
          strengthSupportRules.add(supportAttachment);
        }
        if (supportAttachment.getStrength() && supportAttachment.getEnemy()) {
          enemyStrengthSupportRules.add(supportAttachment);
        }
        if (supportAttachment.getRoll() && supportAttachment.getAllied()) {
          diceRollSupportRules.add(supportAttachment);
        }
        if (supportAttachment.getRoll() && supportAttachment.getEnemy()) {
          enemyDiceRollSupportRules.add(supportAttachment);
        }
      }
    }
  }
}
