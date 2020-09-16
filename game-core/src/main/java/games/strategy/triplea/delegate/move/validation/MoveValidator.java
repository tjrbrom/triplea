package games.strategy.triplea.delegate.move.validation;

import com.google.common.collect.ImmutableMap;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.MoveDescription;
import games.strategy.engine.data.ResourceCollection;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.engine.data.UnitType;
import games.strategy.triplea.Constants;
import games.strategy.triplea.Properties;
import games.strategy.triplea.attachments.CanalAttachment;
import games.strategy.triplea.attachments.PlayerAttachment;
import games.strategy.triplea.attachments.RulesAttachment;
import games.strategy.triplea.attachments.TechAttachment;
import games.strategy.triplea.attachments.UnitAttachment;
import games.strategy.triplea.delegate.AbstractMoveDelegate;
import games.strategy.triplea.delegate.BaseEditDelegate;
import games.strategy.triplea.delegate.GameStepPropertiesHelper;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.delegate.MoveDelegate;
import games.strategy.triplea.delegate.TerritoryEffectHelper;
import games.strategy.triplea.delegate.TransportTracker;
import games.strategy.triplea.delegate.UndoableMove;
import games.strategy.triplea.delegate.UnitComparator;
import games.strategy.triplea.delegate.data.MoveValidationResult;
import games.strategy.triplea.delegate.data.MustMoveWithDetails;
import games.strategy.triplea.formatter.MyFormatter;
import games.strategy.triplea.util.TransportUtils;
import games.strategy.triplea.util.UnitCategory;
import games.strategy.triplea.util.UnitSeparator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.triplea.java.PredicateBuilder;
import org.triplea.java.collections.CollectionUtils;
import org.triplea.java.collections.IntegerMap;

/** Provides some static methods for validating movement. */
public class MoveValidator {

  public static final String TRANSPORT_HAS_ALREADY_UNLOADED_UNITS_IN_A_PREVIOUS_PHASE =
      "Transport has already unloaded units in a previous phase";
  public static final String
      TRANSPORT_MAY_NOT_UNLOAD_TO_FRIENDLY_TERRITORIES_UNTIL_AFTER_COMBAT_IS_RESOLVED =
          "Transport may not unload to friendly territories until after combat is resolved";
  public static final String ENEMY_SUBMARINE_PREVENTING_UNESCORTED_AMPHIBIOUS_ASSAULT_LANDING =
      "Enemy Submarine Preventing Unescorted Amphibious Assault Landing";
  public static final String TRANSPORT_HAS_ALREADY_UNLOADED_UNITS_TO =
      "Transport has already unloaded units to ";
  public static final String CANNOT_LOAD_AND_UNLOAD_AN_ALLIED_TRANSPORT_IN_THE_SAME_ROUND =
      "Cannot load and unload an allied transport in the same round";
  public static final String CANT_MOVE_THROUGH_IMPASSABLE =
      "Can't move through impassable territories";
  public static final String CANT_MOVE_THROUGH_RESTRICTED =
      "Can't move through restricted territories";
  public static final String TOO_POOR_TO_VIOLATE_NEUTRALITY =
      "Not enough money to pay for violating neutrality";
  public static final String CANNOT_VIOLATE_NEUTRALITY = "Cannot violate neutrality";
  public static final String NOT_ALL_AIR_UNITS_CAN_LAND = "Not all air units can land";
  public static final String TRANSPORT_CANNOT_LOAD_AND_UNLOAD_AFTER_COMBAT =
      "Transport cannot both load AND unload after being in combat";
  public static final String TRANSPORT_CANNOT_LOAD_AFTER_COMBAT =
      "Transport cannot load after being in combat";
  public static final String NOT_ALL_UNITS_CAN_BLITZ = "Not all units can blitz";

  private final GameData data;

  public MoveValidator(final GameData data) {
    this.data = data;
  }

  /** Validates the specified move. */
  public MoveValidationResult validateMove(
      final MoveDescription move,
      final GamePlayer player,
      final boolean isNonCombat,
      final List<UndoableMove> undoableMoves) {
    final Collection<Unit> units = move.getUnits();
    final Route route = move.getRoute();
    final Map<Unit, Unit> unitsToTransports = move.getUnitsToTransports();
    final Map<Unit, Collection<Unit>> newDependents = move.getDependentUnits();
    final MoveValidationResult result = new MoveValidationResult();
    if (route.hasNoSteps()) {
      return result;
    }
    if (validateFirst(units, route, player, result).getError() != null) {
      return result;
    }
    if (isNonCombat) {
      if (validateNonCombat(units, route, player, result).getError() != null) {
        return result;
      }
    } else {
      if (validateCombat(units, route, player, result).getError() != null) {
        return result;
      }
    }
    if (validateNonEnemyUnitsOnPath(units, route, player, result).getError() != null) {
      return result;
    }
    if (validateBasic(units, route, player, unitsToTransports, newDependents, result).getError()
        != null) {
      return result;
    }
    if (AirMovementValidator.validateAirCanLand(units, route, player, result).getError() != null) {
      return result;
    }
    if (validateTransport(
                isNonCombat, undoableMoves, units, route, player, unitsToTransports, result)
            .getError()
        != null) {
      return result;
    }
    if (validateParatroops(isNonCombat, units, route, player, result).getError() != null) {
      return result;
    }
    if (validateCanal(units, route, player, newDependents, result).getError() != null) {
      return result;
    }
    if (validateFuel(units, route, player, result).getError() != null) {
      return result;
    }

    // Don't let the user move out of a battle zone, the exception is air units and unloading units
    // into a battle zone
    if (AbstractMoveDelegate.getBattleTracker(data).hasPendingNonBombingBattle(route.getStart())
        && units.stream().anyMatch(Matches.unitIsNotAir())) {
      // if the units did not move into the territory, then they can move out this will happen if
      // there is a submerged
      // sub in the area, and a different unit moved into the sea zone setting up a battle but the
      // original unit can
      // still remain
      boolean unitsStartedInTerritory = true;
      for (final Unit unit : units) {
        if (AbstractMoveDelegate.getRouteUsedToMoveInto(undoableMoves, unit, route.getEnd())
            != null) {
          unitsStartedInTerritory = false;
          break;
        }
      }
      if (!unitsStartedInTerritory) {
        final boolean unload = route.isUnload();
        final GamePlayer endOwner = route.getEnd().getOwner();
        final boolean attack =
            !data.getRelationshipTracker().isAllied(endOwner, player)
                || AbstractMoveDelegate.getBattleTracker(data).wasConquered(route.getEnd());
        // unless they are unloading into another battle
        if (!(unload && attack)) {
          return result.setErrorReturnResult("Cannot move units out of battle zone");
        }
      }
    }
    return result;
  }

  public MoveValidationResult validateFirst(
      final Collection<Unit> units,
      final Route route,
      final GamePlayer player,
      final MoveValidationResult result) {
    if (!units.isEmpty() && !getEditMode(data)) {
      final Collection<Unit> matches =
          CollectionUtils.getMatches(
              units,
              Matches.unitIsBeingTransportedByOrIsDependentOfSomeUnitInThisList(
                      units, player, data, true)
                  .negate());
      if (matches.isEmpty() || !matches.stream().allMatch(Matches.unitIsOwnedBy(player))) {
        result.setError(
            "Player, "
                + player.getName()
                + ", is not owner of all the units: "
                + MyFormatter.unitsToTextNoOwner(units));
        return result;
      }
    }
    // this should never happen
    if (new HashSet<>(units).size() != units.size()) {
      result.setError("Not all units unique, units:" + units + " unique:" + new HashSet<>(units));
      return result;
    }
    if (!data.getMap().isValidRoute(route)) {
      result.setError("Invalid route:" + route);
      return result;
    }
    if (validateMovementRestrictedByTerritory(route, player, result).getError() != null) {
      return result;
    }
    // cannot enter territories owned by a player to which we are neutral towards
    final Collection<Territory> landOnRoute = route.getMatches(Matches.territoryIsLand());
    if (!landOnRoute.isEmpty()) {
      // TODO: if this ever changes, we need to also update getBestRoute(), because getBestRoute is
      // also checking to
      // make sure we avoid land territories owned by nations with these 2 relationship type
      // attachment options
      for (final Territory t : landOnRoute) {
        if (units.stream().anyMatch(Matches.unitIsLand())
            && !data.getRelationshipTracker().canMoveLandUnitsOverOwnedLand(player, t.getOwner())) {
          result.setError(
              player.getName()
                  + " may not move land units over land owned by "
                  + t.getOwner().getName());
          return result;
        }
        if (units.stream().anyMatch(Matches.unitIsAir())
            && !data.getRelationshipTracker().canMoveAirUnitsOverOwnedLand(player, t.getOwner())) {
          result.setError(
              player.getName()
                  + " may not move air units over land owned by "
                  + t.getOwner().getName());
          return result;
        }
      }
    }
    if (units.isEmpty()) {
      return result.setErrorReturnResult("No units");
    }
    for (final Unit unit : units) {
      if (unit.getSubmerged()) {
        result.addDisallowedUnit("Cannot move submerged units", unit);
      } else if (Matches.unitIsDisabled().test(unit)) {
        result.addDisallowedUnit("Cannot move disabled units", unit);
      }
    }
    // make sure all units are actually in the start territory
    if (!route.getStart().getUnitCollection().containsAll(units)) {
      return result.setErrorReturnResult("Not enough units in starting territory");
    }
    return result;
  }

  public MoveValidationResult validateFuel(
      final Collection<Unit> units,
      final Route route,
      final GamePlayer player,
      final MoveValidationResult result) {
    if (getEditMode(data)) {
      return result;
    }
    if (!Properties.getUseFuelCost(data)) {
      return result;
    }
    final ResourceCollection fuelCost = Route.getMovementFuelCostCharge(units, route, player, data);
    if (player.getResources().has(fuelCost.getResourcesCopy())) {
      return result;
    }
    return result.setErrorReturnResult(
        "Not enough resources to perform this move, you need: " + fuelCost + " for this move");
  }

