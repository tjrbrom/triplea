package games.strategy.triplea;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.GameStep;
import games.strategy.engine.data.MoveDescription;
import games.strategy.engine.data.ProductionRule;
import games.strategy.engine.data.RepairRule;
import games.strategy.engine.data.Resource;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.triplea.attachments.PlayerAttachment;
import games.strategy.triplea.attachments.PoliticalActionAttachment;
import games.strategy.triplea.attachments.TerritoryAttachment;
import games.strategy.triplea.attachments.UserActionAttachment;
import games.strategy.triplea.delegate.DiceRoll;
import games.strategy.triplea.delegate.GameStepPropertiesHelper;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.delegate.data.BattleListing;
import games.strategy.triplea.delegate.data.CasualtyDetails;
import games.strategy.triplea.delegate.data.CasualtyList;
import games.strategy.triplea.delegate.data.FightBattleDetails;
import games.strategy.triplea.delegate.data.TechResults;
import games.strategy.triplea.delegate.data.TechRoll;
import games.strategy.triplea.delegate.remote.IAbstractForumPosterDelegate;
import games.strategy.triplea.delegate.remote.IAbstractPlaceDelegate;
import games.strategy.triplea.delegate.remote.IBattleDelegate;
import games.strategy.triplea.delegate.remote.IEditDelegate;
import games.strategy.triplea.delegate.remote.IMoveDelegate;
import games.strategy.triplea.delegate.remote.IPoliticsDelegate;
import games.strategy.triplea.delegate.remote.IPurchaseDelegate;
import games.strategy.triplea.delegate.remote.ITechDelegate;
import games.strategy.triplea.delegate.remote.IUserActionDelegate;
import games.strategy.triplea.formatter.MyFormatter;
import games.strategy.triplea.player.AbstractHumanPlayer;
import games.strategy.triplea.settings.ClientSetting;
import games.strategy.triplea.ui.PlaceData;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;
import javax.swing.ButtonModel;
import javax.swing.SwingUtilities;
import lombok.extern.java.Log;
import org.triplea.java.collections.CollectionUtils;
import org.triplea.java.collections.IntegerMap;
import org.triplea.java.concurrency.AsyncRunner;
import org.triplea.sound.ClipPlayer;
import org.triplea.sound.SoundPath;
import org.triplea.util.Tuple;

/**
 * As a rule, nothing that changes GameData should be in here. It should be using a Change done in a
 * delegate, and done through an IDelegate, which we get through getPlayerBridge().getRemote()
 */
@Log
public abstract class TripleAPlayer extends AbstractHumanPlayer {
  private boolean soundPlayedAlreadyCombatMove = false;
  private boolean soundPlayedAlreadyNonCombatMove = false;
  private boolean soundPlayedAlreadyPurchase = false;
  private boolean soundPlayedAlreadyTechnology = false;
  private boolean soundPlayedAlreadyBattle = false;
  private boolean soundPlayedAlreadyEndTurn = false;
  private boolean soundPlayedAlreadyPlacement = false;
  private final ActionListener editModeAction =
      e -> {
        final boolean editMode = ((ButtonModel) e.getSource()).isSelected();
        try {
          // Set edit mode
          // All GameDataChangeListeners will be notified upon success
          final IEditDelegate editDelegate =
              (IEditDelegate) getPlayerBridge().getRemotePersistentDelegate("edit");
          AsyncRunner.runAsync(() -> editDelegate.setEditMode(editMode))
              .exceptionally(
                  throwable -> log.log(Level.SEVERE, "Failed to toggle edit mode", throwable));
        } catch (final Exception exception) {
          log.log(Level.SEVERE, "Failed to set edit mode to " + editMode, exception);
          // toggle back to previous state since setEditMode failed
          ui.getEditModeButtonModel().setSelected(!ui.getEditModeButtonModel().isSelected());
        }
      };

  public TripleAPlayer(final String name) {
    super(name);
  }

  @Override
  public void reportError(final String error) {
    ui.notifyError(error);
  }

  @Override
  public void reportMessage(final String message, final String title) {
    if (ui != null) {
      ui.notifyMessage(message, title);
    }
  }

