package games.strategy.triplea.delegate;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.TerritoryEffect;
import games.strategy.engine.data.Unit;
import games.strategy.engine.data.UnitType;
import games.strategy.engine.delegate.IDelegateBridge;
import games.strategy.engine.random.IRandomStats.DiceType;
import games.strategy.triplea.Properties;
import games.strategy.triplea.attachments.UnitAttachment;
import games.strategy.triplea.delegate.Die.DieType;
import games.strategy.triplea.delegate.battle.AirBattle;
import games.strategy.triplea.delegate.dice.TotalPowerAndTotalRolls;
import games.strategy.triplea.formatter.MyFormatter;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Used to store information about a dice roll.
 *
 * <p># of rolls at 5, at 4, etc.
 *
 * <p>Externalizable so we can efficiently write out our dice as ints rather than as full objects.
 */
public class DiceRoll implements Externalizable {
  private static final long serialVersionUID = -1167204061937566271L;
  private List<Die> rolls;
  // this does not need to match the Die with isHit true since for low luck we get many hits with
  // few dice
  private int hits;
  private double expectedHits;

  /**
   * Initializes a new instance of the DiceRoll class.
   *
   * @param dice the dice, 0 based
   * @param hits the number of hits
   * @param rollAt what we roll at, [0,Constants.MAX_DICE]
   * @param hitOnlyIfEquals Do we get a hit only if we are equals, or do we hit when we are equal or
   *     less than for example a 5 is a hit when rolling at 6 for equal and less than, but is not
   *     for equals.
   */
  public DiceRoll(
      final int[] dice, final int hits, final int rollAt, final boolean hitOnlyIfEquals) {
    this.hits = hits;
    expectedHits = 0;
    rolls =
        Arrays.stream(dice)
            .mapToObj(
                element -> {
                  final boolean hit = hitOnlyIfEquals ? (rollAt == element) : element <= rollAt;
                  return new Die(element, rollAt, hit ? DieType.HIT : DieType.MISS);
                })
            .collect(Collectors.toList());
  }

  // only for externalizable
  public DiceRoll() {}

  private DiceRoll(final List<Die> dice, final int hits, final double expectedHits) {
    rolls = new ArrayList<>(dice);
    this.hits = hits;
    this.expectedHits = expectedHits;
  }

  /** Used only for rolling SBR or fly over AA as they don't currently take into account support. */
  public static DiceRoll rollSbrOrFlyOverAa(
      final Collection<Unit> validTargets,
      final Collection<Unit> aaUnits,
      final IDelegateBridge bridge,
      final Territory location,
      final boolean defending) {
    return rollAa(
        validTargets, aaUnits, new ArrayList<>(), new ArrayList<>(), bridge, location, defending);
  }