  private MoveValidationResult validateCanal(
      final Collection<Unit> units,
      final Route route,
      final GamePlayer player,
      final Map<Unit, Collection<Unit>> newDependents,
      final MoveValidationResult result) {
    if (getEditMode(data)) {
      return result;
    }
    // TODO: merge validateCanal here and provide granular unit warnings
    return result.setErrorReturnResult(validateCanal(route, units, newDependents, player));
  }

  /**
   * Test a route's canals to see if you can move through it.
   *
   * @param units (Can be null. If null we will assume all units would be stopped by the canal.)
   */
  public String validateCanal(
      final Route route, final Collection<Unit> units, final GamePlayer player) {
    return validateCanal(route, units, new HashMap<>(), player);
  }

  String validateCanal(
      final Route route,
      final Collection<Unit> units,
      final Map<Unit, Collection<Unit>> newDependents,
      final GamePlayer player) {
    // Check each unit 1 by 1 to see if they can move through necessary canals on route
    String result = null;
    final Set<Unit> unitsThatFailCanal = new HashSet<>();
    final Set<Unit> setWithNull = new HashSet<>();
    setWithNull.add(null);
    final Collection<Unit> unitsWithoutDependents =
        (units == null) ? setWithNull : findNonDependentUnits(units, route, newDependents);
    for (final Unit unit : unitsWithoutDependents) {
      for (final Territory t : route.getAllTerritories()) {
        Optional<String> failureMessage = Optional.empty();
        for (final CanalAttachment canalAttachment : CanalAttachment.get(t)) {
          if (!CanalAttachment.isCanalOnRoute(canalAttachment.getCanalName(), route)) {
            continue; // Only check canals that are on the route
          }
          failureMessage = canPassThroughCanal(canalAttachment, unit, player);
          final boolean canPass = failureMessage.isEmpty();
          if ((!Properties.getControlAllCanalsBetweenTerritoriesToPass(data) && canPass)
              || (Properties.getControlAllCanalsBetweenTerritoriesToPass(data) && !canPass)) {
            break; // If need to control any canal and can pass OR need to control all canals and
            // can't pass
          }
        }
        if (failureMessage.isPresent()) {
          result = failureMessage.get();
          unitsThatFailCanal.add(unit);
        }
      }
    }
    if (result == null || units == null) {
      return result;
    }

    // If any units failed canal check then try to land transport them
    final Set<Unit> potentialLandTransports =
        unitsWithoutDependents.stream()
            .filter(
                unit ->
                    !unitsThatFailCanal.contains(unit)
                        && Matches.unitHasEnoughMovementForRoute(route).test(unit))
            .collect(Collectors.toSet());
    final Set<Unit> unitsToLandTransport =
        unitsWithoutDependents.stream()
            .filter(
                unit ->
                    unitsThatFailCanal.contains(unit)
                        || !Matches.unitHasEnoughMovementForRoute(route).test(unit))
            .collect(Collectors.toSet());
    return checkLandTransports(player, potentialLandTransports, unitsToLandTransport).isEmpty()
        ? null
        : result;
  }

  /**
   * Simplified version of {@link #validateCanal(Route, Collection, GamePlayer) validateCanal} used
   * for route finding to check neighboring territories and avoid validating every unit. Performance
   * of this method is critical.
   */
  public boolean canAnyUnitsPassCanal(
      final Territory start,
      final Territory end,
      final Collection<Unit> units,
      final GamePlayer player) {
    boolean canPass = true;
    final Route route = new Route(start, end);
    for (final CanalAttachment canalAttachment : CanalAttachment.get(start)) {
      if (!CanalAttachment.isCanalOnRoute(canalAttachment.getCanalName(), route)) {
        continue; // Only check canals that are on the route
      }
      final Collection<Unit> unitsWithoutDependents =
          findNonDependentUnits(units, route, new HashMap<>());
      canPass = canAnyPassThroughCanal(canalAttachment, unitsWithoutDependents, player).isEmpty();
      if ((!Properties.getControlAllCanalsBetweenTerritoriesToPass(data) && canPass)
          || (Properties.getControlAllCanalsBetweenTerritoriesToPass(data) && !canPass)) {
        break; // If need to control any canal and can pass OR need to control all canals and can't
        // pass
      }
    }
    return canPass;
  }

  private MoveValidationResult validateCombat(
      final Collection<Unit> units,
      final Route route,
      final GamePlayer player,
      final MoveValidationResult result) {
    if (getEditMode(data)) {
      return result;
    }
    for (final Territory t : route.getSteps()) {
      if (!Matches.territoryOwnerRelationshipTypeCanMoveIntoDuringCombatMove(player).test(t)) {
        return result.setErrorReturnResult(
            "Cannot move into territories owned by "
                + t.getOwner().getName()
                + " during Combat Movement Phase");
      }
    }

    // We are in a contested territory owned by the enemy, and we want to move to another enemy
    // owned territory. do not allow unless each unit can blitz the current territory.
    if (!route.getStart().isWater()
        && Matches.isAtWar(route.getStart().getOwner(), data).test(player)
        && (route.anyMatch(Matches.isTerritoryEnemy(player, data))
            && !route.allMatchMiddleSteps(Matches.isTerritoryEnemy(player, data).negate()))) {
      if (!Matches.territoryIsBlitzable(player, data).test(route.getStart())
          && (units.isEmpty() || !units.stream().allMatch(Matches.unitIsAir()))) {
        return result.setErrorReturnResult(
            "Cannot blitz out of a battle further into enemy territory");
      }
      for (final Unit u :
          CollectionUtils.getMatches(
              units, Matches.unitCanBlitz().negate().and(Matches.unitIsNotAir()))) {
        result.addDisallowedUnit("Not all units can blitz out of empty enemy territory", u);
      }
    }

    // We are in a contested territory owned by us, and we want to move to an enemy owned territory.
    // Do not allow unless the territory is blitzable.
    if (!route.getStart().isWater()
        && !Matches.isAtWar(route.getStart().getOwner(), data).test(player)
        && (route.anyMatch(Matches.isTerritoryEnemy(player, data))
            && !route.allMatchMiddleSteps(Matches.isTerritoryEnemy(player, data).negate()))
        && !Matches.territoryIsBlitzable(player, data).test(route.getStart())
        && (units.isEmpty() || !units.stream().allMatch(Matches.unitIsAir()))) {
      return result.setErrorReturnResult("Cannot blitz out of a battle into enemy territory");
    }

    // Don't allow aa guns (and other disallowed units) to move in combat unless they are in a
    // transport
    if (units.stream().anyMatch(Matches.unitCanNotMoveDuringCombatMove())
        && (!route.getStart().isWater() || !route.getEnd().isWater())) {
      for (final Unit unit :
          CollectionUtils.getMatches(units, Matches.unitCanNotMoveDuringCombatMove())) {
        result.addDisallowedUnit("Cannot move AA guns in combat movement phase", unit);
      }
    }

    // If there is a neutral in the middle must stop unless all are air or getNeutralsBlitzable
    if (nonAirPassingThroughNeutralTerritory(route, units, data)) {
      return result.setErrorReturnResult(
          "Must stop land units when passing through neutral territories");
    }
    if (units.stream().anyMatch(Matches.unitIsLand()) && route.hasSteps()) {
      // Check all the territories but the end, if there are enemy territories, make sure they are
      // blitzable
      // if they are not blitzable, or we aren't all blitz units fail
      int enemyCount = 0;
      boolean allEnemyBlitzable = true;
      for (final Territory current : route.getMiddleSteps()) {
        if (current.isWater()) {
          continue;
        }
        if (data.getRelationshipTracker().isAtWar(current.getOwner(), player)
            || AbstractMoveDelegate.getBattleTracker(data).wasConquered(current)) {
          enemyCount++;
          allEnemyBlitzable &= Matches.territoryIsBlitzable(player, data).test(current);
        }
      }
      if (enemyCount > 0 && !allEnemyBlitzable) {
        if (nonParatroopersPresent(player, units)) {
          return result.setErrorReturnResult("Cannot blitz on that route");
        }
      } else if (allEnemyBlitzable && !(route.getStart().isWater() || route.getEnd().isWater())) {
        final Predicate<Unit> blitzingUnit = Matches.unitCanBlitz().or(Matches.unitIsAir());
        final Predicate<Unit> nonBlitzing = blitzingUnit.negate();
        final Collection<Unit> nonBlitzingUnits = CollectionUtils.getMatches(units, nonBlitzing);
        // remove any units that gain blitz due to certain abilities
        nonBlitzingUnits.removeAll(
            UnitAttachment.getUnitsWhichReceivesAbilityWhenWith(units, "canBlitz", data));
        final Predicate<Territory> territoryIsNotEnd = Matches.territoryIs(route.getEnd()).negate();
        final Predicate<Territory> nonFriendlyTerritories =
            Matches.isTerritoryFriendly(player, data).negate();
        final Predicate<Territory> notEndOrFriendlyTerrs =
            nonFriendlyTerritories.and(territoryIsNotEnd);
        final Predicate<Territory> foughtOver =
            Matches.territoryWasFoughtOver(AbstractMoveDelegate.getBattleTracker(data));
        final Predicate<Territory> notEndWasFought = territoryIsNotEnd.and(foughtOver);
        final boolean wasStartFoughtOver =
            AbstractMoveDelegate.getBattleTracker(data).wasConquered(route.getStart())
                || AbstractMoveDelegate.getBattleTracker(data).wasBlitzed(route.getStart());
        nonBlitzingUnits.addAll(
            CollectionUtils.getMatches(
                units,
                Matches.unitIsOfTypes(
                    TerritoryEffectHelper.getUnitTypesThatLostBlitz(
                        (wasStartFoughtOver ? route.getAllTerritories() : route.getSteps())))));
        for (final Unit unit : nonBlitzingUnits) {
          // TODO: Need to actually test if the unit is being air transported or land transported
          if ((Matches.unitIsAirTransportable().test(unit)
                  && units.stream().anyMatch(Matches.unitIsAirTransport()))
              || (Matches.unitIsLandTransportable().test(unit)
                  && units.stream().anyMatch(Matches.unitIsLandTransport()))) {
            continue;
          }
          if (wasStartFoughtOver
              || unit.getWasInCombat()
              || route.anyMatch(notEndOrFriendlyTerrs)
              || route.anyMatch(notEndWasFought)) {
            result.addDisallowedUnit(NOT_ALL_UNITS_CAN_BLITZ, unit);
          }
        }
      }
    }
    // check aircraft
    if (units.stream().anyMatch(Matches.unitIsAir())
        && route.hasSteps()
        && (!Properties.getNeutralFlyoverAllowed(data) || Properties.getNeutralsImpassable(data))
        && route.getMiddleSteps().stream().anyMatch(Matches.territoryIsNeutralButNotWater())) {
      return result.setErrorReturnResult("Air units cannot fly over neutral territories");
    }
    // make sure no conquered territories on route
    // unless we are all air or we are in non combat OR the route is water (was a bug in convoy
    // zone movement)
    if (hasConqueredNonBlitzedNonWaterOnRoute(route, data)
        && (units.isEmpty() || !units.stream().allMatch(Matches.unitIsAir()))) {
      // what if we are paratroopers?
      return result.setErrorReturnResult("Cannot move through newly captured territories");
    }
    // See if they've already been in combat
    if (units.stream().anyMatch(Matches.unitWasInCombat())
        && units.stream().anyMatch(Matches.unitWasUnloadedThisTurn())
        && Matches.isTerritoryEnemyAndNotUnownedWaterOrImpassableOrRestricted(player, data)
            .test(route.getEnd())
        && !route.getEnd().getUnitCollection().isEmpty()) {
      return result.setErrorReturnResult("Units cannot participate in multiple battles");
    }
    // See if we are doing invasions in combat phase, with units or transports that can't do
    // invasion.
    if (route.isUnload() && Matches.isTerritoryEnemy(player, data).test(route.getEnd())) {
      for (final Unit unit : CollectionUtils.getMatches(units, Matches.unitCanInvade().negate())) {
        result.addDisallowedUnit(
            unit.getType().getName()
                + " can't invade from "
                + unit.getTransportedBy().getType().getName(),
            unit);
      }
    }
    return result;
  }