  @Override
  public void start(final String name) {
    // must call super.start
    super.start(name);
    if (getPlayerBridge().isGameOver()) {
      return;
    }
    if (ui == null) {
      // We will get here if we are loading a save game of a map that we do not have. Caller code
      // should be doing
      // the error handling, so just return..
      return;
    }
    // TODO: parsing which UI thing we should run based on the string name of a possibly extended
    // delegate
    // class seems like a bad way of doing this whole method. however i can't think of anything
    // better right now.
    // This is how we find out our game step: getGameData().getSequence().getStep()
    // The game step contains information, like the exact delegate and the delegate's class,
    // that we can further use if needed. This is how we get our communication bridge for affecting
    // the gamedata:
    // (ISomeDelegate) getPlayerBridge().getRemote()
    // We should never touch the game data directly. All changes to game data are done through the
    // remote,
    // which then changes the game using the DelegateBridge -> change factory
    ui.requiredTurnSeries(this.getGamePlayer());
    enableEditModeMenu();
    boolean badStep = false;
    if (name.endsWith("Tech")) {
      tech();
    } else if (GameStep.isPurchaseOrBidStep(name)) {
      purchase(GameStepPropertiesHelper.isBid(getGameData()));
      if (!GameStepPropertiesHelper.isBid(getGameData())) {
        ui.waitForMoveForumPoster(this.getGamePlayer(), getPlayerBridge());
        // TODO only do forum post if there is a combat
      }
    } else if (name.endsWith("Move")) {
      final boolean nonCombat = GameStepPropertiesHelper.isNonCombatMove(getGameData(), false);
      move(nonCombat, name);
      if (!nonCombat) {
        ui.waitForMoveForumPoster(this.getGamePlayer(), getPlayerBridge());
        // TODO only do forum post if there is a combat
      }
    } else if (name.endsWith("Battle")) {
      battle();
    } else if (GameStep.isPlaceStep(name)) {
      place();
    } else if (name.endsWith("Politics")) {
      politics(true);
    } else if (name.endsWith("UserActions")) {
      userActions(true);
    } else if (name.endsWith("EndTurn")) {
      endTurn();
      // reset our sounds
      soundPlayedAlreadyCombatMove = false;
      soundPlayedAlreadyNonCombatMove = false;
      soundPlayedAlreadyPurchase = false;
      soundPlayedAlreadyTechnology = false;
      soundPlayedAlreadyBattle = false;
      soundPlayedAlreadyEndTurn = false;
      soundPlayedAlreadyPlacement = false;
    } else {
      badStep = !name.endsWith("TechActivation");
    }
    disableEditModeMenu();
    if (badStep) {
      throw new IllegalArgumentException("Unrecognized step name:" + name);
    }
  }

  private void enableEditModeMenu() {
    try {
      ui.setEditDelegate((IEditDelegate) getPlayerBridge().getRemotePersistentDelegate("edit"));
    } catch (final Exception e) {
      log.log(Level.SEVERE, "Failed to set edit delegate", e);
    }
    SwingUtilities.invokeLater(
        () -> {
          ui.getEditModeButtonModel().addActionListener(editModeAction);
          ui.getEditModeButtonModel().setEnabled(true);
        });
  }

  private void disableEditModeMenu() {
    ui.setEditDelegate(null);
    SwingUtilities.invokeLater(
        () -> {
          ui.getEditModeButtonModel().setEnabled(false);
          ui.getEditModeButtonModel().removeActionListener(editModeAction);
        });
  }

  private void politics(final boolean firstRun) {
    if (getPlayerBridge().isGameOver()) {
      return;
    }
    final IPoliticsDelegate politicsDelegate;
    try {
      politicsDelegate = (IPoliticsDelegate) getPlayerBridge().getRemoteDelegate();
    } catch (final ClassCastException e) {
      final String errorContext =
          "PlayerBridge step name: "
              + getPlayerBridge().getStepName()
              + ", Remote class name: "
              + getPlayerBridge().getRemoteDelegate().getClass();
      log.log(Level.SEVERE, errorContext, e);
      throw new IllegalStateException(errorContext, e);
    }

    final PoliticalActionAttachment actionChoice =
        ui.getPoliticalActionChoice(this.getGamePlayer(), firstRun, politicsDelegate);
    if (actionChoice != null) {
      politicsDelegate.attemptAction(actionChoice);
      politics(false);
    }
  }