  /**
   * Used to roll AA for battles, SBR, and fly over.
   *
   * @param validTargets - potential AA targets
   * @param aaUnits - AA units that could potentially be rolling
   * @param allEnemyUnitsAliveOrWaitingToDie - all enemy units to check for support
   * @param allFriendlyUnitsAliveOrWaitingToDie - all allied units to check for support
   * @param bridge - delegate bridge
   * @param location - battle territory
   * @param defending - whether AA units are defending or attacking
   * @return DiceRoll result which includes total hits and dice that were rolled
   */
  public static DiceRoll rollAa(
      final Collection<Unit> validTargets,
      final Collection<Unit> aaUnits,
      final Collection<Unit> allEnemyUnitsAliveOrWaitingToDie,
      final Collection<Unit> allFriendlyUnitsAliveOrWaitingToDie,
      final IDelegateBridge bridge,
      final Territory location,
      final boolean defending) {

    final GameData data = bridge.getData();
    final Map<Unit, TotalPowerAndTotalRolls> unitPowerAndRollsMap =
        TotalPowerAndTotalRolls.getAaUnitPowerAndRollsForNormalBattles(
            aaUnits,
            allEnemyUnitsAliveOrWaitingToDie,
            allFriendlyUnitsAliveOrWaitingToDie,
            defending,
            data);

    // Check that there are valid AA and targets to roll for
    final int totalAaAttacks =
        TotalPowerAndTotalRolls.getTotalAaAttacks(unitPowerAndRollsMap, validTargets);
    if (totalAaAttacks <= 0) {
      return new DiceRoll(List.of(), 0, 0);
    }

    // Determine dice sides (doesn't handle the possibility of different dice sides within the same
    // typeAA)
    final int diceSides =
        TotalPowerAndTotalRolls.getMaxAaAttackAndDiceSides(
                aaUnits, data, defending, unitPowerAndRollsMap)
            .getSecond();

    // Roll AA dice for LL or regular
    int hits = 0;
    final List<Die> sortedDice = new ArrayList<>();
    final String typeAa = UnitAttachment.get(aaUnits.iterator().next().getType()).getTypeAa();
    final int totalPower =
        TotalPowerAndTotalRolls.getTotalAaPowerThenHitsAndFillSortedDiceThenIfAllUseSameAttack(
                null, null, defending, unitPowerAndRollsMap, validTargets, data, false)
            .getFirst();
    final GamePlayer player = aaUnits.iterator().next().getOwner();
    final String annotation = "Roll " + typeAa + " in " + location.getName();
    if (Properties.getLowLuck(data) || Properties.getLowLuckAaOnly(data)) {
      hits += getAaLowLuckHits(bridge, sortedDice, totalPower, diceSides, player, annotation);
    } else {
      final int[] dice =
          bridge.getRandom(diceSides, totalAaAttacks, player, DiceType.COMBAT, annotation);
      hits +=
          TotalPowerAndTotalRolls.getTotalAaPowerThenHitsAndFillSortedDiceThenIfAllUseSameAttack(
                  dice, sortedDice, defending, unitPowerAndRollsMap, validTargets, data, true)
              .getSecond();
    }

    // Add dice results to history
    final double expectedHits = ((double) totalPower) / diceSides;
    final DiceRoll roll = new DiceRoll(sortedDice, hits, expectedHits);
    final String historyMessage =
        player.getName()
            + " roll "
            + typeAa
            + " dice in "
            + location
            + " : "
            + MyFormatter.asDice(roll);
    bridge.getHistoryWriter().addChildToEvent(historyMessage, roll);

    return roll;
  }

  private static int getAaLowLuckHits(
      final IDelegateBridge bridge,
      final List<Die> sortedDice,
      final int totalPower,
      final int chosenDiceSize,
      final GamePlayer playerRolling,
      final String annotation) {
    int hits = totalPower / chosenDiceSize;
    final int hitsFractional = totalPower % chosenDiceSize;
    if (hitsFractional > 0) {
      final int[] dice =
          bridge.getRandom(chosenDiceSize, 1, playerRolling, DiceType.COMBAT, annotation);
      final boolean hit = hitsFractional > dice[0];
      if (hit) {
        hits++;
      }
      final Die die = new Die(dice[0], hitsFractional, hit ? DieType.HIT : DieType.MISS);
      sortedDice.add(die);
    }
    return hits;
  }

  @VisibleForTesting
  static DiceRoll rollDice(
      final List<Unit> units,
      final boolean defending,
      final GamePlayer player,
      final IDelegateBridge bridge,
      final Territory territory,
      final Collection<TerritoryEffect> territoryEffects) {
    return rollDice(
        units, defending, player, bridge, territory, "", territoryEffects, List.of(), units);
  }

