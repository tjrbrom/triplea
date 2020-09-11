package games.strategy.triplea.attachments;

import com.google.common.collect.ImmutableMap;
import games.strategy.engine.data.Attachable;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.MutableProperty;
import games.strategy.engine.data.UnitType;
import games.strategy.engine.data.gameparser.GameParseException;
import games.strategy.triplea.Constants;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Level;
import lombok.extern.java.Log;
import org.triplea.java.collections.IntegerMap;

/**
 * The purpose of this class is to separate the Rules Attachment variables and methods that affect
 * Players, from the Rules Attachment things that are part of conditions and national objectives.
 * <br>
 * In other words, things like placementAnyTerritory (allows placing in any territory without need
 * of a factory), or movementRestrictionTerritories (restricts movement to certain territories),
 * would go in This class. While things like alliedOwnershipTerritories (a conditions for testing
 * ownership of territories, or objectiveValue (the money given if the condition is true), would NOT
 * go in This class. <br>
 * Please do not add new things to this class. Any new Player-Rules type of stuff should go in
 * "PlayerAttachment".
 */
@Log
public abstract class AbstractPlayerRulesAttachment extends AbstractRulesAttachment {
  private static final long serialVersionUID = 7224407193725789143L;
  // Please do not add new things to this class. Any new Player-Rules type of stuff should go in
  // "PlayerAttachment".
  // These variables are related to a "rulesAttachment" that changes certain rules for the attached
  // player. They are
  // not related to conditions at all.
  protected String movementRestrictionType = null;
  protected String[] movementRestrictionTerritories = null;
  // allows placing units in any owned land
  protected boolean placementAnyTerritory = false;
  // allows placing units in any sea by owned land
  protected boolean placementAnySeaZone = false;
  // allows placing units in a captured territory
  protected boolean placementCapturedTerritory = false;
  // turns of the warning to the player when they produce more than they can place
  protected boolean unlimitedProduction = false;
  // can only place units in the capital
  protected boolean placementInCapitalRestricted = false;
  // enemy units will defend at 1
  protected boolean dominatingFirstRoundAttack = false;
  // negates dominatingFirstRoundAttack
  protected boolean negateDominatingFirstRoundAttack = false;
  // automatically produces 1 unit of a certain
  protected IntegerMap<UnitType> productionPerXTerritories = new IntegerMap<>();
  // type per every X territories owned
  // stops the user from placing units in any territory that already contains more than this
  protected int placementPerTerritory = -1;
  // number of owned units
  // maximum number of units that can be placed in each territory.
  protected int maxPlacePerTerritory = -1;

  // It would wreck most map xmls to move the rulesAttachment's to another class, so don't move them
  // out of here please!
  // However, any new rules attachments that are not conditions, should be put into the
  // "PlayerAttachment" class.
  protected AbstractPlayerRulesAttachment(
      final String name, final Attachable attachable, final GameData gameData) {
    super(name, attachable, gameData);
  }

  /** Get condition attachment for the given player and condition name. */
  public static ICondition getCondition(
      final String playerName, final String conditionName, final GameData data) {
    final GamePlayer player = data.getPlayerList().getPlayerId(playerName);
    if (player == null) {
      // could be an old map, or an old save, so we don't want to stop the game from running.
      log.severe(
          "When trying to find condition: "
              + conditionName
              + ", player does not exist: "
              + playerName);
      return null;
    }
    final Collection<GamePlayer> allPlayers = data.getPlayerList().getPlayers();
    final ICondition attachment;
    try {
      if (conditionName.contains(Constants.RULES_OBJECTIVE_PREFIX)
          || conditionName.contains(Constants.RULES_CONDITION_PREFIX)) {
        attachment = RulesAttachment.get(player, conditionName, allPlayers, true);
      } else if (conditionName.contains(Constants.TRIGGER_ATTACHMENT_PREFIX)) {
        attachment = TriggerAttachment.get(player, conditionName, allPlayers);
      } else if (conditionName.contains(Constants.POLITICALACTION_ATTACHMENT_PREFIX)) {
        attachment = PoliticalActionAttachment.get(player, conditionName, allPlayers);
      } else {
        log.severe(
            conditionName
                + " attachment must begin with: "
                + Constants.RULES_OBJECTIVE_PREFIX
                + " or "
                + Constants.RULES_CONDITION_PREFIX
                + " or "
                + Constants.TRIGGER_ATTACHMENT_PREFIX
                + " or "
                + Constants.POLITICALACTION_ATTACHMENT_PREFIX);
        return null;
      }
    } catch (final Exception e) {
      // could be an old map, or an old save, so we don't want to stop the game from running.
      log.log(
          Level.SEVERE,
          "Failed to getCondition: " + conditionName + ", for playerName: " + playerName,
          e);
      return null;
    }
    if (attachment == null) {
      log.severe("Condition attachment does not exist: " + conditionName);
      return null;
    }
    return attachment;
  }