  private void userActions(final boolean firstRun) {
    if (getPlayerBridge().isGameOver()) {
      return;
    }
    final IUserActionDelegate userActionDelegate;
    try {
      userActionDelegate = (IUserActionDelegate) getPlayerBridge().getRemoteDelegate();
    } catch (final ClassCastException e) {
      final String errorContext =
          "PlayerBridge step name: "
              + getPlayerBridge().getStepName()
              + ", Remote class name: "
              + getPlayerBridge().getRemoteDelegate().getClass();
      log.log(Level.SEVERE, errorContext, e);
      throw new IllegalStateException(errorContext, e);
    }
    final UserActionAttachment actionChoice =
        ui.getUserActionChoice(this.getGamePlayer(), firstRun, userActionDelegate);
    if (actionChoice != null) {
      userActionDelegate.attemptAction(actionChoice);
      userActions(false);
    }
  }

  @Override
  public boolean acceptAction(
      final GamePlayer playerSendingProposal,
      final String acceptanceQuestion,
      final boolean politics) {
    final GameData data = getGameData();
    return !this.getGamePlayer().amNotDeadYet(data)
        || getPlayerBridge().isGameOver()
        || ui.acceptAction(
            playerSendingProposal,
            "To " + this.getGamePlayer().getName() + ": " + acceptanceQuestion,
            politics);
  }

  private void tech() {
    if (getPlayerBridge().isGameOver()) {
      return;
    }
    final ITechDelegate techDelegate;
    try {
      techDelegate = (ITechDelegate) getPlayerBridge().getRemoteDelegate();
    } catch (final ClassCastException e) {
      final String errorContext =
          "PlayerBridge step name: "
              + getPlayerBridge().getStepName()
              + ", Remote class name: "
              + getPlayerBridge().getRemoteDelegate().getClass();
      // for some reason the client is not seeing or getting these errors, so print to err too
      log.log(Level.SEVERE, errorContext, e);
      throw new IllegalStateException(errorContext, e);
    }

    final GamePlayer gamePlayer = this.getGamePlayer();
    if (!soundPlayedAlreadyTechnology) {
      ClipPlayer.play(SoundPath.CLIP_PHASE_TECHNOLOGY, gamePlayer);
      soundPlayedAlreadyTechnology = true;
    }
    final TechRoll techRoll = ui.getTechRolls(gamePlayer);
    if (techRoll != null) {
      final TechResults techResults =
          techDelegate.rollTech(
              techRoll.getRolls(),
              techRoll.getTech(),
              techRoll.getNewTokens(),
              techRoll.getWhoPaysHowMuch());
      if (techResults.isError()) {
        ui.notifyError(techResults.getErrorString());
        tech();
      } else {
        ui.notifyTechResults(techResults);
      }
    }
  }

  private void move(final boolean nonCombat, final String stepName) {
    if (getPlayerBridge().isGameOver()) {
      return;
    }
    final IMoveDelegate moveDel;
    try {
      moveDel = (IMoveDelegate) getPlayerBridge().getRemoteDelegate();
    } catch (final ClassCastException e) {
      final String errorContext =
          "PlayerBridge step name: "
              + getPlayerBridge().getStepName()
              + ", Remote class name: "
              + getPlayerBridge().getRemoteDelegate().getClass();
      // for some reason the client is not seeing or getting these errors, so print to err too
      log.log(Level.SEVERE, errorContext, e);
      throw new IllegalStateException(errorContext, e);
    }

    final GamePlayer gamePlayer = this.getGamePlayer();

    if (nonCombat && !soundPlayedAlreadyNonCombatMove) {
      ClipPlayer.play(SoundPath.CLIP_PHASE_MOVE_NONCOMBAT, gamePlayer);
      soundPlayedAlreadyNonCombatMove = true;
    }

    if (!nonCombat && !soundPlayedAlreadyCombatMove) {
      ClipPlayer.play(SoundPath.CLIP_PHASE_MOVE_COMBAT, gamePlayer);
      soundPlayedAlreadyCombatMove = true;
    }
    // getMove will block until all moves are done. We recursively call this same method until
    // getMove stops blocking.
    final MoveDescription moveDescription =
        ui.getMove(gamePlayer, getPlayerBridge(), nonCombat, stepName);
    if (moveDescription == null) {
      if (GameStepPropertiesHelper.isRemoveAirThatCanNotLand(getGameData())
          && !canAirLand(true, gamePlayer)) {
        // continue with the move loop
        move(nonCombat, stepName);
      }
      if (!nonCombat && canUnitsFight()) {
        move(false, stepName);
      }
      return;
    }
    final String error = moveDel.performMove(moveDescription);
    if (error != null) {
      ui.notifyError(error);
    }
    move(nonCombat, stepName);
  }

