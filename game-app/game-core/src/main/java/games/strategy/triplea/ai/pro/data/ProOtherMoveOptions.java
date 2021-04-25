package games.strategy.triplea.ai.pro.data;

import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.triplea.ai.pro.ProData;
import games.strategy.triplea.ai.pro.util.ProBattleUtils;
import games.strategy.triplea.ai.pro.util.ProUtils;
import games.strategy.triplea.delegate.Matches;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The result of an AI movement analysis for another player's possible moves. */
public class ProOtherMoveOptions {

  private final Map<Territory, ProTerritory> maxMoveMap;
  private final Map<Territory, List<ProTerritory>> moveMaps;

  public ProOtherMoveOptions() {
    maxMoveMap = new HashMap<>();
    moveMaps = new HashMap<>();
  }

  public ProOtherMoveOptions(
      final ProData proData,
      final List<Map<Territory, ProTerritory>> moveMapList,
      final GamePlayer player,
      final boolean isAttacker) {
    maxMoveMap = newMaxMoveMap(proData, moveMapList, player, isAttacker);
    moveMaps = newMoveMaps(moveMapList);
  }

  public ProTerritory getMax(final Territory t) {
    return maxMoveMap.get(t);
  }

  List<ProTerritory> getAll(final Territory t) {
    final List<ProTerritory> result = moveMaps.get(t);
    if (result != null) {
      return result;
    }
    return new ArrayList<>();
  }

  @Override
  public String toString() {
    return maxMoveMap.toString();
  }

  private static Map<Territory, ProTerritory> newMaxMoveMap(
      final ProData proData,
      final List<Map<Territory, ProTerritory>> moveMaps,
      final GamePlayer player,
      final boolean isAttacker) {

    final Map<Territory, ProTerritory> result = new HashMap<>();
    final List<GamePlayer> players = ProUtils.getOtherPlayersInTurnOrder(player);
    for (final Map<Territory, ProTerritory> moveMap : moveMaps) {
      for (final Territory t : moveMap.keySet()) {

        // Get current player
        final Set<Unit> currentUnits = new HashSet<>(moveMap.get(t).getMaxUnits());
        currentUnits.addAll(moveMap.get(t).getMaxAmphibUnits());
        final GamePlayer movePlayer;
        if (!currentUnits.isEmpty()) {
          movePlayer = currentUnits.iterator().next().getOwner();
        } else {
          continue;
        }

        // Skip if checking allied moves and their turn doesn't come before territory owner's
        if (proData.getData().getRelationshipTracker().isAllied(player, movePlayer)
            && !ProUtils.isPlayersTurnFirst(players, movePlayer, t.getOwner())) {
          continue;
        }

        // Add to max move map if its empty or its strength is greater than existing
        if (!result.containsKey(t)) {
          result.put(t, moveMap.get(t));
        } else {
          final Set<Unit> maxUnits = new HashSet<>(result.get(t).getMaxUnits());
          maxUnits.addAll(result.get(t).getMaxAmphibUnits());
          double maxStrength = 0;
          if (!maxUnits.isEmpty()) {
            maxStrength =
                ProBattleUtils.estimateStrength(
                    t, new ArrayList<>(maxUnits), new ArrayList<>(), isAttacker);
          }
          final double currentStrength =
              ProBattleUtils.estimateStrength(
                  t, new ArrayList<>(currentUnits), new ArrayList<>(), isAttacker);
          final boolean currentHasLandUnits = currentUnits.stream().anyMatch(Matches.unitIsLand());
          final boolean maxHasLandUnits = maxUnits.stream().anyMatch(Matches.unitIsLand());
          if ((currentHasLandUnits
                  && ((!maxHasLandUnits && !t.isWater()) || currentStrength > maxStrength))
              || ((!maxHasLandUnits || t.isWater()) && currentStrength > maxStrength)) {
            result.put(t, moveMap.get(t));
          }
        }
      }
    }
    return result;
  }

  private static Map<Territory, List<ProTerritory>> newMoveMaps(
      final List<Map<Territory, ProTerritory>> moveMapList) {

    final Map<Territory, List<ProTerritory>> result = new HashMap<>();
    for (final Map<Territory, ProTerritory> moveMap : moveMapList) {
      for (final Territory t : moveMap.keySet()) {
        if (!result.containsKey(t)) {
          final List<ProTerritory> list = new ArrayList<>();
          list.add(moveMap.get(t));
          result.put(t, list);
        } else {
          result.get(t).add(moveMap.get(t));
        }
      }
    }
    return result;
  }
}