  private void setMovementRestrictionTerritories(final String value) {
    if (value == null) {
      movementRestrictionTerritories = null;
      return;
    }
    movementRestrictionTerritories = splitOnColon(value);
    validateNames(movementRestrictionTerritories);
  }

  private void setMovementRestrictionTerritories(final String[] value) {
    movementRestrictionTerritories = value;
  }

  public String[] getMovementRestrictionTerritories() {
    return movementRestrictionTerritories;
  }

  private void resetMovementRestrictionTerritories() {
    movementRestrictionTerritories = null;
  }

  private void setMovementRestrictionType(final String value) throws GameParseException {
    if (value == null) {
      movementRestrictionType = null;
      return;
    }
    if (!(value.equals("disallowed") || value.equals("allowed"))) {
      throw new GameParseException(
          "movementRestrictionType must be allowed or disallowed" + thisErrorMsg());
    }
    movementRestrictionType = value;
  }

  public String getMovementRestrictionType() {
    return movementRestrictionType;
  }

  private void resetMovementRestrictionType() {
    movementRestrictionType = null;
  }

  private void setProductionPerXTerritories(final String value) throws GameParseException {
    final String[] s = splitOnColon(value);
    if (s.length <= 0 || s.length > 2) {
      throw new GameParseException(
          "productionPerXTerritories cannot be empty or have more than two fields"
              + thisErrorMsg());
    }
    final String unitTypeToProduce;
    if (s.length == 1) {
      unitTypeToProduce = Constants.UNIT_TYPE_INFANTRY;
    } else {
      unitTypeToProduce = s[1];
    }
    // validate that this unit exists in the xml
    final UnitType ut = getData().getUnitTypeList().getUnitType(unitTypeToProduce);
    if (ut == null) {
      throw new GameParseException("No unit called: " + unitTypeToProduce + thisErrorMsg());
    }
    final int n = getInt(s[0]);
    if (n <= 0) {
      throw new GameParseException(
          "productionPerXTerritories must be a positive integer" + thisErrorMsg());
    }
    productionPerXTerritories.put(ut, n);
  }

  private void setProductionPerXTerritories(final IntegerMap<UnitType> value) {
    productionPerXTerritories = value;
  }

  public IntegerMap<UnitType> getProductionPerXTerritories() {
    return productionPerXTerritories;
  }

  private void resetProductionPerXTerritories() {
    productionPerXTerritories = new IntegerMap<>();
  }

  private void setPlacementPerTerritory(final String value) {
    placementPerTerritory = getInt(value);
  }

  private void setPlacementPerTerritory(final Integer value) {
    placementPerTerritory = value;
  }

  public int getPlacementPerTerritory() {
    return placementPerTerritory;
  }

  private void resetPlacementPerTerritory() {
    placementPerTerritory = -1;
  }

  private void setMaxPlacePerTerritory(final String value) {
    maxPlacePerTerritory = getInt(value);
  }

  private void setMaxPlacePerTerritory(final Integer value) {
    maxPlacePerTerritory = value;
  }

  public int getMaxPlacePerTerritory() {
    return maxPlacePerTerritory;
  }

  private void resetMaxPlacePerTerritory() {
    maxPlacePerTerritory = -1;
  }

  private void setPlacementAnyTerritory(final String value) {
    placementAnyTerritory = getBool(value);
  }

  private void setPlacementAnyTerritory(final Boolean value) {
    placementAnyTerritory = value;
  }

  public boolean getPlacementAnyTerritory() {
    return placementAnyTerritory;
  }

  private void resetPlacementAnyTerritory() {
    placementAnyTerritory = false;
  }

  private void setPlacementAnySeaZone(final String value) {
    placementAnySeaZone = getBool(value);
  }

  private void setPlacementAnySeaZone(final Boolean value) {
    placementAnySeaZone = value;
  }

  public boolean getPlacementAnySeaZone() {
    return placementAnySeaZone;
  }

  private void resetPlacementAnySeaZone() {
    placementAnySeaZone = false;
  }

  private void setPlacementCapturedTerritory(final String value) {
    placementCapturedTerritory = getBool(value);
  }

  private void setPlacementCapturedTerritory(final Boolean value) {
    placementCapturedTerritory = value;
  }

  public boolean getPlacementCapturedTerritory() {
    return placementCapturedTerritory;
  }

  private void resetPlacementCapturedTerritory() {
    placementCapturedTerritory = false;
  }

  private void setPlacementInCapitalRestricted(final String value) {
    placementInCapitalRestricted = getBool(value);
  }

  private void setPlacementInCapitalRestricted(final Boolean value) {
    placementInCapitalRestricted = value;
  }