  private boolean canAirLand(final boolean movePhase, final GamePlayer player) {
    final Collection<Territory> airCantLand;
    try {
      if (movePhase) {
        airCantLand =
            ((IMoveDelegate) getPlayerBridge().getRemoteDelegate())
                .getTerritoriesWhereAirCantLand(player);
      } else {
        airCantLand =
            ((IAbstractPlaceDelegate) getPlayerBridge().getRemoteDelegate())
                .getTerritoriesWhereAirCantLand();
      }
    } catch (final ClassCastException e) {
      final String errorContext =
          "PlayerBridge step name: "
              + getPlayerBridge().getStepName()
              + ", Remote class name: "
              + getPlayerBridge().getRemoteDelegate().getClass();
      log.log(Level.SEVERE, errorContext, e);
      throw new IllegalStateException(errorContext, e);
    }
    return airCantLand.isEmpty()
        || ui.getOkToLetAirDie(this.getGamePlayer(), airCantLand, movePhase);
  }

  private boolean canUnitsFight() {
    final Collection<Territory> unitsCantFight;
    try {
      unitsCantFight =
          ((IMoveDelegate) getPlayerBridge().getRemoteDelegate())
              .getTerritoriesWhereUnitsCantFight();
    } catch (final ClassCastException e) {
      final String errorContext =
          "PlayerBridge step name: "
              + getPlayerBridge().getStepName()
              + ", Remote class name: "
              + getPlayerBridge().getRemoteDelegate().getClass();
      log.log(Level.SEVERE, errorContext, e);
      throw new IllegalStateException(errorContext, e);
    }
    return !(unitsCantFight.isEmpty() || ui.getOkToLetUnitsDie(unitsCantFight));
  }

  private void purchase(final boolean bid) {
    if (getPlayerBridge().isGameOver()) {
      return;
    }

    final GamePlayer gamePlayer = this.getGamePlayer();
    // play a sound for this phase
    if (!bid && !soundPlayedAlreadyPurchase) {
      ClipPlayer.play(SoundPath.CLIP_PHASE_PURCHASE, gamePlayer);
      soundPlayedAlreadyPurchase = true;
    }
    // Check if any factories need to be repaired
    final GameData data = getGameData();
    final boolean isOnlyRepairIfDisabled = GameStepPropertiesHelper.isOnlyRepairIfDisabled(data);
    if (gamePlayer.getRepairFrontier() != null
        && gamePlayer.getRepairFrontier().getRules() != null
        && !gamePlayer.getRepairFrontier().getRules().isEmpty()
        && Properties.getDamageFromBombingDoneToUnitsInsteadOfTerritories(data.getProperties())) {
      Predicate<Unit> myDamaged =
          Matches.unitIsOwnedBy(gamePlayer).and(Matches.unitHasTakenSomeBombingUnitDamage());
      if (isOnlyRepairIfDisabled) {
        myDamaged = myDamaged.and(Matches.unitIsDisabled());
      }
      final Collection<Unit> damagedUnits = new ArrayList<>();
      for (final Territory t : data.getMap().getTerritories()) {
        damagedUnits.addAll(CollectionUtils.getMatches(t.getUnits(), myDamaged));
      }
      if (!damagedUnits.isEmpty()) {
        final Map<Unit, IntegerMap<RepairRule>> repair =
            ui.getRepair(
                gamePlayer, bid, GameStepPropertiesHelper.getRepairPlayers(data, gamePlayer));
        if (repair != null) {
          final IPurchaseDelegate purchaseDel;
          try {
            purchaseDel = (IPurchaseDelegate) getPlayerBridge().getRemoteDelegate();
          } catch (final ClassCastException e) {
            final String errorContext =
                "PlayerBridge step name: "
                    + getPlayerBridge().getStepName()
                    + ", Remote class name: "
                    + getPlayerBridge().getRemoteDelegate().getClass();
            // for some reason the client is not seeing or getting these errors, so print to err
            // too
            log.log(Level.SEVERE, errorContext, e);
            throw new IllegalStateException(errorContext, e);
          }
          final String error = purchaseDel.purchaseRepair(repair);
          if (error != null) {
            ui.notifyError(error);
            // don't give up, keep going
            purchase(bid);
          }
        }
      }
    }
    if (isOnlyRepairIfDisabled) {
      return;
    }
    final IntegerMap<ProductionRule> prod = ui.getProduction(gamePlayer, bid);
    if (prod == null) {
      return;
    }
    final IPurchaseDelegate purchaseDel;
    try {
      purchaseDel = (IPurchaseDelegate) getPlayerBridge().getRemoteDelegate();
    } catch (final ClassCastException e) {
      final String errorContext =
          "PlayerBridge step name: "
              + getPlayerBridge().getStepName()
              + ", Remote class name: "
              + getPlayerBridge().getRemoteDelegate().getClass();
      log.log(Level.SEVERE, errorContext, e);
      throw new IllegalStateException(errorContext, e);
    }
    final String purchaseError = purchaseDel.purchase(prod);
    if (purchaseError != null) {
      ui.notifyError(purchaseError);
      // don't give up, keep going
      purchase(bid);
    }
  }

