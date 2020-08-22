package games.strategy.triplea.delegate;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.RelationshipType;
import games.strategy.engine.data.Resource;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.engine.data.changefactory.ChangeFactory;
import games.strategy.engine.message.IRemote;
import games.strategy.triplea.Constants;
import games.strategy.triplea.Properties;
import games.strategy.triplea.delegate.battle.BattleTracker;
import games.strategy.triplea.delegate.remote.IEditDelegate;
import games.strategy.triplea.formatter.MyFormatter;
import games.strategy.triplea.util.TransportUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;
import org.triplea.java.collections.CollectionUtils;
import org.triplea.java.collections.IntegerMap;
import org.triplea.util.Triple;

/** Edit game state. */
public class EditDelegate extends BaseEditDelegate implements IEditDelegate {

  @Override
  public String removeUnits(final Territory territory, final Collection<Unit> units) {
    String result = checkEditMode();
    if (result != null) {
      return result;
    }

    result = EditValidator.validateRemoveUnits(getData(), territory, units);
    if (result != null) {
      return result;
    }
    if (units.isEmpty()) {
      return null;
    }
    final Collection<GamePlayer> owners = new HashSet<>();
    for (final Unit u : units) {
      owners.add(u.getOwner());
    }
    for (final GamePlayer p : owners) {
      final List<Unit> unitsOwned = CollectionUtils.getMatches(units, Matches.unitIsOwnedBy(p));
      logEvent(
          "Removing units owned by "
              + p.getName()
              + " from "
              + territory.getName()
              + ": "
              + MyFormatter.unitsToTextNoOwner(unitsOwned),
          unitsOwned);
      bridge.addChange(ChangeFactory.removeUnits(territory, unitsOwned));
    }
    return null;
  }

  @Override
  public String addUnits(final Territory territory, final Collection<Unit> units) {
    String result = checkEditMode();
    if (result != null) {
      return result;
    }

    result = EditValidator.validateAddUnits(getData(), territory, units);
    if (result != null) {
      return result;
    }
    if (units.isEmpty()) {
      return null;
    }
    // now make sure land units are put on transports properly
    final GamePlayer player = units.iterator().next().getOwner();
    final GameData data = getData();
    Map<Unit, Unit> mapLoading = null;
    if (territory.isWater()
        && (units.isEmpty() || !units.stream().allMatch(Matches.unitIsSea()))
        && units.stream().anyMatch(Matches.unitIsLand())) {
      // this should be exact same as the one in the EditValidator
      if (units.isEmpty() || !units.stream().allMatch(Matches.alliedUnit(player, data))) {
        return "Can't add mixed nationality units to water";
      }
      final Predicate<Unit> friendlySeaTransports =
          Matches.unitIsTransport().and(Matches.unitIsSea()).and(Matches.alliedUnit(player, data));
      final Collection<Unit> seaTransports =
          CollectionUtils.getMatches(units, friendlySeaTransports);
      final Collection<Unit> landUnitsToAdd =
          CollectionUtils.getMatches(units, Matches.unitIsLand());
      if (landUnitsToAdd.isEmpty()
          || !landUnitsToAdd.stream().allMatch(Matches.unitCanBeTransported())) {
        return "Can't add land units that can't be transported, to water";
      }
      seaTransports.addAll(territory.getUnitCollection().getMatches(friendlySeaTransports));
      if (seaTransports.isEmpty()) {
        return "Can't add land units to water without enough transports";
      }
      mapLoading = TransportUtils.mapTransportsToLoad(landUnitsToAdd, seaTransports);
      if (!mapLoading.keySet().containsAll(landUnitsToAdd)) {
        return "Can't add land units to water without enough transports";
      }
    }
    // now perform the changes
    logEvent(
        "Adding units owned by "
            + units.iterator().next().getOwner().getName()
            + " to "
            + territory.getName()
            + ": "
            + MyFormatter.unitsToTextNoOwner(units),
        units);
    bridge.addChange(ChangeFactory.addUnits(territory, units));
    if (Properties.getUnitsMayGiveBonusMovement(getData())
        && GameStepPropertiesHelper.isGiveBonusMovement(data)) {
      bridge.addChange(MoveDelegate.giveBonusMovementToUnits(player, data, territory));
    }
    if (mapLoading != null && !mapLoading.isEmpty()) {
      for (final Entry<Unit, Unit> entry : mapLoading.entrySet()) {
        bridge.addChange(TransportTracker.loadTransportChange(entry.getValue(), entry.getKey()));
      }
    }
    return null;
  }