  /**
   * Used to roll dice for attackers and defenders in battles.
   *
   * @param units - units that could potentially be rolling
   * @param defending - whether units are defending or attacking
   * @param player - that will be rolling the dice
   * @param bridge - delegate bridge
   * @param territory - territory where the battle takes place
   * @param annotation - description of the battle being rolled for
   * @param territoryEffects - list of territory effects for the battle
   * @param allEnemyUnitsAliveOrWaitingToDie - all enemy units to check for support
   * @param allFriendlyUnitsAliveOrWaitingToDie - all allied units to check for support
   * @return DiceRoll result which includes total hits and dice that were rolled
   */
  public static DiceRoll rollDice(
      final Collection<Unit> units,
      final boolean defending,
      final GamePlayer player,
      final IDelegateBridge bridge,
      final Territory territory,
      final String annotation,
      final Collection<TerritoryEffect> territoryEffects,
      final Collection<Unit> allEnemyUnitsAliveOrWaitingToDie,
      final Collection<Unit> allFriendlyUnitsAliveOrWaitingToDie) {

    if (Properties.getLowLuck(bridge.getData())) {
      return rollDiceLowLuck(
          units,
          defending,
          player,
          bridge,
          territory,
          annotation,
          territoryEffects,
          allEnemyUnitsAliveOrWaitingToDie,
          allFriendlyUnitsAliveOrWaitingToDie);
    }
    return rollDiceNormal(
        units,
        defending,
        player,
        bridge,
        territory,
        annotation,
        territoryEffects,
        allEnemyUnitsAliveOrWaitingToDie,
        allFriendlyUnitsAliveOrWaitingToDie);
  }

  /**
   * Roll n-sided dice.
   *
   * @param annotation 0 based, add 1 to get actual die roll
   */
  public static DiceRoll rollNDice(
      final IDelegateBridge bridge,
      final int rollCount,
      final int sides,
      final GamePlayer playerRolling,
      final DiceType diceType,
      final String annotation) {
    if (rollCount == 0) {
      return new DiceRoll(new ArrayList<>(), 0, 0);
    }
    final int[] random = bridge.getRandom(sides, rollCount, playerRolling, diceType, annotation);
    final List<Die> dice = new ArrayList<>();
    for (int i = 0; i < rollCount; i++) {
      dice.add(new Die(random[i], 1, DieType.IGNORED));
    }
    return new DiceRoll(dice, rollCount, rollCount);
  }

  /** Roll dice for units using low luck rules. */
  private static DiceRoll rollDiceLowLuck(
      final Collection<Unit> unitsList,
      final boolean defending,
      final GamePlayer player,
      final IDelegateBridge bridge,
      final Territory location,
      final String annotation,
      final Collection<TerritoryEffect> territoryEffects,
      final Collection<Unit> allEnemyUnitsAliveOrWaitingToDie,
      final Collection<Unit> allFriendlyUnitsAliveOrWaitingToDie) {

    final List<Unit> units = new ArrayList<>(unitsList);
    final GameData data = bridge.getData();
    final Map<Unit, TotalPowerAndTotalRolls> unitPowerAndRollsMap =
        TotalPowerAndTotalRolls.getUnitPowerAndRollsForNormalBattles(
            units,
            allEnemyUnitsAliveOrWaitingToDie,
            allFriendlyUnitsAliveOrWaitingToDie,
            defending,
            data,
            location,
            territoryEffects);

    final int power = TotalPowerAndTotalRolls.getTotalPower(unitPowerAndRollsMap, data);
    if (power == 0) {
      return new DiceRoll(List.of(), 0, 0);
    }

    // Roll dice for the fractional part of the dice
    int hitCount = power / data.getDiceSides();
    final List<Die> dice = new ArrayList<>();
    final int rollFor = power % data.getDiceSides();
    final int[] random;
    if (rollFor == 0) {
      random = new int[0];
    } else {
      random = bridge.getRandom(data.getDiceSides(), 1, player, DiceType.COMBAT, annotation);
      // Zero based
      final boolean hit = rollFor > random[0];
      if (hit) {
        hitCount++;
      }
      dice.add(new Die(random[0], rollFor, hit ? DieType.HIT : DieType.MISS));
    }

    // Create DiceRoll object
    final double expectedHits = ((double) power) / data.getDiceSides();
    final DiceRoll diceRoll = new DiceRoll(dice, hitCount, expectedHits);
    bridge
        .getHistoryWriter()
        .addChildToEvent(annotation + " : " + MyFormatter.asDice(random), diceRoll);

    return diceRoll;
  }

