package games.strategy.engine.data.util;

import games.strategy.engine.data.GameMap;
import games.strategy.engine.data.Territory;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.triplea.java.ObjectUtils;

/**
 * Implements Breadth First Search (BFS) to traverse / find territories. Since the search criteria
 * varies depending on the use case, the class is designed to take a Visitor object with methods
 * visit() and shouldContinueSearch() for customizing the behavior.
 */
public final class BreadthFirstSearch {
  private final GameMap map;
  private final Set<Territory> visited;
  private final ArrayDeque<Territory> territoriesToCheck;
  private final Predicate<Territory> neighborCondition;

  public abstract static class Visitor {
    /**
     * Called when a new territory is encountered.
     *
     * @param territory The new territory.
     */
    public abstract void visit(Territory territory);

    /**
     * Called after all territories within the specified distance have been searched. Can be
     * overridden to terminate the search.
     *
     * @param distanceSearched The current distance searched
     * @return Whether the search should continue.
     */
    public boolean shouldContinueSearch(final int distanceSearched) {
      return true;
    }
  }

  /**
   * @param startTerritory The territory from where to start the search.
   * @param neighborCondition Condition that neighboring territories must match to be considered
   *     neighbors.
   */
  public BreadthFirstSearch(
      final Territory startTerritory, final Predicate<Territory> neighborCondition) {
    this.map = startTerritory.getData().getMap();
    this.visited = new HashSet<>(List.of(startTerritory));
    this.territoriesToCheck = new ArrayDeque<>(List.of(startTerritory));
    this.neighborCondition = neighborCondition;
  }

  public BreadthFirstSearch(final Territory startTerritory) {
    this(startTerritory, t -> true);
  }

  /**
   * Performs the search. It will end when either all territories have been visited or
   * shouldContinueSearch() returns false.
   *
   * @param visitor The visitor object to customize the search.
   */
  public void traverse(final Visitor visitor) {
    // Since we process territories in order of distance, we can keep track of the last territory
    // at the current distance that's in the territoriesToCheck queue. When we encounter it, we
    // increment the distance and update lastTerritoryAtCurrentDistance.
    int currentDistance = 0;
    Territory lastTerritoryAtCurrentDistance = territoriesToCheck.peekLast();
    while (!territoriesToCheck.isEmpty()) {
      final Territory territory = checkNextTerritory(visitor);

      // If we just processed the last territory at the current distance, increment the distance
      // and set the territory at which we need to update it again to be the last one added.
      if (ObjectUtils.referenceEquals(territory, lastTerritoryAtCurrentDistance)) {
        currentDistance++;
        if (!visitor.shouldContinueSearch(currentDistance)) {
          return;
        }
        lastTerritoryAtCurrentDistance = territoriesToCheck.peekLast();
      }
    }
  }

  private Territory checkNextTerritory(final Visitor visitor) {
    final Territory territory = territoriesToCheck.removeFirst();
    // Note: The condition isn't passed to getNeighbors() because that implementation is very slow.
    for (final Territory neighbor : map.getNeighbors(territory)) {
      if (neighborCondition.test(neighbor) && visited.add(neighbor)) {
        territoriesToCheck.add(neighbor);
        visitor.visit(neighbor);
      }
    }
    return territory;
  }
}
