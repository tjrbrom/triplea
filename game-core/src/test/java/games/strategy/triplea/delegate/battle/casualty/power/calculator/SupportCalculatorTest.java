package games.strategy.triplea.delegate.battle.casualty.power.calculator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import com.google.common.base.Preconditions;
import games.strategy.engine.data.UnitType;
import games.strategy.triplea.attachments.UnitSupportAttachment;
import games.strategy.triplea.delegate.battle.casualty.power.calculator.SupportCalculator.UnitData;
import games.strategy.triplea.xml.TestDataBigWorld1942V3;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.Singular;
import lombok.ToString;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SupportCalculatorTest {
  private static final TestDataBigWorld1942V3 testData = new TestDataBigWorld1942V3();

  @ParameterizedTest
  @MethodSource
  void verifySupport(final TestParameters testParameters) {

    final Collection<UnitSupportAttachment> supportAttachments =
        testParameters.supportAttachments.stream()
            .map(SupportCalculatorTest::buildSupportAttachment)
            .collect(Collectors.toList());

    final var supportCalculator =
        SupportCalculator.builder()
            .defending(testParameters.defending)
            .units(testParameters.units)
            .enemyUnits(testParameters.enemyUnits)
            .supportAttachments(supportAttachments)
            .build();

    final int result = supportCalculator.computeSupportBonus();

    assertThat(testParameters.testCaseLabel, result, is(testParameters.expectedPowerWithSupport));
  }

  @SuppressWarnings("unused")
  private static List<TestParameters> verifySupport() {
    return List.of(
        singleInfantryAndArtilleryWithNoSupportAttachments(), // [1]
        loneArtilleryProvidesNoSupport(),
        singleInfantryWithNoArtilleryReceivesNoSupport(), // [3]
        artilleryAndInfantryWhereArtilleryDoesNotSupportInfantry(),
        artillerySupportingAnInfantry(), // [5]
        twoUnitsSupportingOneInfantryChoosesBestSupport(),
        twoInfantrySupportedByBothTankAndArtillery(), // [7]
        threeArtillerySupportingTwoInfantry(),
        threeInfantrySupportedByTwoArtillery(), // [9]
        oneArtilleryWithMultipleSupportsSupportingTwoOfThreeInfantry(),
        oneArtilleryAndTankEachWithMultipleSupportsSupportingThreeInfantry(), // [11]
        oneArtilleryProvidingOneAdditionalRollsToTank(),
        oneArtilleryProvidingThreeAdditionalRollsToTank(), // [13]
        oneArtilleryProvidingAdditionalRollsAndAdditionalStrengthToTank(),
        oneArtilleryProvidingAdditionalRollsAndTanksProvidingAdditionalStrength(), // [15]
        enemyUnitProvidingLessAttackRolls(),
        enemyUnitProvidingLessAttackStrength(), // [17]
        enemyUnitProvidingLessAttackStrengthToMultipleUnits(),
        enemyUnitProvidingLessAttackStrengthAndLessRolls(), // [19]
        enemyNegativeStrengthSupportDoesNotGoNegative(),
        enemyNegativeRollsSupportDoesNotGoNegative(), // [21]
        artilleryProvidingNoBenefitOnDefense(),
        artilleryProvidingSupportOnDefense(), // [23]
        supportRuleForUnitNotPresent());
  }

  @Builder
  @ToString
  private static class TestParameters {
    @Nonnull private final String testCaseLabel;
    @Builder.Default private boolean defending = false;
    @Singular private final Collection<UnitData> units;
    @Singular private final Collection<UnitData> enemyUnits;
    @Singular private final Collection<SupportAttachmentParameters> supportAttachments;
    @Nonnull private final Integer expectedPowerWithSupport;
  }

  @Builder
  private static class SupportAttachmentParameters {
    @Nonnull private final UnitType supportingUnit;
    /** The number of units that can be supported */
    @Builder.Default private int supportCount = 1;

    @Builder.Default private int strength = 0;
    @Builder.Default private int rolls = 0;
    @Singular private Set<UnitType> supportedUnits;
    @Builder.Default private boolean offense = true;
    @Builder.Default private boolean defense = true;
    @Builder.Default private boolean allied = true;
  }

  private static UnitSupportAttachment buildSupportAttachment(
      final SupportAttachmentParameters parameters) {
    Preconditions.checkState(
        parameters.strength == 0 || parameters.rolls == 0,
        "Can only set one of rolls or strength at a time");

    final var unitSupportAttachment =
        new UnitSupportAttachment(
            UnitSupportAttachment.ATTACHMENT_NAME, parameters.supportingUnit, testData.gameData);
    unitSupportAttachment.setStrength(parameters.strength != 0);
    unitSupportAttachment.setRoll(parameters.rolls != 0);
    unitSupportAttachment.setBonus(
        parameters.strength != 0 ? parameters.strength : parameters.rolls);
    unitSupportAttachment.setBonusType(
        new UnitSupportAttachment.BonusType("name", parameters.supportCount));
    unitSupportAttachment.setOffence(parameters.offense);
    unitSupportAttachment.setDefence(parameters.defense);
    unitSupportAttachment.setAllied(parameters.allied);
    unitSupportAttachment.setEnemy(!parameters.allied);
    unitSupportAttachment.setUnitType(parameters.supportedUnits);
    return unitSupportAttachment;
  }

  private static TestParameters singleInfantryAndArtilleryWithNoSupportAttachments() {
    final int basePower = 2;
    final int expectedSupportBonus = 0; // no rules to give support

    return TestParameters.builder()
        .testCaseLabel("singleInfantryWithNoSupportAttachments")
        .unit(UnitData.builder().type(testData.infantry).count(1).power(1).build())
        .unit(UnitData.builder().type(testData.artillery).count(1).power(1).build())
        .expectedPowerWithSupport(basePower + expectedSupportBonus)
        .build();
  }

  /** Artillery, provides support, but no unit present to get that support. */
  private static TestParameters loneArtilleryProvidesNoSupport() {
    final int basePower = 1;
    final int expectedSupportBonus = 0; // no infantry to receive support

    return TestParameters.builder()
        .testCaseLabel("loneArtilleryProvidesNoSupport")
        .unit(UnitData.builder().type(testData.artillery).count(1).power(1).build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .strength(1)
                .supportingUnit(testData.artillery)
                .supportedUnit(testData.tank)
                .build())
        .expectedPowerWithSupport(basePower + expectedSupportBonus)
        .build();
  }

  /** 1 unit, an infantry, receives support, but no unit present to provide that support. */
  private static TestParameters singleInfantryWithNoArtilleryReceivesNoSupport() {
    final int basePower = 1;
    final int expectedSupportBonus = 0; // no artillery to provide support

    return TestParameters.builder()
        .testCaseLabel("singleInfantryWithNoArtilleryReceivesNoSupport")
        .unit(UnitData.builder().type(testData.infantry).count(1).power(1).build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .strength(1)
                .supportingUnit(testData.artillery)
                .supportedUnit(testData.infantry)
                .build())
        .expectedPowerWithSupport(basePower + expectedSupportBonus)
        .build();
  }

  /**
   * 1 infantry, 1 artillery (with special rule artillery supports tank, not infantry).
   *
   * <p>Crafting the support so that artillery supports tanks, we should see no support bonus from
   * this combination.
   */
  private static TestParameters artilleryAndInfantryWhereArtilleryDoesNotSupportInfantry() {
    final int basePower = 2;
    final int expectedSupportBonus = 0; // artillery does not support infantry here

    return TestParameters.builder()
        .testCaseLabel("artilleryAndInfantryWhereArtilleryDoesNotSupportInfantry")
        .unit(UnitData.builder().type(testData.infantry).count(1).power(1).build())
        .unit(UnitData.builder().type(testData.artillery).count(1).power(1).build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .strength(1)
                .supportingUnit(testData.artillery)
                .supportedUnit(testData.tank)
                .build())
        .expectedPowerWithSupport(basePower + expectedSupportBonus)
        .build();
  }

  /**
   * 2 units, 1 artillery, 1 infantry
   *
   * <p>Relatively simple case where the artillery should support the infantry.
   */
  private static TestParameters artillerySupportingAnInfantry() {
    final int basePower = 2;
    final int expectedSupportBonus = 1; // simple plus one from artillery to infantry

    return TestParameters.builder()
        .testCaseLabel("artillerySupportingAnInfantry")
        .unit(UnitData.builder().type(testData.infantry).count(1).power(1).build())
        .unit(UnitData.builder().type(testData.artillery).count(1).power(1).build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .strength(1)
                .supportingUnit(testData.artillery)
                .supportedUnit(testData.infantry)
                .build())
        .expectedPowerWithSupport(basePower + expectedSupportBonus)
        .build();
  }

  /**
   * 3 units, 1 inf, 1 tank, 1 artillery
   *
   * <p>Infantry chooses best support, we give more support from the tank than the artillery
   */
  private static TestParameters twoUnitsSupportingOneInfantryChoosesBestSupport() {
    final int basePower = 3;
    final int expectedSupportBonus = 3; // tank has best infantry support, adds +3

    return TestParameters.builder()
        .testCaseLabel("twoUnitsSupportingOneInfantryChoosesBestSupport")
        .unit(UnitData.builder().type(testData.infantry).count(1).power(1).build())
        .unit(UnitData.builder().type(testData.artillery).count(1).power(1).build())
        .unit(UnitData.builder().type(testData.tank).count(1).power(1).build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .strength(1)
                .supportingUnit(testData.artillery)
                .supportedUnit(testData.infantry)
                .build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .strength(3)
                .supportingUnit(testData.tank)
                .supportedUnit(testData.infantry)
                .build())
        .expectedPowerWithSupport(basePower + expectedSupportBonus)
        .build();
  }

  /**
   * 4 units, 2 inf, 1 artillery, 1 tank
   *
   * <p>1 inf supported by artillery (+1) 1 inf supported by a tank (+4)
   */
  private static TestParameters twoInfantrySupportedByBothTankAndArtillery() {
    final int basePower = 4;
    final int expectedSupportBonus = 5; // tank +4 and artillery +1 to the two infantry

    return TestParameters.builder()
        .testCaseLabel("twoInfantrySupportedByBothTankAndArtillery")
        .unit(UnitData.builder().type(testData.infantry).count(2).power(1).build())
        .unit(UnitData.builder().type(testData.artillery).count(1).power(1).build())
        .unit(UnitData.builder().type(testData.tank).count(1).power(1).build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .strength(1)
                .supportingUnit(testData.artillery)
                .supportedUnit(testData.infantry)
                .build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .strength(4)
                .supportingUnit(testData.tank)
                .supportedUnit(testData.infantry)
                .build())
        .expectedPowerWithSupport(basePower + expectedSupportBonus)
        .build();
  }

  private static TestParameters threeArtillerySupportingTwoInfantry() {
    final int basePower = 5;
    final int expectedSupportBonus = 2; // two artillery supporting two infantry

    return TestParameters.builder()
        .testCaseLabel("threeArtillerySupportingTwoInfantry")
        .unit(UnitData.builder().type(testData.infantry).count(2).power(1).build())
        .unit(UnitData.builder().type(testData.artillery).count(3).power(1).build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .strength(1)
                .supportingUnit(testData.artillery)
                .supportedUnit(testData.infantry)
                .build())
        .expectedPowerWithSupport(basePower + expectedSupportBonus)
        .build();
  }

  private static TestParameters threeInfantrySupportedByTwoArtillery() {
    final int basePower = 4;
    final int expectedSupportBonus = 2; // artillery supporting two infantry

    return TestParameters.builder()
        .testCaseLabel("threeInfantrySupportedByTwoArtillery")
        .unit(UnitData.builder().type(testData.infantry).count(3).power(1).build())
        .unit(UnitData.builder().type(testData.artillery).count(2).power(1).build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .strength(1)
                .supportingUnit(testData.artillery)
                .supportedUnit(testData.infantry)
                .build())
        .expectedPowerWithSupport(basePower + expectedSupportBonus)
        .expectedPowerWithSupport(7)
        .build();
  }

  private static TestParameters oneArtilleryWithMultipleSupportsSupportingTwoOfThreeInfantry() {

    final int basePower = 4;
    final int expectedSupportBonus = 2; // artillery supporting two infantry

    return TestParameters.builder()
        .testCaseLabel("oneArtilleryWithMultipleSupportsSupportingTwoOfThreeInfantry")
        .unit(UnitData.builder().type(testData.infantry).count(3).power(1).build())
        .unit(UnitData.builder().type(testData.artillery).count(1).power(1).build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .strength(1)
                .supportCount(2)
                .supportingUnit(testData.artillery)
                .supportedUnit(testData.infantry)
                .build())
        .expectedPowerWithSupport(basePower + expectedSupportBonus)
        .build();
  }

  /**
   * 5 units, 3 infantry, 1 tank, 1 artillery<br>
   * - tank is supporting 2 at +5 each<br>
   * - artillery is supporting 2 at +1<br>
   * In this test we provide support up to 4 units, we have only 3, need to be sure we allocate the
   * best support to the first 2 and the third still receives support.
   */
  private static TestParameters
      oneArtilleryAndTankEachWithMultipleSupportsSupportingThreeInfantry() {

    final int basePower = 5;
    final int expectedSupportBonus = 11; // tank support 2 infantry, artillery supporting 3rd inf

    return TestParameters.builder()
        .testCaseLabel("oneArtilleryAndTankEachWithMultipleSupportsSupportingThreeInfantry")
        .unit(UnitData.builder().type(testData.infantry).count(3).power(1).build())
        .unit(UnitData.builder().type(testData.artillery).count(1).power(1).build())
        .unit(UnitData.builder().type(testData.tank).count(1).power(1).build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .strength(1)
                .supportCount(2)
                .supportingUnit(testData.artillery)
                .supportedUnit(testData.infantry)
                .build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .strength(5)
                .supportCount(2)
                .supportingUnit(testData.tank)
                .supportedUnit(testData.infantry)
                .build())
        .expectedPowerWithSupport(basePower + expectedSupportBonus)
        .build();
  }

  private static TestParameters oneArtilleryProvidingOneAdditionalRollsToTank() {
    final int basePower = 4;
    final int expectedSupportBonus = 3; // tank gets an extra roll

    return TestParameters.builder()
        .testCaseLabel("oneArtilleryProvidingOneAdditionalRollsToTank")
        .unit(UnitData.builder().type(testData.artillery).count(1).power(1).build())
        .unit(UnitData.builder().type(testData.tank).count(1).power(3).build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .supportCount(1)
                .rolls(1)
                .supportingUnit(testData.artillery)
                .supportedUnit(testData.tank)
                .build())
        .expectedPowerWithSupport(basePower + expectedSupportBonus)
        .build();
  }

  private static TestParameters oneArtilleryProvidingThreeAdditionalRollsToTank() {
    final int basePower = 4;
    final int expectedSupportBonus = 9; // tank gets 3 more rolls at power 3

    return TestParameters.builder()
        .testCaseLabel("oneArtilleryProvidingThreeAdditionalRollsToTank")
        .unit(UnitData.builder().type(testData.artillery).count(1).power(1).build())
        .unit(UnitData.builder().type(testData.tank).count(1).power(3).build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .supportCount(1)
                .rolls(3)
                .supportingUnit(testData.artillery)
                .supportedUnit(testData.tank)
                .build())
        .expectedPowerWithSupport(basePower + expectedSupportBonus)
        .build();
  }

  private static TestParameters oneArtilleryProvidingAdditionalRollsAndAdditionalStrengthToTank() {
    final int basePower = 4;
    final int expectedSupportBonus =
        1 + 2 * 4; // tank attacking gets +1, and then 2 more rolls at 4

    return TestParameters.builder()
        .testCaseLabel("oneArtilleryProvidingAdditionalRollsAndAdditionalStrengthToTank")
        .unit(UnitData.builder().type(testData.artillery).count(1).power(1).build())
        .unit(UnitData.builder().type(testData.tank).count(1).power(3).build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .supportCount(1)
                .rolls(2)
                .supportingUnit(testData.artillery)
                .supportedUnit(testData.tank)
                .build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .supportCount(1)
                .strength(1)
                .supportingUnit(testData.artillery)
                .supportedUnit(testData.tank)
                .build())
        .expectedPowerWithSupport(basePower + expectedSupportBonus)
        .build();
  }

  private static TestParameters
      oneArtilleryProvidingAdditionalRollsAndTanksProvidingAdditionalStrength() {
    final int basePower = 6;
    final int expectedSupportBonus = 2 * 2; // infantry should get 2 more rolls at 2 from tank

    return TestParameters.builder()
        .testCaseLabel("oneArtilleryProvidingAdditionalRollsAndTanksProvidingAdditionalStrength")
        .unit(UnitData.builder().type(testData.infantry).count(1).power(2).build())
        .unit(UnitData.builder().type(testData.artillery).count(1).power(1).build())
        .unit(UnitData.builder().type(testData.tank).count(1).power(3).build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .supportCount(1)
                .rolls(1)
                .supportingUnit(testData.artillery)
                .supportedUnit(testData.infantry)
                .build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .supportCount(1)
                .rolls(2)
                .supportingUnit(testData.tank)
                .supportedUnit(testData.infantry)
                .build())
        .expectedPowerWithSupport(basePower + expectedSupportBonus)
        .build();
  }

  private static TestParameters enemyUnitProvidingLessAttackRolls() {
    final int basePower = 2;
    final int expectedSupportBonus = -2; // infantry goes from 1 roll to 0 rolls

    return TestParameters.builder()
        .testCaseLabel("enemyUnitProvidingLessAttackRolls")
        .unit(UnitData.builder().type(testData.infantry).count(1).power(2).build())
        .enemyUnit(UnitData.builder().type(testData.infantry).count(1).power(2).build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .allied(false)
                .supportCount(1)
                .rolls(-1)
                .supportingUnit(testData.infantry)
                .supportedUnit(testData.infantry)
                .build())
        .expectedPowerWithSupport(basePower + expectedSupportBonus)
        .build();
  }

  private static TestParameters enemyUnitProvidingLessAttackStrength() {
    final int basePower = 2;
    final int expectedSupportBonus = -1; // single enemy buff gives -1

    return TestParameters.builder()
        .testCaseLabel("enemyUnitProvidingLessAttackStrength")
        .unit(UnitData.builder().type(testData.infantry).count(1).power(2).build())
        .enemyUnit(UnitData.builder().type(testData.infantry).count(1).power(2).build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .allied(false)
                .supportCount(1)
                .strength(-1)
                .supportingUnit(testData.infantry)
                .supportedUnit(testData.infantry)
                .build())
        .expectedPowerWithSupport(basePower + expectedSupportBonus)
        .build();
  }

  private static TestParameters enemyUnitProvidingLessAttackStrengthToMultipleUnits() {
    final int basePower = 6;
    final int expectedSupportBonus = -3; // only one enemy buff should be picked, -1 to each inf.

    return TestParameters.builder()
        .testCaseLabel("enemyUnitProvidingLessAttackStrengthToMultipleUnits")
        .unit(UnitData.builder().type(testData.infantry).count(3).power(2).build())
        .enemyUnit(UnitData.builder().type(testData.infantry).count(2).power(2).build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .allied(false)
                .supportCount(5)
                .strength(-1)
                .supportingUnit(testData.infantry)
                .supportedUnit(testData.infantry)
                .build())
        .expectedPowerWithSupport(basePower + expectedSupportBonus)
        .build();
  }

  private static TestParameters enemyUnitProvidingLessAttackStrengthAndLessRolls() {
    final int basePower = 7;
    // enemy power buff subtracts one from each: -3
    // friendly artillery gives two more rolls @ 1: +2
    // enemy reduces roll count of all units (leaving only two infantry rolling at a 1)
    final int expectedSupportBonus = -3 + 2 - 3;

    return TestParameters.builder()
        .testCaseLabel("enemyUnitProvidingLessAttackStrengthAndLessRolls")
        .unit(UnitData.builder().type(testData.infantry).count(3).power(2).build())
        .unit(UnitData.builder().type(testData.artillery).count(1).power(1).build())
        .enemyUnit(UnitData.builder().type(testData.infantry).count(1).power(2).build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .supportCount(2)
                .rolls(1)
                .supportingUnit(testData.artillery)
                .supportedUnit(testData.infantry)
                .build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .allied(false)
                .supportCount(5)
                .strength(-1)
                .supportingUnit(testData.infantry)
                .supportedUnit(testData.infantry)
                .build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .allied(false)
                .supportCount(5)
                .rolls(-1)
                .supportingUnit(testData.infantry)
                .supportedUnit(testData.infantry)
                .build())
        .expectedPowerWithSupport(basePower + expectedSupportBonus)
        .build();
  }

  private static TestParameters enemyNegativeStrengthSupportDoesNotGoNegative() {
    final int basePower = 2;
    // enemy buff removes power down to zero, does not go negative
    final int expectedSupportBonus = -2;

    return TestParameters.builder()
        .testCaseLabel("enemyNegativeSupportDoesNotGoNegative")
        .unit(UnitData.builder().type(testData.infantry).count(1).power(2).build())
        .enemyUnit(UnitData.builder().type(testData.infantry).count(1).power(2).build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .allied(false)
                .supportCount(2)
                .strength(-3)
                .supportingUnit(testData.infantry)
                .supportedUnit(testData.infantry)
                .build())
        .expectedPowerWithSupport(basePower + expectedSupportBonus)
        .build();
  }

  private static TestParameters enemyNegativeRollsSupportDoesNotGoNegative() {
    final int basePower = 2;
    // enemy buff removes all rolls, which effectively removes all power.
    final int expectedSupportBonus = -2;

    return TestParameters.builder()
        .testCaseLabel("enemyNegativeRollsSupportDoesNotGoNegative")
        .unit(UnitData.builder().type(testData.infantry).count(1).power(2).build())
        .enemyUnit(UnitData.builder().type(testData.infantry).count(1).power(2).build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .allied(false)
                .supportCount(2)
                .rolls(-3)
                .supportingUnit(testData.infantry)
                .supportedUnit(testData.infantry)
                .build())
        .expectedPowerWithSupport(basePower + expectedSupportBonus)
        .build();
  }

  private static TestParameters artilleryProvidingNoBenefitOnDefense() {
    final int basePower = 2;
    final int expectedSupportBonus = 0; // this is a defensive battle, no support

    return TestParameters.builder()
        .testCaseLabel("artilleryProvidingNoBenefitOnDefense")
        .defending(true)
        .unit(UnitData.builder().type(testData.infantry).count(1).power(2).build())
        .enemyUnit(UnitData.builder().type(testData.artillery).count(1).power(2).build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .supportCount(1)
                .strength(1)
                .supportingUnit(testData.artillery)
                .supportedUnit(testData.infantry)
                .offense(true)
                .defense(false)
                .build())
        .expectedPowerWithSupport(basePower + expectedSupportBonus)
        .build();
  }

  private static TestParameters artilleryProvidingSupportOnDefense() {
    final int basePower = 4;
    final int expectedSupportBonus = 1; // artillery this time does provide defensive bonus.

    return TestParameters.builder()
        .testCaseLabel("artilleryProvidingSupportOnDefense")
        .defending(true)
        .unit(UnitData.builder().type(testData.infantry).count(1).power(2).build())
        .unit(UnitData.builder().type(testData.artillery).count(1).power(2).build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .supportCount(1)
                .strength(1)
                .supportingUnit(testData.artillery)
                .supportedUnit(testData.infantry)
                .offense(false)
                .defense(true)
                .build())
        .expectedPowerWithSupport(basePower + expectedSupportBonus)
        .build();
  }

  private static TestParameters supportRuleForUnitNotPresent() {
    final int basePower = 1;
    final int expectedSupportBonus = 0; // no artillery to provide support

    return TestParameters.builder()
        .testCaseLabel("supportRuleForUnitNotPresent")
        .unit(UnitData.builder().type(testData.infantry).count(1).power(1).build())
        .supportAttachment(
            SupportAttachmentParameters.builder()
                .strength(1)
                .supportingUnit(testData.artillery)
                .supportedUnit(testData.tank)
                .build())
        .expectedPowerWithSupport(basePower + expectedSupportBonus)
        .build();
  }
}