  /**
   * Sorts the specified collection of units in ascending order of their attack or defense strength.
   *
   * @param defending {@code true} if the units should be sorted by their defense strength;
   *     otherwise the units will be sorted by their attack strength.
   */
  public static void sortByStrength(final List<Unit> units, final boolean defending) {
    // Pre-compute unit strength information to speed up the sort.
    final Table<UnitType, GamePlayer, Integer> strengthTable = HashBasedTable.create();
    for (final Unit unit : units) {
      final UnitType type = unit.getType();
      final GamePlayer owner = unit.getOwner();
      if (!strengthTable.contains(type, owner)) {
        if (defending) {
          strengthTable.put(type, owner, UnitAttachment.get(type).getDefense(owner));
        } else {
          strengthTable.put(type, owner, UnitAttachment.get(type).getAttack(owner));
        }
      }
    }
    final Comparator<Unit> comp =
        (u1, u2) -> {
          final int v1 = strengthTable.get(u1.getType(), u1.getOwner());
          final int v2 = strengthTable.get(u2.getType(), u2.getOwner());
          return Integer.compare(v1, v2);
        };
    units.sort(comp);
  }

  public static DiceRoll airBattle(
      final Collection<Unit> unitsList,
      final boolean defending,
      final GamePlayer player,
      final IDelegateBridge bridge,
      final String annotation) {

    final GameData data = bridge.getData();
    final boolean lhtrBombers = Properties.getLhtrHeavyBombers(data);
    final List<Unit> units = new ArrayList<>(unitsList);
    final int rollCount = AirBattle.getAirBattleRolls(unitsList, defending);
    if (rollCount == 0) {
      return new DiceRoll(new ArrayList<>(), 0, 0);
    }
    int[] random;
    final List<Die> dice = new ArrayList<>();
    int hitCount = 0;

    // bonus is normally 1 for most games
    final int extraRollBonus = Math.max(1, data.getDiceSides() / 6);
    int totalPower = 0;
    // We iterate through the units to find the total strength of the units
    for (final Unit current : units) {
      final UnitAttachment ua = UnitAttachment.get(current.getType());
      final int rolls = AirBattle.getAirBattleRolls(current, defending);
      int totalStrength = 0;
      final int strength =
          Math.min(
              data.getDiceSides(),
              Math.max(
                  0,
                  (defending
                      ? ua.getAirDefense(current.getOwner())
                      : ua.getAirAttack(current.getOwner()))));
      for (int i = 0; i < rolls; i++) {
        // LHTR means pick the best dice roll, which doesn't really make sense in LL. So instead, we
        // will just add +1
        // onto the power to simulate the gains of having the best die picked.
        if (i > 1 && (lhtrBombers || ua.getChooseBestRoll())) {
          totalStrength += extraRollBonus;
          continue;
        }
        totalStrength += strength;
      }
      totalPower += Math.min(Math.max(totalStrength, 0), data.getDiceSides());
    }

    if (Properties.getLowLuck(data)) {
      // Get number of hits
      hitCount = totalPower / data.getDiceSides();
      random = new int[0];
      // We need to roll dice for the fractional part of the dice.
      final int power = totalPower % data.getDiceSides();
      if (power != 0) {
        random = bridge.getRandom(data.getDiceSides(), 1, player, DiceType.COMBAT, annotation);
        final boolean hit = power > random[0];
        if (hit) {
          hitCount++;
        }
        dice.add(new Die(random[0], power, hit ? DieType.HIT : DieType.MISS));
      }
    } else {
      random =
          bridge.getRandom(data.getDiceSides(), rollCount, player, DiceType.COMBAT, annotation);
      int diceIndex = 0;
      for (final Unit current : units) {
        final UnitAttachment ua = UnitAttachment.get(current.getType());
        final int strength =
            Math.min(
                data.getDiceSides(),
                Math.max(
                    0,
                    (defending
                        ? ua.getAirDefense(current.getOwner())
                        : ua.getAirAttack(current.getOwner()))));
        final int rolls = AirBattle.getAirBattleRolls(current, defending);
        // lhtr heavy bombers take best of n dice for both attack and defense
        if (rolls > 1 && (lhtrBombers || ua.getChooseBestRoll())) {
          int minIndex = 0;
          int min = data.getDiceSides();
          for (int i = 0; i < rolls; i++) {
            if (random[diceIndex + i] < min) {
              min = random[diceIndex + i];
              minIndex = i;
            }
          }
          final boolean hit = strength > random[diceIndex + minIndex];
          dice.add(
              new Die(random[diceIndex + minIndex], strength, hit ? DieType.HIT : DieType.MISS));
          for (int i = 0; i < rolls; i++) {
            if (i != minIndex) {
              dice.add(new Die(random[diceIndex + i], strength, DieType.IGNORED));
            }
          }
          if (hit) {
            hitCount++;
          }
          diceIndex += rolls;
        } else {
          for (int i = 0; i < rolls; i++) {
            final boolean hit = strength > random[diceIndex];
            dice.add(new Die(random[diceIndex], strength, hit ? DieType.HIT : DieType.MISS));
            if (hit) {
              hitCount++;
            }
            diceIndex++;
          }
        }
      }
    }
    final double expectedHits = ((double) totalPower) / data.getDiceSides();
    final DiceRoll diceRoll = new DiceRoll(dice, hitCount, expectedHits);
    bridge
        .getHistoryWriter()
        .addChildToEvent(annotation + " : " + MyFormatter.asDice(random), diceRoll);
    return diceRoll;
  }