  private static boolean nonAirPassingThroughNeutralTerritory(
      final Route route, final Collection<Unit> units, final GameData gameData) {
    return route.hasNeutralBeforeEnd()
        && (units.isEmpty() || !units.stream().allMatch(Matches.unitIsAir()))
        && !isNeutralsBlitzable(gameData);
  }

  private MoveValidationResult validateNonCombat(
      final Collection<Unit> units,
      final Route route,
      final GamePlayer player,
      final MoveValidationResult result) {
    if (getEditMode(data)) {
      return result;
    }
    if (route.anyMatch(Matches.territoryIsImpassable())) {
      return result.setErrorReturnResult(CANT_MOVE_THROUGH_IMPASSABLE);
    }
    if (!route.anyMatch(Matches.territoryIsPassableAndNotRestricted(player, data))) {
      return result.setErrorReturnResult(CANT_MOVE_THROUGH_RESTRICTED);
    }
    final Predicate<Territory> neutralOrEnemy =
        Matches.territoryIsNeutralButNotWater()
            .or(Matches.isTerritoryEnemyAndNotUnownedWaterOrImpassableOrRestricted(player, data));
    final boolean navalMayNotNonComIntoControlled =
        Properties.getWW2V2(data)
            || Properties.getNavalUnitsMayNotNonCombatMoveIntoControlledSeaZones(data);
    // TODO need to account for subs AND transports that are ignored, not just OR
    final Territory end = route.getEnd();
    if (neutralOrEnemy.test(end)) {
      // a convoy zone is controlled, so we must make sure we can still move there if there are
      // actual battle there
      if (!end.isWater() || navalMayNotNonComIntoControlled) {
        return result.setErrorReturnResult("Cannot advance units to battle in non combat");
      }
    }
    // Subs can't move under destroyers
    if (!units.isEmpty()
        && units.stream().allMatch(Matches.unitCanMoveThroughEnemies())
        && enemyDestroyerOnPath(route, player)) {
      return result.setErrorReturnResult("Cannot move submarines under destroyers");
    }
    // Can't advance to battle unless only ignored units on route, only air units to sea, or only
    // units that can enter
    // territories with enemy units during NCM
    if (end.getUnitCollection()
            .anyMatch(Matches.enemyUnit(player, data).and(Matches.unitIsSubmerged().negate()))
        && !onlyIgnoredUnitsOnPath(route, player, false)
        && !(end.isWater() && units.stream().allMatch(Matches.unitIsAir()))
        && !(Properties.getSubsCanEndNonCombatMoveWithEnemies(data)
            && units.stream().allMatch(Matches.unitCanMoveThroughEnemies()))) {
      return result.setErrorReturnResult("Cannot advance to battle in non combat");
    }
    // if there are enemy units on the path blocking us, that is validated elsewhere
    // (validateNonEnemyUnitsOnPath)
    // now check if we can move over neutral or enemies territories in noncombat
    if ((!units.isEmpty() && units.stream().allMatch(Matches.unitIsAir()))
        || (units.stream().noneMatch(Matches.unitIsSea())
            && !nonParatroopersPresent(player, units))) {
      // if there are non-paratroopers present, then we cannot fly over stuff
      // if there are neutral territories in the middle, we cannot fly over (unless allowed to)
      // otherwise we can generally fly over anything in noncombat
      if (route.anyMatch(
              Matches.territoryIsNeutralButNotWater().and(Matches.territoryIsWater().negate()))
          && (!Properties.getNeutralFlyoverAllowed(data)
              || Properties.getNeutralsImpassable(data))) {
        return result.setErrorReturnResult(
            "Air units cannot fly over neutral territories in non combat");
      }
      // if sea units, or land units moving over/onto sea (ex: loading onto a transport), then only
      // check if old rules stop us
    } else if (units.stream().anyMatch(Matches.unitIsSea())
        || route.anyMatch(Matches.territoryIsWater())) {
      // if there are neutral or owned territories, we cannot move through them (only under old
      // rules. under new rules we can move through owned sea zones.)
      if (navalMayNotNonComIntoControlled && route.anyMatch(neutralOrEnemy)) {
        return result.setErrorReturnResult(
            "Cannot move units through neutral or enemy territories in non combat");
      }
    } else {
      if (route.anyMatch(neutralOrEnemy)) {
        return result.setErrorReturnResult(
            "Cannot move units through neutral or enemy territories in non combat");
      }
    }
    return result;
  }

  // Added to handle restriction of movement to listed territories
  private MoveValidationResult validateMovementRestrictedByTerritory(
      final Route route, final GamePlayer player, final MoveValidationResult result) {
    if (getEditMode(data)) {
      return result;
    }
    if (!Properties.getMovementByTerritoryRestricted(data)) {
      return result;
    }
    final RulesAttachment ra =
        (RulesAttachment) player.getAttachment(Constants.RULES_ATTACHMENT_NAME);
    if (ra == null || ra.getMovementRestrictionTerritories() == null) {
      return result;
    }
    final String movementRestrictionType = ra.getMovementRestrictionType();
    final Collection<Territory> listedTerritories =
        ra.getListedTerritories(ra.getMovementRestrictionTerritories(), true, true);
    if (movementRestrictionType.equals("allowed")) {
      for (final Territory current : route.getAllTerritories()) {
        if (!listedTerritories.contains(current)) {
          return result.setErrorReturnResult("Cannot move outside restricted territories");
        }
      }
    } else if (movementRestrictionType.equals("disallowed")) {
      for (final Territory current : route.getAllTerritories()) {
        if (listedTerritories.contains(current)) {
          return result.setErrorReturnResult("Cannot move to restricted territories");
        }
      }
    }
    return result;
  }

  private MoveValidationResult validateNonEnemyUnitsOnPath(
      final Collection<Unit> units,
      final Route route,
      final GamePlayer player,
      final MoveValidationResult result) {
    if (getEditMode(data)) {
      return result;
    }
    // check to see no enemy units on path
    if (noEnemyUnitsOnPathMiddleSteps(route, player)) {
      return result;
    }
    // if we are all air, then its ok
    if (!units.isEmpty() && units.stream().allMatch(Matches.unitIsAir())) {
      return result;
    }
    // subs may possibly carry units...
    final Collection<Unit> matches =
        CollectionUtils.getMatches(units, Matches.unitIsBeingTransported().negate());
    if (!matches.isEmpty() && matches.stream().allMatch(Matches.unitCanMoveThroughEnemies())) {
      // this is ok unless there are destroyer on the path
      return enemyDestroyerOnPath(route, player)
          ? result.setErrorReturnResult("Cannot move submarines under destroyers")
          : result;
    }
    if (onlyIgnoredUnitsOnPath(route, player, true)) {
      return result;
    }
    // omit paratroops
    if (nonParatroopersPresent(player, units)) {
      return result.setErrorReturnResult("Enemy units on path");
    }
    return result;
  }