  private void battle() {
    if (getPlayerBridge().isGameOver()) {
      return;
    }
    final IBattleDelegate battleDel;
    try {
      battleDel = (IBattleDelegate) getPlayerBridge().getRemoteDelegate();
    } catch (final ClassCastException e) {
      final String errorContext =
          "PlayerBridge step name: "
              + getPlayerBridge().getStepName()
              + ", Remote class name: "
              + getPlayerBridge().getRemoteDelegate().getClass();
      log.log(Level.SEVERE, errorContext, e);
      throw new IllegalStateException(errorContext, e);
    }

    final GamePlayer gamePlayer = this.getGamePlayer();
    while (true) {
      if (getPlayerBridge().isGameOver()) {
        return;
      }
      final BattleListing battles = battleDel.getBattles();
      if (battles.isEmpty()) {
        return;
      }
      if (!soundPlayedAlreadyBattle) {
        ClipPlayer.play(SoundPath.CLIP_PHASE_BATTLE, gamePlayer);
        soundPlayedAlreadyBattle = true;
      }
      final FightBattleDetails details = ui.getBattle(gamePlayer, battles.getBattles());
      if (getPlayerBridge().isGameOver()) {
        return;
      }
      if (details != null) {
        final String error =
            battleDel.fightBattle(
                details.getWhere(), details.isBombingRaid(), details.getBattleType());
        if (error != null) {
          ui.notifyError(error);
        }
      }
    }
  }

  private void place() {
    final boolean bid = GameStepPropertiesHelper.isBid(getGameData());
    if (getPlayerBridge().isGameOver()) {
      return;
    }
    final GamePlayer gamePlayer = this.getGamePlayer();
    final IAbstractPlaceDelegate placeDel;
    try {
      placeDel = (IAbstractPlaceDelegate) getPlayerBridge().getRemoteDelegate();
    } catch (final ClassCastException e) {
      final String errorContext =
          "PlayerBridge step name: "
              + getPlayerBridge().getStepName()
              + ", Remote class name: "
              + getPlayerBridge().getRemoteDelegate().getClass();
      log.log(Level.SEVERE, errorContext, e);
      throw new IllegalStateException(errorContext, e);
    }
    while (true) {
      if (!soundPlayedAlreadyPlacement) {
        ClipPlayer.play(SoundPath.CLIP_PHASE_PLACEMENT, gamePlayer);
        soundPlayedAlreadyPlacement = true;
      }
      final PlaceData placeData = ui.waitForPlace(gamePlayer, bid, getPlayerBridge());
      if (placeData == null) {
        // this only happens in lhtr rules
        if (!GameStepPropertiesHelper.isRemoveAirThatCanNotLand(getGameData())
            || canAirLand(false, gamePlayer)
            || getPlayerBridge().isGameOver()) {
          return;
        }
        continue;
      }
      final String error =
          placeDel.placeUnits(
              placeData.getUnits(),
              placeData.getAt(),
              bid ? IAbstractPlaceDelegate.BidMode.BID : IAbstractPlaceDelegate.BidMode.NOT_BID);
      if (error != null) {
        ui.notifyError(error);
      }
    }
  }