  /** Roll dice for units per normal rules. */
  private static DiceRoll rollDiceNormal(
      final Collection<Unit> unitsList,
      final boolean defending,
      final GamePlayer player,
      final IDelegateBridge bridge,
      final Territory location,
      final String annotation,
      final Collection<TerritoryEffect> territoryEffects,
      final Collection<Unit> allEnemyUnitsAliveOrWaitingToDie,
      final Collection<Unit> allFriendlyUnitsAliveOrWaitingToDie) {

    final List<Unit> units = new ArrayList<>(unitsList);
    final GameData data = bridge.getData();
    sortByStrength(units, defending);
    final Map<Unit, TotalPowerAndTotalRolls> unitPowerAndRollsMap =
        TotalPowerAndTotalRolls.getUnitPowerAndRollsForNormalBattles(
            units,
            allEnemyUnitsAliveOrWaitingToDie,
            allFriendlyUnitsAliveOrWaitingToDie,
            defending,
            data,
            location,
            territoryEffects);

    final TotalPowerAndTotalRolls totalPowerAndRolls =
        TotalPowerAndTotalRolls.getTotalPowerAndRolls(unitPowerAndRollsMap, data);
    final int rollCount = totalPowerAndRolls.getTotalRolls();
    if (rollCount == 0) {
      return new DiceRoll(new ArrayList<>(), 0, 0);
    }

    final int[] random =
        bridge.getRandom(data.getDiceSides(), rollCount, player, DiceType.COMBAT, annotation);
    final boolean lhtrBombers = Properties.getLhtrHeavyBombers(data);
    final List<Die> dice = new ArrayList<>();
    int hitCount = 0;
    int diceIndex = 0;
    for (final Unit current : units) {
      final UnitAttachment ua = UnitAttachment.get(current.getType());
      final TotalPowerAndTotalRolls powerAndRolls = unitPowerAndRollsMap.get(current);
      final int strength = powerAndRolls.getTotalPower();
      final int rolls = powerAndRolls.getTotalRolls();
      // lhtr heavy bombers take best of n dice for both attack and defense
      if (rolls <= 0 || strength <= 0) {
        continue;
      }
      if (rolls > 1 && (lhtrBombers || ua.getChooseBestRoll())) {
        int smallestDieIndex = 0;
        int smallestDie = data.getDiceSides();
        for (int i = 0; i < rolls; i++) {
          if (random[diceIndex + i] < smallestDie) {
            smallestDie = random[diceIndex + i];
            smallestDieIndex = i;
          }
        }
        // Zero based
        final boolean hit = strength > random[diceIndex + smallestDieIndex];
        dice.add(
            new Die(
                random[diceIndex + smallestDieIndex], strength, hit ? DieType.HIT : DieType.MISS));
        for (int i = 0; i < rolls; i++) {
          if (i != smallestDieIndex) {
            dice.add(new Die(random[diceIndex + i], strength, DieType.IGNORED));
          }
        }
        if (hit) {
          hitCount++;
        }
        diceIndex += rolls;
      } else {
        for (int i = 0; i < rolls; i++) {
          if (diceIndex >= random.length) {
            break;
          }
          // Zero based
          final boolean hit = strength > random[diceIndex];
          dice.add(new Die(random[diceIndex], strength, hit ? DieType.HIT : DieType.MISS));
          if (hit) {
            hitCount++;
          }
          diceIndex++;
        }
      }
    }

    final int totalPower = totalPowerAndRolls.getTotalPower();
    final double expectedHits = ((double) totalPower) / data.getDiceSides();
    final DiceRoll diceRoll = new DiceRoll(dice, hitCount, expectedHits);
    bridge
        .getHistoryWriter()
        .addChildToEvent(annotation + " : " + MyFormatter.asDice(random), diceRoll);

    return diceRoll;
  }

