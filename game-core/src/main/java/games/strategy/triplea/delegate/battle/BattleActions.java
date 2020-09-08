package games.strategy.triplea.delegate.battle;

import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.engine.delegate.IDelegateBridge;
import games.strategy.triplea.delegate.battle.MustFightBattle.RetreatType;
import games.strategy.triplea.delegate.battle.MustFightBattle.ReturnFire;
import java.util.Collection;
import java.util.function.Predicate;

/** Actions that can occur in a battle that require interaction with {@link IDelegateBridge} */
public interface BattleActions {

  void fireOffensiveAaGuns();

  void fireDefensiveAaGuns();

  void submergeUnits(Collection<Unit> units, boolean defender, IDelegateBridge bridge);

  void queryRetreat(
      boolean defender,
      RetreatType retreatType,
      IDelegateBridge bridge,
      Collection<Territory> initialAvailableTerritories);

  void fireNavalBombardment(IDelegateBridge bridge);

  void landParatroopers(
      IDelegateBridge bridge, Collection<Unit> airTransports, Collection<Unit> dependents);

  void removeNonCombatants(IDelegateBridge bridge);

  void markNoMovementLeft(IDelegateBridge bridge);

  void clearWaitingToDieAndDamagedChangesInto(IDelegateBridge bridge);

  void endBattle(IDelegateBridge bridge);

  void attackerWins(IDelegateBridge bridge);

  void findTargetGroupsAndFire(
      ReturnFire returnFire,
      String stepName,
      boolean defending,
      GamePlayer firingPlayer,
      Predicate<Unit> firingUnitPredicate,
      Collection<Unit> firingUnits,
      Collection<Unit> firingUnitsWaitingToDie,
      Collection<Unit> enemyUnits,
      Collection<Unit> enemyUnitsWaitingToDie);

  void remove(
      Collection<Unit> killedUnits,
      IDelegateBridge bridge,
      Territory battleSite,
      Boolean defenderDying);

  void damagedChangeInto(
      GamePlayer player,
      Collection<Unit> units,
      Collection<Unit> killedUnits,
      IDelegateBridge bridge);
}