  @Override
  public String changeTerritoryOwner(final Territory territory, final GamePlayer player) {
    String result = checkEditMode();
    if (result != null) {
      return result;
    }
    final GameData data = getData();
    // validate this edit
    result = EditValidator.validateChangeTerritoryOwner(data, territory);
    if (result != null) {
      return result;
    }
    logEvent(
        "Changing ownership of "
            + territory.getName()
            + " from "
            + territory.getOwner().getName()
            + " to "
            + player.getName(),
        territory);
    if (!data.getRelationshipTracker().isAtWar(territory.getOwner(), player)) {
      // change ownership of friendly factories
      final Collection<Unit> units =
          territory.getUnitCollection().getMatches(Matches.unitIsInfrastructure());
      bridge.addChange(ChangeFactory.changeOwner(units, player, territory));
    } else {
      final Predicate<Unit> enemyNonCom =
          Matches.unitIsInfrastructure().and(Matches.enemyUnit(player, data));
      final Collection<Unit> units = territory.getUnitCollection().getMatches(enemyNonCom);
      // mark no movement for enemy units
      bridge.addChange(ChangeFactory.markNoMovementChange(units));
      // change ownership of enemy AA and factories
      bridge.addChange(ChangeFactory.changeOwner(units, player, territory));
    }
    // change ownership of territory
    bridge.addChange(ChangeFactory.changeOwner(territory, player));
    return null;
  }

  @Override
  public String changePUs(final GamePlayer player, final int newTotal) {
    final String result = checkEditMode();
    if (result != null) {
      return result;
    }
    final Resource pus = getData().getResourceList().getResource(Constants.PUS);
    final int oldTotal = player.getResources().getQuantity(pus);
    if (oldTotal == newTotal) {
      return "New PUs total is unchanged";
    }
    if (newTotal < 0) {
      return "New PUs total is invalid";
    }
    logEvent(
        "Changing PUs for " + player.getName() + " from " + oldTotal + " to " + newTotal, null);
    bridge.addChange(ChangeFactory.changeResourcesChange(player, pus, (newTotal - oldTotal)));
    return null;
  }

  @Override
  public String addTechAdvance(final GamePlayer player, final Collection<TechAdvance> advances) {
    String result = checkEditMode();
    if (result != null) {
      return result;
    }
    if (null != (result = EditValidator.validateAddTech(getData(), advances, player))) {
      return result;
    }
    for (final TechAdvance advance : advances) {
      logEvent("Adding Technology " + advance.getName() + " for " + player.getName(), null);
      TechTracker.addAdvance(player, bridge, advance);
    }
    return null;
  }

  @Override
  public String removeTechAdvance(final GamePlayer player, final Collection<TechAdvance> advances) {
    String result = checkEditMode();
    if (result != null) {
      return result;
    }
    if (null != (result = EditValidator.validateRemoveTech(getData(), advances, player))) {
      return result;
    }
    for (final TechAdvance advance : advances) {
      logEvent("Removing Technology " + advance.getName() + " for " + player.getName(), null);
      TechTracker.removeAdvance(player, bridge, advance);
    }
    return null;
  }