  /**
   * Parses the player name from the given annotation that has been produced by getAnnotation().
   *
   * @param annotation The annotation string.
   * @return The player's name.
   */
  public static String getPlayerNameFromAnnotation(final String annotation) {
    // This parses the "Germans roll dice for " format produced by getAnnotation() below.
    return annotation.split(" ", 2)[0];
  }

  public static String getAnnotation(
      final Collection<Unit> units,
      final GamePlayer player,
      final Territory territory,
      final int battleRound) {
    final StringBuilder buffer = new StringBuilder(80);
    // Note: This pattern is parsed when loading saved games to restore dice stats to get the player
    // name via the
    // getPlayerNameFromAnnotation() function above. When changing this format, update
    // getPlayerNameFromAnnotation(),
    // preferably in a way that is backwards compatible (can parse previous formats too).
    buffer
        .append(player.getName())
        .append(" roll dice for ")
        .append(MyFormatter.unitsToTextNoOwner(units))
        .append(" in ")
        .append(territory.getName())
        .append(", round ")
        .append(battleRound + 1);
    return buffer.toString();
  }

  public int getHits() {
    return hits;
  }

  public double getExpectedHits() {
    return expectedHits;
  }

  /**
   * Returns all rolls that are equal to the specified value.
   *
   * @param rollAt the strength of the roll, eg infantry roll at 2, expecting a number in [1,6]
   */
  public List<Die> getRolls(final int rollAt) {
    return rolls.stream() //
        .filter(die -> die.getRolledAt() == rollAt)
        .collect(Collectors.toList());
  }

  public int size() {
    return rolls.size();
  }

  public boolean isEmpty() {
    return rolls.isEmpty();
  }

  public Die getDie(final int index) {
    return rolls.get(index);
  }

  @Override
  public void writeExternal(final ObjectOutput out) throws IOException {
    final int[] dice = new int[rolls.size()];
    for (int i = 0; i < rolls.size(); i++) {
      dice[i] = rolls.get(i).getCompressedValue();
    }
    out.writeObject(dice);
    out.writeInt(hits);
    out.writeDouble(expectedHits);
  }

  @Override
  public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
    final int[] dice = (int[]) in.readObject();
    rolls = new ArrayList<>(dice.length);
    for (final int element : dice) {
      rolls.add(Die.getFromWriteValue(element));
    }
    hits = in.readInt();
    expectedHits = in.readDouble();
  }

  @Override
  public String toString() {
    return "DiceRoll dice:" + rolls + " hits:" + hits + " expectedHits:" + expectedHits;
  }
}