  private void endTurn() {
    if (getPlayerBridge().isGameOver()) {
      return;
    }
    final GameData data = getGameData();
    // play a sound for this phase
    final IAbstractForumPosterDelegate endTurnDelegate;
    try {
      endTurnDelegate = (IAbstractForumPosterDelegate) getPlayerBridge().getRemoteDelegate();
    } catch (final ClassCastException e) {
      final String errorContext =
          "PlayerBridge step name: "
              + getPlayerBridge().getStepName()
              + ", Remote class name: "
              + getPlayerBridge().getRemoteDelegate().getClass();
      log.log(Level.SEVERE, errorContext, e);
      throw new IllegalStateException(errorContext, e);
    }
    if (!soundPlayedAlreadyEndTurn
        && TerritoryAttachment.doWeHaveEnoughCapitalsToProduce(this.getGamePlayer(), data)) {
      // do not play if we are reloading a savegame from pbem (gets annoying)
      if (!endTurnDelegate.getHasPostedTurnSummary()) {
        ClipPlayer.play(SoundPath.CLIP_PHASE_END_TURN, this.getGamePlayer());
      }
      soundPlayedAlreadyEndTurn = true;
    }
    ui.waitForEndTurn(this.getGamePlayer(), getPlayerBridge());
  }

  @Override
  public CasualtyDetails selectCasualties(
      final Collection<Unit> selectFrom,
      final Map<Unit, Collection<Unit>> dependents,
      final int count,
      final String message,
      final DiceRoll dice,
      final GamePlayer hit,
      final Collection<Unit> friendlyUnits,
      final Collection<Unit> enemyUnits,
      final boolean amphibious,
      final Collection<Unit> amphibiousLandAttackers,
      final CasualtyList defaultCasualties,
      final UUID battleId,
      final Territory battlesite,
      final boolean allowMultipleHitsPerUnit) {
    return ui.getBattlePanel()
        .getCasualties(
            selectFrom,
            dependents,
            count,
            message,
            dice,
            hit,
            defaultCasualties,
            battleId,
            allowMultipleHitsPerUnit);
  }

  @Override
  public int[] selectFixedDice(
      final int numDice, final int hitAt, final String title, final int diceSides) {
    return ui.selectFixedDice(numDice, hitAt, title, diceSides);
  }

  @Override
  public Territory selectBombardingTerritory(
      final Unit unit,
      final Territory unitTerritory,
      final Collection<Territory> territories,
      final boolean noneAvailable) {
    return ui.getBattlePanel().getBombardment(unit, unitTerritory, territories, noneAvailable);
  }

  @Override
  public boolean selectAttackSubs(final Territory unitTerritory) {
    return ui.getBattlePanel().getAttackSubs(unitTerritory);
  }

  @Override
  public boolean selectAttackTransports(final Territory unitTerritory) {
    return ui.getBattlePanel().getAttackTransports(unitTerritory);
  }

  @Override
  public boolean selectAttackUnits(final Territory unitTerritory) {
    return ui.getBattlePanel().getAttackUnits(unitTerritory);
  }

  @Override
  public boolean selectShoreBombard(final Territory unitTerritory) {
    return ui.getBattlePanel().getShoreBombard(unitTerritory);
  }

  @Override
  public boolean shouldBomberBomb(final Territory territory) {
    return ui.getStrategicBombingRaid(territory);
  }

  @Override
  public Unit whatShouldBomberBomb(
      final Territory territory,
      final Collection<Unit> potentialTargets,
      final Collection<Unit> bombers) {
    return ui.getStrategicBombingRaidTarget(territory, potentialTargets, bombers);
  }

  @Override
  public Territory whereShouldRocketsAttack(
      final Collection<Territory> candidates, final Territory from) {
    return ui.getRocketAttack(candidates, from);
  }

  @Override
  public Collection<Unit> getNumberOfFightersToMoveToNewCarrier(
      final Collection<Unit> fightersThatCanBeMoved, final Territory from) {
    return ui.moveFightersToCarrier(fightersThatCanBeMoved, from);
  }

  @Override
  public Territory selectTerritoryForAirToLand(
      final Collection<Territory> candidates,
      final Territory currentTerritory,
      final String unitMessage) {
    return ui.selectTerritoryForAirToLand(candidates, currentTerritory, unitMessage);
  }

  @Override
  public boolean confirmMoveInFaceOfAa(final Collection<Territory> aaFiringTerritories) {
    final String question =
        "Your units will be fired on in: "
            + MyFormatter.defaultNamedToTextList(aaFiringTerritories)
            + ".  Do you still want to move?";
    return ui.getOk(question);
  }

