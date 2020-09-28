package games.strategy.triplea.ui.display;

import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.engine.display.IDisplay;
import games.strategy.engine.framework.startup.ui.PlayerType;
import games.strategy.engine.player.Player;
import games.strategy.triplea.TripleAPlayer;
import games.strategy.triplea.delegate.DiceRoll;
import games.strategy.triplea.delegate.Die;
import games.strategy.triplea.delegate.battle.IBattle.BattleType;
import games.strategy.triplea.ui.TripleAFrame;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Implementation of {@link IDisplay} appropriate for a headed environment. */
public class TripleADisplay implements IDisplay {
  private final TripleAFrame ui;

  public TripleADisplay(final TripleAFrame ui) {
    this.ui = ui;
  }

  @Override
  public void showBattle(
      final UUID battleId,
      final Territory location,
      final String battleTitle,
      final Collection<Unit> attackingUnits,
      final Collection<Unit> defendingUnits,
      final Collection<Unit> killedUnits,
      final Collection<Unit> attackingWaitingToDie,
      final Collection<Unit> defendingWaitingToDie,
      final Map<Unit, Collection<Unit>> unitDependents,
      final GamePlayer attacker,
      final GamePlayer defender,
      final boolean isAmphibious,
      final BattleType battleType,
      final Collection<Unit> amphibiousLandAttackers) {
    ui.getBattlePanel()
        .showBattle(
            battleId,
            location,
            attackingUnits,
            defendingUnits,
            killedUnits,
            attackingWaitingToDie,
            defendingWaitingToDie,
            attacker,
            defender,
            battleType);
  }

  @Override
  public void listBattleSteps(final UUID battleId, final List<String> steps) {
    ui.getBattlePanel().listBattle(steps);
  }

  @Override
  public void casualtyNotification(
      final UUID battleId,
      final String step,
      final DiceRoll dice,
      final GamePlayer player,
      final Collection<Unit> killed,
      final Collection<Unit> damaged,
      final Map<Unit, Collection<Unit>> dependents) {
    ui.getBattlePanel().casualtyNotification(step, dice, player, killed, damaged, dependents);
  }

  @Override
  public void deadUnitNotification(
      final UUID battleId,
      final GamePlayer player,
      final Collection<Unit> killed,
      final Map<Unit, Collection<Unit>> dependents) {
    ui.getBattlePanel().deadUnitNotification(player, killed, dependents);
  }

  @Override
  public void changedUnitsNotification(
      final UUID battleId,
      final GamePlayer player,
      final Collection<Unit> removedUnits,
      final Collection<Unit> addedUnits,
      final Map<Unit, Collection<Unit>> dependents) {
    ui.getBattlePanel().changedUnitsNotification(player, removedUnits, addedUnits);
  }

  @Override
  public void battleEnd(final UUID battleId, final String message) {
    ui.getBattlePanel().battleEndMessage(message);
  }

  @Override
  public void bombingResults(final UUID battleId, final List<Die> dice, final int cost) {
    ui.getBattlePanel().bombingResults(dice, cost);
  }

  @Override
  public void notifyRetreat(
      final String shortMessage,
      final String message,
      final String step,
      final GamePlayer retreatingPlayer) {
    // we just told the game to retreat, so we already know
    if (ui.getLocalPlayers().playing(retreatingPlayer)) {
      return;
    }
    ui.getBattlePanel().notifyRetreat(message, step);
  }

  @Override
  public void notifyRetreat(final UUID battleId, final Collection<Unit> retreating) {
    ui.getBattlePanel().notifyRetreat(retreating);
  }

  @Override
  public void notifyDice(final DiceRoll dice, final String stepName) {
    ui.getBattlePanel().showDice(dice, stepName);
  }

  @Override
  public void gotoBattleStep(final UUID battleId, final String step) {
    ui.getBattlePanel().gotoStep(step);
  }

  @Override
  public void shutDown() {
    ui.stopGame();
  }

  @Override
  public void reportMessageToAll(
      final String message,
      final String title,
      final boolean doNotIncludeHost,
      final boolean doNotIncludeClients,
      final boolean doNotIncludeObservers) {
    if (doNotIncludeHost && doNotIncludeClients && doNotIncludeObservers) {
      return;
    }
    if (doNotIncludeHost || doNotIncludeClients || doNotIncludeObservers) {
      boolean isHost = false;
      boolean isClient = false;
      boolean isObserver = true;
      for (final Player player : ui.getLocalPlayers().getLocalPlayers()) {
        // if we have any local players, we are not an observer
        isObserver = false;
        if (player instanceof TripleAPlayer) {
          if (player.getPlayerType() == PlayerType.CLIENT_PLAYER) {
            isClient = true;
          } else {
            isHost = true;
          }
        } else {
          // AIs are run by the host machine
          isHost = true;
        }
      }
      if ((doNotIncludeHost && isHost)
          || (doNotIncludeClients && isClient)
          || (doNotIncludeObservers && isObserver)) {
        return;
      }
    }
    ui.notifyMessage(message, title);
  }

  @Override
  public void reportMessageToPlayers(
      final Collection<GamePlayer> playersToSendTo,
      final Collection<GamePlayer> butNotThesePlayers,
      final String message,
      final String title) {
    if (playersToSendTo == null || playersToSendTo.isEmpty()) {
      return;
    }
    if (butNotThesePlayers != null) {
      for (final GamePlayer p : butNotThesePlayers) {
        if (ui.getLocalPlayers().playing(p)) {
          return;
        }
      }
    }
    boolean isPlaying = false;
    for (final GamePlayer p : playersToSendTo) {
      if (ui.getLocalPlayers().playing(p)) {
        isPlaying = true;
        break;
      }
    }
    if (isPlaying) {
      ui.notifyMessage(message, title);
    }
  }
}