  public boolean getPlacementInCapitalRestricted() {
    return placementInCapitalRestricted;
  }

  private void resetPlacementInCapitalRestricted() {
    placementInCapitalRestricted = false;
  }

  private void setUnlimitedProduction(final String value) {
    unlimitedProduction = getBool(value);
  }

  private void setUnlimitedProduction(final Boolean value) {
    unlimitedProduction = value;
  }

  public boolean getUnlimitedProduction() {
    return unlimitedProduction;
  }

  private void resetUnlimitedProduction() {
    unlimitedProduction = false;
  }

  private void setDominatingFirstRoundAttack(final String value) {
    dominatingFirstRoundAttack = getBool(value);
  }

  private void setDominatingFirstRoundAttack(final Boolean value) {
    dominatingFirstRoundAttack = value;
  }

  public boolean getDominatingFirstRoundAttack() {
    return dominatingFirstRoundAttack;
  }

  private void resetDominatingFirstRoundAttack() {
    dominatingFirstRoundAttack = false;
  }

  private void setNegateDominatingFirstRoundAttack(final String value) {
    negateDominatingFirstRoundAttack = getBool(value);
  }

  private void setNegateDominatingFirstRoundAttack(final Boolean value) {
    negateDominatingFirstRoundAttack = value;
  }

  public boolean getNegateDominatingFirstRoundAttack() {
    return negateDominatingFirstRoundAttack;
  }

  private void resetNegateDominatingFirstRoundAttack() {
    negateDominatingFirstRoundAttack = false;
  }

  @Override
  public void validate(final GameData data) {
    validateNames(movementRestrictionTerritories);
  }

  @Override
  public Map<String, MutableProperty<?>> getPropertyMap() {
    return ImmutableMap.<String, MutableProperty<?>>builder()
        .putAll(super.getPropertyMap())
        .put(
            "movementRestrictionType",
            MutableProperty.ofString(
                this::setMovementRestrictionType,
                this::getMovementRestrictionType,
                this::resetMovementRestrictionType))
        .put(
            "movementRestrictionTerritories",
            MutableProperty.of(
                this::setMovementRestrictionTerritories,
                this::setMovementRestrictionTerritories,
                this::getMovementRestrictionTerritories,
                this::resetMovementRestrictionTerritories))
        .put(
            "placementAnyTerritory",
            MutableProperty.of(
                this::setPlacementAnyTerritory,
                this::setPlacementAnyTerritory,
                this::getPlacementAnyTerritory,
                this::resetPlacementAnyTerritory))
        .put(
            "placementAnySeaZone",
            MutableProperty.of(
                this::setPlacementAnySeaZone,
                this::setPlacementAnySeaZone,
                this::getPlacementAnySeaZone,
                this::resetPlacementAnySeaZone))
        .put(
            "placementCapturedTerritory",
            MutableProperty.of(
                this::setPlacementCapturedTerritory,
                this::setPlacementCapturedTerritory,
                this::getPlacementCapturedTerritory,
                this::resetPlacementCapturedTerritory))
        .put(
            "unlimitedProduction",
            MutableProperty.of(
                this::setUnlimitedProduction,
                this::setUnlimitedProduction,
                this::getUnlimitedProduction,
                this::resetUnlimitedProduction))
        .put(
            "placementInCapitalRestricted",
            MutableProperty.of(
                this::setPlacementInCapitalRestricted,
                this::setPlacementInCapitalRestricted,
                this::getPlacementInCapitalRestricted,
                this::resetPlacementInCapitalRestricted))
        .put(
            "dominatingFirstRoundAttack",
            MutableProperty.of(
                this::setDominatingFirstRoundAttack,
                this::setDominatingFirstRoundAttack,
                this::getDominatingFirstRoundAttack,
                this::resetDominatingFirstRoundAttack))
        .put(
            "negateDominatingFirstRoundAttack",
            MutableProperty.of(
                this::setNegateDominatingFirstRoundAttack,
                this::setNegateDominatingFirstRoundAttack,
                this::getNegateDominatingFirstRoundAttack,
                this::resetNegateDominatingFirstRoundAttack))
        .put(
            "productionPerXTerritories",
            MutableProperty.of(
                this::setProductionPerXTerritories,
                this::setProductionPerXTerritories,
                this::getProductionPerXTerritories,
                this::resetProductionPerXTerritories))
        .put(
            "placementPerTerritory",
            MutableProperty.of(
                this::setPlacementPerTerritory,
                this::setPlacementPerTerritory,
                this::getPlacementPerTerritory,
                this::resetPlacementPerTerritory))
        .put(
            "maxPlacePerTerritory",
            MutableProperty.of(
                this::setMaxPlacePerTerritory,
                this::setMaxPlacePerTerritory,
                this::getMaxPlacePerTerritory,
                this::resetMaxPlacePerTerritory))
        .build();
  }
}