  private MoveValidationResult validateBasic(
      final Collection<Unit> units,
      final Route route,
      final GamePlayer player,
      final Map<Unit, Unit> unitToTransport,
      final Map<Unit, Collection<Unit>> newDependents,
      final MoveValidationResult result) {
    final boolean isEditMode = getEditMode(data);
    // make sure transports in the destination
    if (!route.getEnd().getUnitCollection().containsAll(unitToTransport.values())
        && !units.containsAll(unitToTransport.values())) {
      return result.setErrorReturnResult("Transports not found in route end");
    }
    if (!isEditMode) {

      // Make sure all units are at least friendly
      for (final Unit unit : CollectionUtils.getMatches(units, Matches.enemyUnit(player, data))) {
        result.addDisallowedUnit("Can only move friendly units", unit);
      }

      // Ensure all air transports are included
      for (final Unit airTransport : newDependents.keySet()) {
        if (!units.contains(airTransport)) {
          for (final Unit unit : newDependents.get(airTransport)) {
            if (units.contains(unit)) {
              result.addDisallowedUnit("Not all units have enough movement", unit);
            }
          }
        }
      }

      // Check that units have enough movement considering land transports
      final Collection<Unit> unitsWithoutDependents =
          findNonDependentUnits(units, route, newDependents);
      final Set<Unit> unitsWithEnoughMovement =
          unitsWithoutDependents.stream()
              .filter(unit -> Matches.unitHasEnoughMovementForRoute(route).test(unit))
              .collect(Collectors.toSet());
      final Set<Unit> unitsWithoutEnoughMovement =
          unitsWithoutDependents.stream()
              .filter(unit -> !Matches.unitHasEnoughMovementForRoute(route).test(unit))
              .collect(Collectors.toSet());
      checkLandTransports(player, unitsWithEnoughMovement, unitsWithoutEnoughMovement)
          .forEach(unit -> result.addDisallowedUnit("Not all units have enough movement", unit));

      // Can only move owned units except transported units or allied air on carriers
      for (final Unit unit :
          CollectionUtils.getMatches(
              unitsWithoutDependents, Matches.unitIsOwnedBy(player).negate())) {
        if (!(UnitAttachment.get(unit.getType()).getCarrierCost() > 0
            && data.getRelationshipTracker().isAllied(player, unit.getOwner()))) {
          result.addDisallowedUnit("Can only move own troops", unit);
        }
      }

      // if there is a neutral in the middle must stop unless all are air or getNeutralsBlitzable
      if (nonAirPassingThroughNeutralTerritory(route, units, data)) {
        return result.setErrorReturnResult(
            "Must stop land units when passing through neutral territories");
      }
      // a territory effect can disallow unit types in
      if (units.stream()
          .anyMatch(
              Matches.unitIsOfTypes(
                  TerritoryEffectHelper.getUnitTypesForUnitsNotAllowedIntoTerritory(
                      route.getSteps())))) {
        return result.setErrorReturnResult(
            "Territory Effects disallow some units into "
                + (route.numberOfSteps() > 1 ? "these territories" : "this territory"));
      }
      // Check requiresUnitsToMove conditions
      Collection<Unit> requiresUnitsToMoveList = unitsWithoutDependents;
      if (route.isUnload()) {
        requiresUnitsToMoveList = units;
      }
      for (final Territory t : route.getAllTerritories()) {
        if (!requiresUnitsToMoveList.stream()
            .allMatch(Matches.unitHasRequiredUnitsToMove(t, data))) {
          return result.setErrorReturnResult(
              t.getName()
                  + " doesn't have the required units to allow moving the selected units into it");
        }
      }
    } // !isEditMode

    // make sure that no non sea non transportable no carriable units end at sea
    if (route.getEnd().isWater()) {
      for (final Unit unit : getUnitsThatCantGoOnWater(units)) {
        result.addDisallowedUnit("Not all units can end at water", unit);
      }
    }
    // if we are water make sure no land
    if (units.stream().anyMatch(Matches.unitIsSea())) {
      if (route.hasLand()) {
        for (final Unit unit : CollectionUtils.getMatches(units, Matches.unitIsSea())) {
          result.addDisallowedUnit("Sea units cannot go on land", unit);
        }
      }
    }
    // test for stack limits per unit
    final Collection<Unit> unitsWithStackingLimits =
        CollectionUtils.getMatches(
            units, Matches.unitHasMovementLimit().or(Matches.unitHasAttackingLimit()));
    for (final Territory t : route.getSteps()) {
      final Collection<Unit> unitsAllowedSoFar = new ArrayList<>();
      if (Matches.isTerritoryEnemyAndNotUnownedWater(player, data).test(t)
          || t.getUnitCollection().anyMatch(Matches.unitIsEnemyOf(data, player))) {
        for (final Unit unit : unitsWithStackingLimits) {
          final UnitType ut = unit.getType();
          int maxAllowed =
              UnitAttachment.getMaximumNumberOfThisUnitTypeToReachStackingLimit(
                  "attackingLimit", ut, t, player, data);
          maxAllowed -= CollectionUtils.countMatches(unitsAllowedSoFar, Matches.unitIsOfType(ut));
          if (maxAllowed > 0) {
            unitsAllowedSoFar.add(unit);
          } else {
            result.addDisallowedUnit(
                "UnitType " + ut.getName() + " has reached stacking limit", unit);
          }
        }
        if (!PlayerAttachment.getCanTheseUnitsMoveWithoutViolatingStackingLimit(
            "attackingLimit", units, t, player, data)) {
          return result.setErrorReturnResult("Units Cannot Go Over Stacking Limit");
        }
      } else {
        for (final Unit unit : unitsWithStackingLimits) {
          final UnitType ut = unit.getType();
          int maxAllowed =
              UnitAttachment.getMaximumNumberOfThisUnitTypeToReachStackingLimit(
                  "movementLimit", ut, t, player, data);
          maxAllowed -= CollectionUtils.countMatches(unitsAllowedSoFar, Matches.unitIsOfType(ut));
          if (maxAllowed > 0) {
            unitsAllowedSoFar.add(unit);
          } else {
            result.addDisallowedUnit(
                "UnitType " + ut.getName() + " has reached stacking limit", unit);
          }
        }
        if (!PlayerAttachment.getCanTheseUnitsMoveWithoutViolatingStackingLimit(
            "movementLimit", units, t, player, data)) {
          return result.setErrorReturnResult("Units Cannot Go Over Stacking Limit");
        }
      }
    }

    // Don't allow move through impassable territories
    if (!isEditMode && route.anyMatch(Matches.territoryIsImpassable())) {
      return result.setErrorReturnResult(CANT_MOVE_THROUGH_IMPASSABLE);
    }
    if (canCrossNeutralTerritory(route, player, result).getError() != null) {
      return result;
    }
    if (Properties.getNeutralsImpassable(data)
        && !isNeutralsBlitzable(data)
        && !route.getMatches(Matches.territoryIsNeutralButNotWater()).isEmpty()) {
      return result.setErrorReturnResult(CANNOT_VIOLATE_NEUTRALITY);
    }
    return result;
  }

  private static Collection<Unit> findNonDependentUnits(
      final Collection<Unit> units,
      final Route route,
      final Map<Unit, Collection<Unit>> newDependents) {
    Collection<Unit> unitsWithoutDependents = new ArrayList<>(units);
    if (route.getStart().isWater()) {
      unitsWithoutDependents = getNonLand(units);
    }
    final Map<Unit, Collection<Unit>> dependentsMap =
        getDependents(CollectionUtils.getMatches(units, Matches.unitCanTransport()));
    final Set<Unit> dependents =
        dependentsMap.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
    dependents.addAll(
        newDependents.values().stream().flatMap(Collection::stream).collect(Collectors.toSet()));
    unitsWithoutDependents.removeAll(dependents);
    return unitsWithoutDependents;
  }

  /**
   * Returns any units that couldn't be land transported and handles both styles of land transports:
   * 1. Transport units on a 1-to-1 basis (have no capacity set) 2. Transport like sea transports
   * using capacity and cost
   */
  private Set<Unit> checkLandTransports(
      final GamePlayer player,
      final Collection<Unit> possibleLandTransports,
      final Set<Unit> unitsToLandTransport) {
    final Set<Unit> disallowedUnits = new HashSet<>();
    data.acquireReadLock();
    try {
      int numLandTransportsWithoutCapacity =
          getNumLandTransportsWithoutCapacity(possibleLandTransports, player);
      final IntegerMap<Unit> landTransportsWithCapacity =
          getLandTransportsWithCapacity(possibleLandTransports, player);
      for (final Unit unit : TransportUtils.sortByTransportCostDescending(unitsToLandTransport)) {
        boolean unitOk = false;
        if (Matches.unitHasNotMoved().test(unit) && Matches.unitIsLandTransportable().test(unit)) {
          if (numLandTransportsWithoutCapacity > 0) {
            numLandTransportsWithoutCapacity--;
            unitOk = true;
          } else {
            for (final Unit transport : landTransportsWithCapacity.keySet()) {
              final int cost = UnitAttachment.get(unit.getType()).getTransportCost();
              if (cost <= landTransportsWithCapacity.getInt(transport)) {
                landTransportsWithCapacity.add(transport, -cost);
                unitOk = true;
                break;
              }
            }
          }
        }
        if (!unitOk) {
          disallowedUnits.add(unit);
        }
      }
    } finally {
      data.releaseReadLock();
    }
    return disallowedUnits;
  }

  private static int getNumLandTransportsWithoutCapacity(
      final Collection<Unit> units, final GamePlayer player) {
    if (TechAttachment.isMechanizedInfantry(player)) {
      final Predicate<Unit> transportLand =
          Matches.unitIsLandTransportWithoutCapacity().and(Matches.unitIsOwnedBy(player));
      return CollectionUtils.countMatches(units, transportLand);
    }
    return 0;
  }

  private static IntegerMap<Unit> getLandTransportsWithCapacity(
      final Collection<Unit> units, final GamePlayer player) {
    final IntegerMap<Unit> map = new IntegerMap<>();
    if (TechAttachment.isMechanizedInfantry(player)) {
      final Predicate<Unit> transportLand =
          Matches.unitIsLandTransportWithCapacity().and(Matches.unitIsOwnedBy(player));
      for (final Unit unit : CollectionUtils.getMatches(units, transportLand)) {
        map.put(unit, UnitAttachment.get(unit.getType()).getTransportCapacity());
      }
    }
    return map;
  }