  @Override
  public String changeUnitHitDamage(
      final IntegerMap<Unit> unitDamageMap, final Territory territory) {
    String result = checkEditMode();
    if (result != null) {
      return result;
    }
    result = EditValidator.validateChangeHitDamage(getData(), unitDamageMap, territory);
    if (result != null) {
      return result;
    }
    // remove anyone who is the same
    final Collection<Unit> units = new ArrayList<>(unitDamageMap.keySet());
    for (final Unit u : units) {
      final int dmg = unitDamageMap.getInt(u);
      if (u.getHits() == dmg) {
        unitDamageMap.removeKey(u);
      }
    }
    if (unitDamageMap.isEmpty()) {
      return null;
    }
    final Collection<Unit> unitsFinal = new ArrayList<>(unitDamageMap.keySet());
    logEvent(
        "Changing unit hit damage for these "
            + unitsFinal.iterator().next().getOwner().getName()
            + " owned units to: "
            + MyFormatter.integerUnitMapToString(unitDamageMap, ", ", " = ", false),
        unitsFinal);
    bridge.addChange(ChangeFactory.unitsHit(unitDamageMap, List.of(territory)));
    // territory.notifyChanged();
    return null;
  }

  @Override
  public String changeUnitBombingDamage(
      final IntegerMap<Unit> unitDamageMap, final Territory territory) {
    String result = checkEditMode();
    if (result != null) {
      return result;
    }
    result = EditValidator.validateChangeBombingDamage(getData(), unitDamageMap, territory);
    if (result != null) {
      return result;
    }
    // remove anyone who is the same
    final Collection<Unit> units = new ArrayList<>(unitDamageMap.keySet());
    for (final Unit u : units) {
      final int dmg = unitDamageMap.getInt(u);
      final int currentDamage = u.getUnitDamage();
      if (currentDamage == dmg) {
        unitDamageMap.removeKey(u);
      }
    }
    if (unitDamageMap.isEmpty()) {
      return null;
    }
    // we do damage to the unit
    final Collection<Unit> unitsFinal = new ArrayList<>(unitDamageMap.keySet());
    logEvent(
        "Changing unit bombing damage for these "
            + unitsFinal.iterator().next().getOwner().getName()
            + " owned units to: "
            + MyFormatter.integerUnitMapToString(unitDamageMap, ", ", " = ", false),
        unitsFinal);
    bridge.addChange(ChangeFactory.bombingUnitDamage(unitDamageMap));
    // territory.notifyChanged();
    return null;
  }

  @Override
  public String changePoliticalRelationships(
      final Collection<Triple<GamePlayer, GamePlayer, RelationshipType>> relationshipChanges) {
    if (relationshipChanges == null || relationshipChanges.isEmpty()) {
      return null;
    }
    String result = checkEditMode();
    if (result != null) {
      return result;
    }
    result = EditValidator.validateChangePoliticalRelationships(relationshipChanges);
    if (result != null) {
      return result;
    }
    final BattleTracker battleTracker = AbstractMoveDelegate.getBattleTracker(getData());
    for (final Triple<GamePlayer, GamePlayer, RelationshipType> relationshipChange :
        relationshipChanges) {
      final RelationshipType currentRelation =
          getData()
              .getRelationshipTracker()
              .getRelationshipType(relationshipChange.getFirst(), relationshipChange.getSecond());
      if (!currentRelation.equals(relationshipChange.getThird())) {
        logEvent(
            "Editing Political Relationship for "
                + relationshipChange.getFirst().getName()
                + " and "
                + relationshipChange.getSecond().getName()
                + " from "
                + currentRelation.getName()
                + " to "
                + relationshipChange.getThird().getName(),
            null);
        bridge.addChange(
            ChangeFactory.relationshipChange(
                relationshipChange.getFirst(),
                relationshipChange.getSecond(),
                currentRelation,
                relationshipChange.getThird()));
        battleTracker.addRelationshipChangesThisTurn(
            relationshipChange.getFirst(),
            relationshipChange.getSecond(),
            currentRelation,
            relationshipChange.getThird());
      }
    }
    return null;
  }

  @Override
  public Class<? extends IRemote> getRemoteType() {
    return IEditDelegate.class;
  }
}