  @Override
  public boolean confirmMoveKamikaze() {
    final String question =
        "Not all air units in destination territory can land, do you still want to move?";
    return ui.getOk(question);
  }

  @Override
  public Territory retreatQuery(
      final UUID battleId,
      final boolean submerge,
      final Territory battleTerritory,
      final Collection<Territory> possibleTerritories,
      final String message) {
    return ui.getBattlePanel().getRetreat(battleId, message, possibleTerritories, submerge);
  }

  @Override
  public Map<Territory, Collection<Unit>> scrambleUnitsQuery(
      final Territory scrambleTo,
      final Map<Territory, Tuple<Collection<Unit>, Collection<Unit>>> possibleScramblers) {
    return ui.scrambleUnitsQuery(scrambleTo, possibleScramblers);
  }

  @Override
  public Collection<Unit> selectUnitsQuery(
      final Territory current, final Collection<Unit> possible, final String message) {
    return ui.selectUnitsQuery(current, possible, message);
  }

  @Override
  public void confirmEnemyCasualties(
      final UUID battleId, final String message, final GamePlayer hitPlayer) {
    // no need, we have already confirmed since we are firing player
    if (ui.getLocalPlayers().playing(hitPlayer)) {
      return;
    }
    // we don't want to confirm enemy casualties
    if (!ClientSetting.confirmEnemyCasualties.getValueOrThrow()) {
      return;
    }
    ui.getBattlePanel().confirmCasualties(battleId, message);
  }

  @Override
  public void confirmOwnCasualties(final UUID battleId, final String message) {
    ui.getBattlePanel().confirmCasualties(battleId, message);
  }

  @Override
  public Map<Territory, Map<Unit, IntegerMap<Resource>>> selectKamikazeSuicideAttacks(
      final Map<Territory, Collection<Unit>> possibleUnitsToAttack) {
    final GamePlayer gamePlayer = this.getGamePlayer();
    final PlayerAttachment pa = PlayerAttachment.get(gamePlayer);
    if (pa == null) {
      return null;
    }
    final IntegerMap<Resource> resourcesAndAttackValues = pa.getSuicideAttackResources();
    if (resourcesAndAttackValues.isEmpty()) {
      return null;
    }
    final IntegerMap<Resource> playerResourceCollection =
        gamePlayer.getResources().getResourcesCopy();
    final IntegerMap<Resource> attackTokens = new IntegerMap<>();
    for (final Resource possible : resourcesAndAttackValues.keySet()) {
      final int amount = playerResourceCollection.getInt(possible);
      if (amount > 0) {
        attackTokens.put(possible, amount);
      }
    }
    if (attackTokens.isEmpty()) {
      return null;
    }
    final Map<Territory, Map<Unit, IntegerMap<Resource>>> kamikazeSuicideAttacks = new HashMap<>();
    for (final Entry<Resource, Integer> entry : attackTokens.entrySet()) {
      final Resource resource = entry.getKey();
      final int max = entry.getValue();
      final Map<Territory, IntegerMap<Unit>> selection =
          ui.selectKamikazeSuicideAttacks(possibleUnitsToAttack, resource, max);
      for (final Entry<Territory, IntegerMap<Unit>> selectionEntry : selection.entrySet()) {
        final Territory territory = selectionEntry.getKey();
        final Map<Unit, IntegerMap<Resource>> currentTerr =
            kamikazeSuicideAttacks.getOrDefault(territory, new HashMap<>());
        for (final Entry<Unit, Integer> unitEntry : selectionEntry.getValue().entrySet()) {
          final Unit unit = unitEntry.getKey();
          final IntegerMap<Resource> currentUnit =
              currentTerr.getOrDefault(unit, new IntegerMap<>());
          currentUnit.add(resource, unitEntry.getValue());
          currentTerr.put(unit, currentUnit);
        }
        kamikazeSuicideAttacks.put(territory, currentTerr);
      }
    }
    return kamikazeSuicideAttacks;
  }

  @Override
  public Tuple<Territory, Set<Unit>> pickTerritoryAndUnits(
      final List<Territory> territoryChoices,
      final List<Unit> unitChoices,
      final int unitsPerPick) {
    if (territoryChoices == null || territoryChoices.isEmpty() || unitsPerPick < 1) {
      return Tuple.of(null, new HashSet<>());
    }
    return ui.pickTerritoryAndUnits(
        this.getGamePlayer(), territoryChoices, unitChoices, unitsPerPick);
  }
}