  public static Map<Unit, Collection<Unit>> getDependents(final Collection<Unit> units) {
    // just worry about transports
    final Map<Unit, Collection<Unit>> dependents = new HashMap<>();
    for (final Unit unit : units) {
      dependents.put(unit, TransportTracker.transporting(unit));
    }
    return dependents;
  }

  /**
   * Checks that there are no enemy units on the route except possibly at the end. Submerged enemy
   * units are not considered as they don't affect movement. AA and factory dont count as enemy.
   */
  private boolean noEnemyUnitsOnPathMiddleSteps(final Route route, final GamePlayer player) {
    final Predicate<Unit> alliedOrNonCombat =
        Matches.unitIsInfrastructure()
            .or(Matches.enemyUnit(player, data).negate())
            .or(Matches.unitIsSubmerged());
    // Submerged units do not interfere with movement
    return route.getMiddleSteps().stream()
        .allMatch(current -> current.getUnitCollection().allMatch(alliedOrNonCombat));
  }

  /**
   * Checks that there only transports, subs and/or allies on the route except at the end. AA and
   * factory dont count as enemy.
   */
  public boolean onlyIgnoredUnitsOnPath(
      final Route route, final GamePlayer player, final boolean ignoreRouteEnd) {
    final Predicate<Unit> transportOnly =
        Matches.unitIsInfrastructure()
            .or(Matches.unitIsTransportButNotCombatTransport())
            .or(Matches.unitIsLand())
            .or(Matches.enemyUnit(player, data).negate());
    final Predicate<Unit> subOnly =
        Matches.unitIsInfrastructure()
            .or(Matches.unitCanBeMovedThroughByEnemies())
            .or(Matches.enemyUnit(player, data).negate());
    final Predicate<Unit> transportOrSubOnly = transportOnly.or(subOnly);
    final boolean getIgnoreTransportInMovement = Properties.getIgnoreTransportInMovement(data);
    final List<Territory> steps;
    if (ignoreRouteEnd) {
      steps = route.getMiddleSteps();
    } else {
      steps = route.getSteps();
    }
    // if there are no steps, then we began in this sea zone, so see if there are ignored units in
    // this sea zone (not sure if we need !ignoreRouteEnd here).
    if (steps.isEmpty() && !ignoreRouteEnd) {
      steps.add(route.getStart());
    }
    boolean validMove = false;
    for (final Territory current : steps) {
      if (current.isWater()) {
        if ((getIgnoreTransportInMovement
                && current.getUnitCollection().allMatch(transportOrSubOnly))
            || current.getUnitCollection().allMatch(subOnly)) {
          validMove = true;
          continue;
        }
        return false;
      }
    }
    return validMove;
  }

  private boolean enemyDestroyerOnPath(final Route route, final GamePlayer player) {
    final Predicate<Unit> enemyDestroyer =
        Matches.unitIsDestroyer().and(Matches.enemyUnit(player, data));
    return route.getMiddleSteps().stream()
        .anyMatch(current -> current.getUnitCollection().anyMatch(enemyDestroyer));
  }

  private static boolean getEditMode(final GameData data) {
    return BaseEditDelegate.getEditMode(data);
  }

  private static boolean hasConqueredNonBlitzedNonWaterOnRoute(
      final Route route, final GameData data) {
    for (final Territory current : route.getMiddleSteps()) {
      if (!Matches.territoryIsWater().test(current)
          && AbstractMoveDelegate.getBattleTracker(data).wasConquered(current)
          && !AbstractMoveDelegate.getBattleTracker(data).wasBlitzed(current)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns {@code true} if moving the specified units along the specified route requires them to
   * be loaded/unloaded on/off a transport.
   */
  // TODO KEV revise these to include paratroop load/unload
  public static boolean isLoad(
      final Collection<Unit> units,
      final Map<Unit, Collection<Unit>> newDependents,
      final Route route,
      final GamePlayer player) {
    final Map<Unit, Collection<Unit>> alreadyLoaded =
        mustMoveWith(route.getStart(), units, newDependents, player);
    if (route.hasNoSteps() && alreadyLoaded.isEmpty()) {
      return false;
    }
    // See if we even need to go to the trouble of checking for AirTransported units
    final boolean checkForAlreadyTransported = !route.getStart().isWater() && route.hasWater();
    if (checkForAlreadyTransported) {
      // TODO Leaving unitIsTransport() for potential use with amphib transports (hovercraft, ducks,
      // etc...)
      final List<Unit> transports =
          CollectionUtils.getMatches(
              units, Matches.unitIsTransport().or(Matches.unitIsAirTransport()));
      final List<Unit> transportable =
          CollectionUtils.getMatches(
              units, Matches.unitCanBeTransported().or(Matches.unitIsAirTransportable()));
      // Check if there are transports in the group to be checked
      if (alreadyLoaded.keySet().containsAll(transports)) {
        // Check each transportable unit -vs those already loaded.
        for (final Unit unit : transportable) {
          boolean found = false;
          for (final Unit transport : transports) {
            if (alreadyLoaded.get(transport) == null
                || alreadyLoaded.get(transport).contains(unit)) {
              found = true;
              break;
            }
          }
          if (!found) {
            return true;
          }
        }
      } else {
        // TODO I think this is right
        return true;
      }
    }
    return false;
  }

  private static Collection<Unit> getUnitsThatCantGoOnWater(final Collection<Unit> units) {
    final Collection<Unit> retUnits = new ArrayList<>();
    for (final Unit unit : units) {
      final UnitAttachment ua = UnitAttachment.get(unit.getType());
      if (!ua.getIsSea() && !ua.getIsAir() && ua.getTransportCost() == -1) {
        retUnits.add(unit);
      }
    }
    return retUnits;
  }

  static boolean hasUnitsThatCantGoOnWater(final Collection<Unit> units) {
    return !getUnitsThatCantGoOnWater(units).isEmpty();
  }

  private static Collection<Unit> getNonLand(final Collection<Unit> units) {
    return CollectionUtils.getMatches(units, Matches.unitIsAir().or(Matches.unitIsSea()));
  }

  public static BigDecimal getMaxMovement(final Collection<Unit> units) {
    if (units.isEmpty()) {
      throw new IllegalArgumentException("no units");
    }
    BigDecimal max = BigDecimal.ZERO;
    for (final Unit unit : units) {
      final BigDecimal left = unit.getMovementLeft();
      max = left.max(max);
    }
    return max;
  }

  static BigDecimal getLeastMovement(final Collection<Unit> units) {
    if (units.isEmpty()) {
      throw new IllegalArgumentException("no units");
    }
    BigDecimal least = new BigDecimal(Integer.MAX_VALUE);
    for (final Unit unit : units) {
      final BigDecimal left = unit.getMovementLeft();
      least = left.min(least);
    }
    return least;
  }

  // Determines whether we can pay the neutral territory charge for a given route for air units. We
  // can't cross neutral
  // territories in WW2V2.
  private MoveValidationResult canCrossNeutralTerritory(
      final Route route, final GamePlayer player, final MoveValidationResult result) {
    // neutrals we will overfly in the first place
    final Collection<Territory> neutrals = MoveDelegate.getEmptyNeutral(route);
    final int pus = player.isNull() ? 0 : player.getResources().getQuantity(Constants.PUS);
    if (pus < getNeutralCharge(data, neutrals.size())) {
      return result.setErrorReturnResult(TOO_POOR_TO_VIOLATE_NEUTRALITY);
    }
    return result;
  }

  private static Territory getTerritoryTransportHasUnloadedTo(
      final List<UndoableMove> undoableMoves, final Unit transport) {
    for (final UndoableMove undoableMove : undoableMoves) {
      if (undoableMove.wasTransportUnloaded(transport)) {
        return undoableMove.getRoute().getEnd();
      }
    }
    return null;
  }

  private MoveValidationResult validateTransport(
      final boolean isNonCombat,
      final List<UndoableMove> undoableMoves,
      final Collection<Unit> units,
      final Route route,
      final GamePlayer player,
      final Map<Unit, Unit> unitsToTransports,
      final MoveValidationResult result) {
    final boolean isEditMode = getEditMode(data);
    if (!units.isEmpty() && units.stream().allMatch(Matches.unitIsAir())) {
      return result;
    }
    if (!route.hasWater()) {
      return result;
    }
    // If there are non-sea transports return
    final boolean loadingNonSeaTransportsOnly =
        !unitsToTransports.isEmpty()
            && unitsToTransports.values().stream()
                .noneMatch(Matches.unitIsSea().and(Matches.unitCanTransport()));
    if (loadingNonSeaTransportsOnly) {
      return result;
    }
    final Territory routeEnd = route.getEnd();
    final Territory routeStart = route.getStart();
    // if unloading make sure length of route is only 1
    if (!isEditMode && route.isUnload()) {
      if (route.hasMoreThenOneStep()) {
        return result.setErrorReturnResult("Unloading units must stop where they are unloaded");
      }
      for (final Unit unit : TransportTracker.getUnitsLoadedOnAlliedTransportsThisTurn(units)) {
        result.addDisallowedUnit(
            CANNOT_LOAD_AND_UNLOAD_AN_ALLIED_TRANSPORT_IN_THE_SAME_ROUND, unit);
      }
      final Collection<Unit> transports = TransportUtils.mapTransports(route, units, null).values();
      final boolean isScramblingOrKamikazeAttacksEnabled =
          Properties.getScrambleRulesInEffect(data)
              || Properties.getUseKamikazeSuicideAttacks(data);
      final boolean subsPreventUnescortedAmphibAssaults =
          Properties.getSubmarinesPreventUnescortedAmphibiousAssaults(data);
      final Predicate<Unit> enemySubMatch =
          Matches.unitIsEnemyOf(data, player).and(Matches.unitCanBeMovedThroughByEnemies());
      final Predicate<Unit> ownedSeaNonTransportMatch =
          Matches.unitIsOwnedBy(player)
              .and(Matches.unitIsSea())
              .and(Matches.unitIsNotTransportButCouldBeCombatTransport());
      for (final Unit transport : transports) {
        if (!isNonCombat) {
          if (Matches.territoryHasEnemyUnits(player, data).test(routeEnd)
              || Matches.isTerritoryEnemyAndNotUnownedWater(player, data).test(routeEnd)) {
            // this is an amphibious assault
            if (subsPreventUnescortedAmphibAssaults
                && !Matches.territoryHasUnitsThatMatch(ownedSeaNonTransportMatch).test(routeStart)
                && Matches.territoryHasUnitsThatMatch(enemySubMatch).test(routeStart)) {
              // we must have at least one warship (non-transport) unit, otherwise the enemy sub
              // stops our unloading for amphibious assault
              for (final Unit unit : TransportTracker.transporting(transport)) {
                result.addDisallowedUnit(
                    ENEMY_SUBMARINE_PREVENTING_UNESCORTED_AMPHIBIOUS_ASSAULT_LANDING, unit);
              }
            }
          } else if (!AbstractMoveDelegate.getBattleTracker(data).wasConquered(routeEnd)) {
            // this is an unload to a friendly territory
            if (isScramblingOrKamikazeAttacksEnabled
                || !Matches.territoryIsEmptyOfCombatUnits(data, player).test(routeStart)) {
              // Unloading a transport from a sea zone with a battle, to a friendly land territory,
              // during combat move phase, is illegal and in addition to being illegal, it is also
              // causing problems if
              // the sea transports get killed (the land units are not dying)
              // TODO: should we use the battle tracker for this instead?
              for (final Unit unit : TransportTracker.transporting(transport)) {
                result.addDisallowedUnit(
                    TRANSPORT_MAY_NOT_UNLOAD_TO_FRIENDLY_TERRITORIES_UNTIL_AFTER_COMBAT_IS_RESOLVED,
                    unit);
              }
            }
          }
        }
        // TODO This is very sensitive to the order of the transport collection. The users may need
        // to modify the order
        // in which they perform their actions. check whether transport has already unloaded
        if (TransportTracker.hasTransportUnloadedInPreviousPhase(transport)) {
          for (final Unit unit : TransportTracker.transporting(transport)) {
            result.addDisallowedUnit(
                TRANSPORT_HAS_ALREADY_UNLOADED_UNITS_IN_A_PREVIOUS_PHASE, unit);
          }
          // check whether transport is restricted to another territory
        } else if (TransportTracker.isTransportUnloadRestrictedToAnotherTerritory(
            transport, route.getEnd())) {
          final Territory alreadyUnloadedTo =
              getTerritoryTransportHasUnloadedTo(undoableMoves, transport);
          if (alreadyUnloadedTo != null) {
            for (final Unit unit : TransportTracker.transporting(transport)) {
              result.addDisallowedUnit(
                  TRANSPORT_HAS_ALREADY_UNLOADED_UNITS_TO + alreadyUnloadedTo.getName(), unit);
            }
          }
          // Check if the transport has already loaded after being in combat
        } else if (TransportTracker.isTransportUnloadRestrictedInNonCombat(transport)) {
          for (final Unit unit : TransportTracker.transporting(transport)) {
            result.addDisallowedUnit(TRANSPORT_CANNOT_LOAD_AND_UNLOAD_AFTER_COMBAT, unit);
          }
        }
      }
    }
    // if we are land make sure no water in route except for transport situations
    final Collection<Unit> land = CollectionUtils.getMatches(units, Matches.unitIsLand());
    final Collection<Unit> landAndAir =
        CollectionUtils.getMatches(units, Matches.unitIsLand().or(Matches.unitIsAir()));
    // make sure we can be transported
    final Predicate<Unit> cantBeTransported = Matches.unitCanBeTransported().negate();
    for (final Unit unit : CollectionUtils.getMatches(land, cantBeTransported)) {
      result.addDisallowedUnit("Not all units can be transported", unit);
    }
    // make sure that the only the first or last territory is land don't want situation where they
    // go sea land sea
    if (!isEditMode
        && route.hasLand()
        && !(route.getStart().isWater() || route.getEnd().isWater())) {
      // needs to include all land and air to work, since it makes sure the land units can be
      // carried by the air and that the air has enough capacity
      if (nonParatroopersPresent(player, landAndAir)) {
        return result.setErrorReturnResult(
            "Invalid move, only start or end can be land when route has water.");
      }
    }
    // simply because I dont want to handle it yet checks are done at the start and end, dont want
    // to worry about just
    // using a transport as a bridge yet
    // TODO handle this
    if (!isEditMode
        && !route.getEnd().isWater()
        && !route.getStart().isWater()
        && nonParatroopersPresent(player, landAndAir)) {
      return result.setErrorReturnResult("Must stop units at a transport on route");
    }
    if (route.getEnd().isWater() && route.getStart().isWater()) {
      // make sure units and transports stick together
      for (final Unit unit : units) {
        final UnitAttachment ua = UnitAttachment.get(unit.getType());
        // make sure transports dont leave their units behind
        if (ua.getTransportCapacity() != -1) {
          final Collection<Unit> holding = TransportTracker.transporting(unit);
          if (!units.containsAll(holding)) {
            result.addDisallowedUnit("Transports cannot leave their units", unit);
          }
        }
        // make sure units dont leave their transports behind
        if (ua.getTransportCost() != -1) {
          final Unit transport = unit.getTransportedBy();
          if (transport != null && !units.contains(transport)) {
            result.addDisallowedUnit("Unit must stay with its transport while moving", unit);
          }
        }
      }
    }
    if (route.isLoad()) {
      if (!isEditMode && !route.hasExactlyOneStep() && nonParatroopersPresent(player, landAndAir)) {
        return result.setErrorReturnResult("Units cannot move before loading onto transports");
      }
      final Predicate<Unit> enemyNonSubmerged =
          Matches.enemyUnit(player, data).and(Matches.unitIsSubmerged().negate());
      if (!Properties.getUnitsCanLoadInHostileSeaZones(data)
          && route.getEnd().getUnitCollection().anyMatch(enemyNonSubmerged)
          && nonParatroopersPresent(player, landAndAir)
          && !onlyIgnoredUnitsOnPath(route, player, false)
          && !AbstractMoveDelegate.getBattleTracker(data)
              .didAllThesePlayersJustGoToWarThisTurn(player, route.getEnd().getUnits(), data)) {
        return result.setErrorReturnResult("Cannot load when enemy sea units are present");
      }
      if (!isEditMode) {
        for (final Unit baseUnit : land) {
          if (baseUnit.hasMoved()) {
            result.addDisallowedUnit("Units cannot move before loading onto transports", baseUnit);
          }
          final Unit transport = unitsToTransports.get(baseUnit);
          if (transport == null) {
            continue;
          }
          if (TransportTracker.hasTransportUnloadedInPreviousPhase(transport)) {
            result.addDisallowedUnit(
                TRANSPORT_HAS_ALREADY_UNLOADED_UNITS_IN_A_PREVIOUS_PHASE, baseUnit);
          } else if (TransportTracker.isTransportLoadRestrictedAfterCombat(transport)) {
            result.addDisallowedUnit(TRANSPORT_CANNOT_LOAD_AFTER_COMBAT, baseUnit);
          } else if (TransportTracker.isTransportUnloadRestrictedToAnotherTerritory(
              transport, route.getEnd())) {
            Territory alreadyUnloadedTo =
                getTerritoryTransportHasUnloadedTo(undoableMoves, transport);
            for (final Unit transportToLoad : unitsToTransports.values()) {
              if (!TransportTracker.isTransportUnloadRestrictedToAnotherTerritory(
                  transportToLoad, route.getEnd())) {
                final UnitAttachment ua = UnitAttachment.get(baseUnit.getType());
                if (TransportTracker.getAvailableCapacity(transportToLoad)
                    >= ua.getTransportCost()) {
                  alreadyUnloadedTo = null;
                  break;
                }
              }
            }
            if (alreadyUnloadedTo != null) {
              result.addDisallowedUnit(
                  TRANSPORT_HAS_ALREADY_UNLOADED_UNITS_TO + alreadyUnloadedTo.getName(), baseUnit);
            }
          }
        }
      }
      if (!unitsToTransports.keySet().containsAll(land)) {
        // some units didn't get mapped to a transport
        final Collection<UnitCategory> unitsToLoadCategories = UnitSeparator.categorize(land);
        if (unitsToTransports.isEmpty() || unitsToLoadCategories.size() == 1) {
          // set all unmapped units as disallowed if there are no transports or only one unit
          // category
          for (final Unit unit : land) {
            if (unitsToTransports.containsKey(unit)) {
              continue;
            }
            final UnitAttachment ua = UnitAttachment.get(unit.getType());
            if (ua.getTransportCost() != -1) {
              result.addDisallowedUnit("Not enough transports", unit);
            }
          }
        } else {
          // set all units as unresolved if there is at least one transport and mixed unit
          // categories
          for (final Unit unit : land) {
            final UnitAttachment ua = UnitAttachment.get(unit.getType());
            if (ua.getTransportCost() != -1) {
              result.addUnresolvedUnit("Not enough transports", unit);
            }
          }
        }
      }
    }
    return result;
  }

  public static boolean allLandUnitsAreBeingParatroopered(final Collection<Unit> units) {
    // some units that can't be paratrooped
    if (units.isEmpty()
        || !units.stream()
            .allMatch(
                Matches.unitIsAirTransportable()
                    .or(Matches.unitIsAirTransport())
                    .or(Matches.unitIsAir()))) {
      return false;
    }
    // final List<Unit> paratroopsRequiringTransport = getParatroopsRequiringTransport(units,
    // route);
    // due to various problems with units like tanks, we will assume that if we are in this method,
    // then all the land units need transports
    final List<Unit> paratroopsRequiringTransport =
        CollectionUtils.getMatches(units, Matches.unitIsAirTransportable());
    if (paratroopsRequiringTransport.isEmpty()) {
      return false;
    }
    final List<Unit> airTransports =
        CollectionUtils.getMatches(units, Matches.unitIsAirTransport());
    final List<Unit> allParatroops =
        TransportUtils.findUnitsToLoadOnAirTransports(paratroopsRequiringTransport, airTransports);
    if (!allParatroops.containsAll(paratroopsRequiringTransport)) {
      return false;
    }
    final Map<Unit, Unit> transportLoadMap =
        TransportUtils.mapTransportsToLoad(units, airTransports);
    return transportLoadMap.keySet().containsAll(paratroopsRequiringTransport);
  }

  // checks if there are non-paratroopers present that cause move validations to fail
  private static boolean nonParatroopersPresent(
      final GamePlayer player, final Collection<Unit> units) {
    if (!TechAttachment.isAirTransportable(player)) {
      return true;
    }
    if (units.isEmpty() || !units.stream().allMatch(Matches.unitIsAir().or(Matches.unitIsLand()))) {
      return true;
    }
    for (final Unit unit : CollectionUtils.getMatches(units, Matches.unitIsNotAirTransportable())) {
      if (Matches.unitIsLand().test(unit)) {
        return true;
      }
    }
    return !allLandUnitsAreBeingParatroopered(units);
  }

  private static List<Unit> getParatroopsRequiringTransport(
      final Collection<Unit> units, final Route route) {
    return CollectionUtils.getMatches(
        units,
        Matches.unitIsAirTransportable()
            .and(
                u ->
                    (u.getMovementLeft().compareTo(route.getMovementCost(u)) < 0)
                        || route.crossesWater()
                        || route.getEnd().isWater()));
  }

  private MoveValidationResult validateParatroops(
      final boolean nonCombat,
      final Collection<Unit> units,
      final Route route,
      final GamePlayer player,
      final MoveValidationResult result) {
    if (!TechAttachment.isAirTransportable(player)) {
      return result;
    }
    if (units.stream().noneMatch(Matches.unitIsAirTransportable())
        || units.stream().noneMatch(Matches.unitIsAirTransport())) {
      return result;
    }
    if (nonCombat && !Properties.getParatroopersCanMoveDuringNonCombat(data)) {
      return result.setErrorReturnResult("Paratroops may not move during NonCombat");
    }
    if (!getEditMode(data)) {
      // if we can move without using paratroop tech, do so this allows moving a bomber/infantry
      // from one friendly
      // territory to another
      final List<Unit> paratroopsRequiringTransport = getParatroopsRequiringTransport(units, route);
      if (paratroopsRequiringTransport.isEmpty()) {
        return result;
      }
      final List<Unit> airTransports =
          CollectionUtils.getMatches(units, Matches.unitIsAirTransport());
      // TODO kev change below to mapAirTransports (or modify mapTransports to handle air cargo)
      // Map<Unit, Unit> airTransportsAndParatroops = MoveDelegate.mapTransports(route,
      // paratroopsRequiringTransport,
      // airTransports);
      final Map<Unit, Unit> airTransportsAndParatroops =
          TransportUtils.mapTransportsToLoad(paratroopsRequiringTransport, airTransports);
      for (final Unit paratroop : airTransportsAndParatroops.keySet()) {
        if (paratroop.hasMoved()) {
          result.addDisallowedUnit("Cannot paratroop units that have already moved", paratroop);
        }
        final Unit transport = airTransportsAndParatroops.get(paratroop);
        if (transport.hasMoved()) {
          result.addDisallowedUnit("Cannot move then transport paratroops", transport);
        }
      }
      final Territory routeEnd = route.getEnd();
      for (final Unit paratroop : paratroopsRequiringTransport) {
        if (paratroop.hasMoved()) {
          result.addDisallowedUnit("Cannot paratroop units that have already moved", paratroop);
        }
        if (Matches.isTerritoryFriendly(player, data).test(routeEnd)
            && !Properties.getParatroopersCanMoveDuringNonCombat(data)) {
          result.addDisallowedUnit("Paratroops must advance to battle", paratroop);
        }
        if (!nonCombat
            && Matches.isTerritoryFriendly(player, data).test(routeEnd)
            && Properties.getParatroopersCanMoveDuringNonCombat(data)) {
          result.addDisallowedUnit(
              "Paratroops may only airlift during Non-Combat Movement Phase", paratroop);
        }
      }
      if (!Properties.getParatroopersCanAttackDeepIntoEnemyTerritory(data)) {
        for (final Territory current :
            CollectionUtils.getMatches(route.getMiddleSteps(), Matches.territoryIsLand())) {
          if (Matches.isTerritoryEnemy(player, data).test(current)) {
            return result.setErrorReturnResult("Must stop paratroops in first enemy territory");
          }
        }
      }
    }
    return result;
  }

  private Optional<String> canPassThroughCanal(
      final CanalAttachment canalAttachment, final Unit unit, final GamePlayer player) {
    if (unit != null && Matches.unitIsOfTypes(canalAttachment.getExcludedUnits()).test(unit)) {
      return Optional.empty();
    }
    return checkCanalStepAndOwnership(canalAttachment, player);
  }

  private Optional<String> canAnyPassThroughCanal(
      final CanalAttachment canalAttachment,
      final Collection<Unit> units,
      final GamePlayer player) {
    if (units.stream().anyMatch(Matches.unitIsOfTypes(canalAttachment.getExcludedUnits()))) {
      return Optional.empty();
    }
    return checkCanalStepAndOwnership(canalAttachment, player);
  }

  private Optional<String> checkCanalStepAndOwnership(
      final CanalAttachment canalAttachment, final GamePlayer player) {
    if (canalAttachment.getCanNotMoveThroughDuringCombatMove()
        && GameStepPropertiesHelper.isCombatMove(data, true)) {
      return Optional.of(
          "Can only move through " + canalAttachment.getCanalName() + " during non-combat move");
    }
    for (final Territory borderTerritory : canalAttachment.getLandTerritories()) {
      if (!data.getRelationshipTracker().canMoveThroughCanals(player, borderTerritory.getOwner())) {
        return Optional.of("Must control " + canalAttachment.getCanalName() + " to move through");
      }
      if (AbstractMoveDelegate.getBattleTracker(data).wasConquered(borderTerritory)) {
        return Optional.of(
            "Must control "
                + canalAttachment.getCanalName()
                + " for an entire turn to move through");
      }
    }
    return Optional.empty();
  }

  public static MustMoveWithDetails getMustMoveWith(
      final Territory start,
      final Map<Unit, Collection<Unit>> newDependents,
      final GamePlayer player) {
    return new MustMoveWithDetails(mustMoveWith(start, start.getUnits(), newDependents, player));
  }

  private static Map<Unit, Collection<Unit>> mustMoveWith(
      final Territory start,
      final Collection<Unit> units,
      final Map<Unit, Collection<Unit>> newDependents,
      final GamePlayer player) {
    final GameData data = start.getData();
    final List<Unit> sortedUnits = new ArrayList<>(units);
    sortedUnits.sort(UnitComparator.getHighestToLowestMovementComparator());
    final Map<Unit, Collection<Unit>> mapping = new HashMap<>(transportsMustMoveWith(sortedUnits));
    // Check if there are combined transports (carriers that are transports) and load them.
    addToMapping(mapping, carrierMustMoveWith(sortedUnits, start, data, player));
    addToMapping(mapping, airTransportsMustMoveWith(sortedUnits, newDependents));
    return mapping;
  }

  private static void addToMapping(
      final Map<Unit, Collection<Unit>> mapping, final Map<Unit, Collection<Unit>> newMapping) {
    if (mapping.isEmpty()) {
      mapping.putAll(newMapping);
      return;
    }
    for (final Unit key : newMapping.keySet()) {
      if (mapping.containsKey(key)) {
        final Collection<Unit> heldUnits = new ArrayList<>(mapping.get(key));
        heldUnits.addAll(newMapping.get(key));
        mapping.put(key, heldUnits);
      } else {
        mapping.put(key, newMapping.get(key));
      }
    }
  }

  private static Map<Unit, Collection<Unit>> transportsMustMoveWith(final Collection<Unit> units) {
    final Map<Unit, Collection<Unit>> mustMoveWith = new HashMap<>();
    final Collection<Unit> transports =
        CollectionUtils.getMatches(units, Matches.unitIsTransport());
    for (final Unit transport : transports) {
      final Collection<Unit> transporting = TransportTracker.transporting(transport);
      mustMoveWith.put(transport, transporting);
    }
    return mustMoveWith;
  }

  private static Map<Unit, Collection<Unit>> airTransportsMustMoveWith(
      final Collection<Unit> units, final Map<Unit, Collection<Unit>> newDependents) {
    final Map<Unit, Collection<Unit>> mustMoveWith = new HashMap<>();
    final Collection<Unit> airTransports =
        CollectionUtils.getMatches(units, Matches.unitIsAirTransport());
    // Then check those that have already had their transportedBy set
    for (final Unit airTransport : airTransports) {
      if (!mustMoveWith.containsKey(airTransport)) {
        Collection<Unit> transporting = TransportTracker.transporting(airTransport);
        if (transporting.isEmpty() && !newDependents.isEmpty()) {
          transporting = newDependents.get(airTransport);
        }
        mustMoveWith.put(airTransport, transporting);
      }
    }
    return mustMoveWith;
  }

  public static Map<Unit, Collection<Unit>> carrierMustMoveWith(
      final Collection<Unit> units,
      final Territory start,
      final GameData data,
      final GamePlayer player) {
    return carrierMustMoveWith(units, start.getUnits(), data, player);
  }

  public static Map<Unit, Collection<Unit>> carrierMustMoveWith(
      final Collection<Unit> units,
      final Collection<Unit> startUnits,
      final GameData data,
      final GamePlayer player) {
    // we want to get all air units that are owned by our allies but not us that can land on a
    // carrier
    final Predicate<Unit> friendlyNotOwnedAir =
        Matches.alliedUnit(player, data)
            .and(Matches.unitIsOwnedBy(player).negate())
            .and(Matches.unitCanLandOnCarrier());
    final Collection<Unit> alliedAir = CollectionUtils.getMatches(startUnits, friendlyNotOwnedAir);
    if (alliedAir.isEmpty()) {
      return Map.of();
    }
    // remove air that can be carried by allied
    final Predicate<Unit> friendlyNotOwnedCarrier =
        Matches.unitIsCarrier()
            .and(Matches.alliedUnit(player, data))
            .and(Matches.unitIsOwnedBy(player).negate());
    final Collection<Unit> alliedCarrier =
        CollectionUtils.getMatches(startUnits, friendlyNotOwnedCarrier);
    for (final Unit carrier : alliedCarrier) {
      final Collection<Unit> carrying = getCanCarry(carrier, alliedAir, player, data);
      alliedAir.removeAll(carrying);
    }
    if (alliedAir.isEmpty()) {
      return Map.of();
    }
    final Map<Unit, Collection<Unit>> mapping = new HashMap<>();
    // get air that must be carried by our carriers
    final Collection<Unit> ownedCarrier =
        CollectionUtils.getMatches(
            units, Matches.unitIsCarrier().and(Matches.unitIsOwnedBy(player)));
    for (final Unit carrier : ownedCarrier) {
      final Collection<Unit> carrying = getCanCarry(carrier, alliedAir, player, data);
      alliedAir.removeAll(carrying);
      mapping.put(carrier, carrying);
    }
    return ImmutableMap.copyOf(mapping);
  }

  private static Collection<Unit> getCanCarry(
      final Unit carrier,
      final Collection<Unit> selectFrom,
      final GamePlayer playerWhoIsDoingTheMovement,
      final GameData data) {
    final UnitAttachment ua = UnitAttachment.get(carrier.getType());
    final Collection<Unit> canCarry = new ArrayList<>();
    int available = ua.getCarrierCapacity();
    for (final Unit plane : selectFrom) {
      final UnitAttachment planeAttachment = UnitAttachment.get(plane.getType());
      final int cost = planeAttachment.getCarrierCost();
      if (available >= cost) {
        // this is to test if they started in the same sea zone or not, and its not a very good way
        // of testing it.
        if ((carrier.getAlreadyMoved().compareTo(plane.getAlreadyMoved()) == 0)
            || (Matches.unitHasNotMoved().test(plane) && Matches.unitHasNotMoved().test(carrier))
            || (Matches.unitIsOwnedBy(playerWhoIsDoingTheMovement).negate().test(plane)
                && Matches.alliedUnit(playerWhoIsDoingTheMovement, data).test(plane))) {
          available -= cost;
          canCarry.add(plane);
        }
      }
      if (available == 0) {
        break;
      }
    }
    return canCarry;
  }

  /** Get the route ignoring forced territories. */
  public static Route getBestRoute(
      final Territory start,
      final Territory end,
      final GameData data,
      final GamePlayer player,
      final Collection<Unit> units,
      final boolean forceLandOrSeaRoute) {

    final boolean hasLand = units.stream().anyMatch(Matches.unitIsLand());
    final boolean hasAir = units.stream().anyMatch(Matches.unitIsAir());
    final boolean isNeutralsImpassable =
        Properties.getNeutralsImpassable(data)
            || (hasAir && !Properties.getNeutralFlyoverAllowed(data));
    final Predicate<Territory> noNeutral = Matches.territoryIsNeutralButNotWater().negate();
    final Predicate<Territory> noImpassableOrRestrictedOrNeutral =
        PredicateBuilder.of(Matches.territoryIsPassableAndNotRestricted(player, data))
            .and(Matches.territoryEffectsAllowUnits(units))
            .andIf(hasAir, Matches.territoryAllowsCanMoveAirUnitsOverOwnedLand(player, data))
            .andIf(hasLand, Matches.territoryAllowsCanMoveLandUnitsOverOwnedLand(player, data))
            .andIf(isNeutralsImpassable, noNeutral)
            .build();

    Route defaultRoute =
        data.getMap()
            .getRouteForUnits(start, end, noImpassableOrRestrictedOrNeutral, units, player);
    if (defaultRoute == null) {
      // Try for a route without impassable territories, but allowing restricted territories, since
      // there is a chance politics may change in the future
      defaultRoute =
          data.getMap()
              .getRoute(
                  start,
                  end,
                  (isNeutralsImpassable
                      ? noNeutral.and(Matches.territoryIsImpassable())
                      : Matches.territoryIsImpassable()));
      // There really is nothing, so just return any route, without conditions
      if (defaultRoute == null) {
        return data.getMap().getRoute(start, end, Matches.always());
      }
      return defaultRoute;
    }

    // Avoid looking at the dependents
    final Collection<Unit> unitsWhichAreNotBeingTransportedOrDependent =
        new ArrayList<>(
            CollectionUtils.getMatches(
                units,
                Matches.unitIsBeingTransportedByOrIsDependentOfSomeUnitInThisList(
                        units, player, data, true)
                    .negate()));

    // If start and end are land, try a land route. Don't force a land route, since planes may be
    // moving
    boolean mustGoLand = false;
    if (!start.isWater() && !end.isWater()) {
      final Route landRoute =
          data.getMap()
              .getRouteForUnits(
                  start,
                  end,
                  Matches.territoryIsLand().and(noImpassableOrRestrictedOrNeutral),
                  units,
                  player);
      if ((landRoute != null)
          && ((landRoute.numberOfSteps() <= defaultRoute.numberOfSteps())
              || (forceLandOrSeaRoute
                  && unitsWhichAreNotBeingTransportedOrDependent.stream()
                      .anyMatch(Matches.unitIsLand())))) {
        defaultRoute = landRoute;
        mustGoLand = true;
      }
    }

    // If the start and end are water, try and get a water route don't force a water route, since
    // planes may be moving
    boolean mustGoSea = false;
    if (start.isWater() && end.isWater()) {
      final Route waterRoute =
          data.getMap()
              .getRouteForUnits(
                  start,
                  end,
                  Matches.territoryIsWater().and(noImpassableOrRestrictedOrNeutral),
                  units,
                  player);
      if ((waterRoute != null)
          && ((waterRoute.numberOfSteps() <= defaultRoute.numberOfSteps())
              || (forceLandOrSeaRoute
                  && unitsWhichAreNotBeingTransportedOrDependent.stream()
                      .anyMatch(Matches.unitIsSea())))) {
        defaultRoute = waterRoute;
        mustGoSea = true;
      }
    }

    // These are the conditions we would like the route to satisfy, starting with the most important
    final Predicate<Territory> hasRequiredUnitsToMove =
        Matches.territoryHasRequiredUnitsToMove(unitsWhichAreNotBeingTransportedOrDependent, data);
    final Predicate<Territory> notEnemyOwned =
        Matches.isTerritoryEnemy(player, data)
            .negate()
            .and(
                Matches.territoryWasFoughtOver(AbstractMoveDelegate.getBattleTracker(data))
                    .negate());
    final Predicate<Territory> noEnemyUnits = Matches.territoryHasNoEnemyUnits(player, data);
    final Predicate<Territory> noAa = Matches.territoryHasEnemyAaForFlyOver(player, data).negate();
    final List<Predicate<Territory>> prioritizedMovePreferences =
        new ArrayList<>(
            List.of(
                hasRequiredUnitsToMove.and(notEnemyOwned).and(noEnemyUnits),
                hasRequiredUnitsToMove.and(noEnemyUnits),
                hasRequiredUnitsToMove.and(noAa),
                notEnemyOwned.and(noEnemyUnits),
                noEnemyUnits,
                noAa));

    // Determine max distance route is willing to accept
    final List<Unit> landUnits =
        CollectionUtils.getMatches(
            unitsWhichAreNotBeingTransportedOrDependent, Matches.unitIsLand());
    final int maxLandMoves = landUnits.isEmpty() ? 0 : getMaxMovement(landUnits).intValue();
    final int maxSteps =
        GameStepPropertiesHelper.isCombatMove(data)
            ? defaultRoute.numberOfSteps()
            : Math.max(defaultRoute.numberOfSteps(), maxLandMoves);

    // Try to find preferred route
    for (final Predicate<Territory> movePreference : prioritizedMovePreferences) {
      final Predicate<Territory> moveCondition;
      if (mustGoLand) {
        moveCondition =
            movePreference.and(Matches.territoryIsLand()).and(noImpassableOrRestrictedOrNeutral);
      } else if (mustGoSea) {
        moveCondition =
            movePreference.and(Matches.territoryIsWater()).and(noImpassableOrRestrictedOrNeutral);
      } else {
        moveCondition = movePreference.and(noImpassableOrRestrictedOrNeutral);
      }
      final Route route = data.getMap().getRouteForUnits(start, end, moveCondition, units, player);
      if ((route != null) && (route.numberOfSteps() <= maxSteps)) {
        return route;
      }
    }

    return defaultRoute;
  }

  private static boolean isNeutralsBlitzable(final GameData data) {
    return Properties.getNeutralsBlitzable(data) && !Properties.getNeutralsImpassable(data);
  }

  private static int getNeutralCharge(final GameData data, final int numberOfTerritories) {
    return numberOfTerritories * Properties.getNeutralCharge(data);
  }
}
