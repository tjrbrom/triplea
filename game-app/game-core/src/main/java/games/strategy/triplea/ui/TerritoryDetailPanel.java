package games.strategy.triplea.ui;

import static games.strategy.triplea.image.UnitImageFactory.ImageKey;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Territory;
import games.strategy.triplea.attachments.TerritoryAttachment;
import games.strategy.triplea.odds.calculator.BattleCalculatorDialog;
import games.strategy.triplea.ui.panels.map.MapPanel;
import games.strategy.triplea.util.UnitCategory;
import games.strategy.triplea.util.UnitSeparator;
import games.strategy.ui.OverlayIcon;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import org.triplea.swing.key.binding.KeyCode;
import org.triplea.swing.key.binding.SwingKeyBinding;

public class TerritoryDetailPanel extends AbstractStatPanel {
  private static final long serialVersionUID = 1377022163587438988L;
  private final UiContext uiContext;
  private final JButton showOdds = new JButton("Battle Calculator (Ctrl-B)");
  private final JButton addAttackers = new JButton("Add Attackers (Ctrl-A)");
  private final JButton addDefenders = new JButton("Add Defenders (Ctrl-D)");
  private final JButton findTerritoryButton;
  private final JLabel territoryInfo = new JLabel();
  private final JLabel unitInfo = new JLabel();
  private final JScrollPane units = new JScrollPane();
  private @Nullable Territory currentTerritory;
  private final TripleAFrame frame;
  private List<Function<Territory, String>> additionalTerritoryDetailFunctions = new ArrayList<>();

  TerritoryDetailPanel(
      final MapPanel mapPanel,
      final GameData data,
      final UiContext uiContext,
      final TripleAFrame frame) {
    super(data);
    this.frame = frame;
    this.uiContext = uiContext;
    mapPanel.addMapSelectionListener(
        new DefaultMapSelectionListener() {
          @Override
          public void mouseEntered(final Territory territory) {
            territoryChanged(territory);
          }
        });

    findTerritoryButton = new JButton(new FindTerritoryAction(frame));
    findTerritoryButton.setText(findTerritoryButton.getText() + " (Ctrl-F)");

    initLayout();
  }

  protected void initLayout() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(new EmptyBorder(5, 5, 0, 0));

    showOdds.addActionListener(
        e -> BattleCalculatorDialog.show(frame, currentTerritory, gameData.getHistory()));
    SwingKeyBinding.addKeyBindingWithMetaAndCtrlMasks(
        frame,
        KeyCode.B,
        () -> BattleCalculatorDialog.show(frame, currentTerritory, gameData.getHistory()));

    addAttackers.addActionListener(e -> BattleCalculatorDialog.addAttackers(currentTerritory));
    SwingKeyBinding.addKeyBindingWithMetaAndCtrlMasks(
        frame, KeyCode.A, () -> BattleCalculatorDialog.addAttackers(currentTerritory));

    addDefenders.addActionListener(e -> BattleCalculatorDialog.addDefenders(currentTerritory));
    SwingKeyBinding.addKeyBindingWithMetaAndCtrlMasks(
        frame, KeyCode.D, () -> BattleCalculatorDialog.addDefenders(currentTerritory));
    units.setBorder(BorderFactory.createEmptyBorder());
    units.getVerticalScrollBar().setUnitIncrement(20);
    add(showOdds);
    add(addAttackers);
    add(addDefenders);
    add(findTerritoryButton);
    add(territoryInfo);
    add(unitInfo);
    add(units);
    setElementsVisible(false);
  }

  private void setElementsVisible(final boolean visible) {
    showOdds.setVisible(visible);
    addAttackers.setVisible(visible);
    addDefenders.setVisible(visible);
    findTerritoryButton.setVisible(visible);
    territoryInfo.setVisible(visible);
    unitInfo.setVisible(visible);
    units.setVisible(visible);
  }

  public void setGameData(final GameData data) {
    gameData = data;
    territoryChanged(null);
  }

  public void addAdditionalTerritoryDetailsFunction(final Function<Territory, String> method) {
    this.additionalTerritoryDetailFunctions.add(method);
  }

  public void removeAdditionalTerritoryDetailsFunction(final Function<Territory, String> method) {
    this.additionalTerritoryDetailFunctions.remove(method);
  }

  private void territoryChanged(final @Nullable Territory territory) {
    currentTerritory = territory;
    if (territory == null) {
      setElementsVisible(false);
      return;
    }
    setElementsVisible(true);
    final TerritoryAttachment ta = TerritoryAttachment.get(territory);
    final String labelText;
    final String additionalText =
        this.additionalTerritoryDetailFunctions.stream()
            .map(method -> method.apply(territory))
            .collect(Collectors.joining("<br />"));
    if (ta == null) {
      labelText =
          "<html>"
              + territory.getName()
              + "<br>Water Territory"
              + "<br><br>"
              + additionalText
              + "</html>";
    } else {
      labelText = "<html>" + ta.toStringForInfo(true, true) + "<br>" + additionalText + "</html>";
    }
    territoryInfo.setText(labelText);
    unitInfo.setText(
        "Units: "
            + territory.getUnits().stream()
                .filter(u -> uiContext.getMapData().shouldDrawUnit(u.getType().getName()))
                .count());
    units.setViewportView(unitsInTerritoryPanel(territory, uiContext));
  }

  private static JPanel unitsInTerritoryPanel(
      final Territory territory, final UiContext uiContext) {
    final JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createEmptyBorder(2, 20, 2, 2));
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    final List<UnitCategory> units =
        UnitSeparator.getSortedUnitCategories(territory, uiContext.getMapData());
    @Nullable GamePlayer currentPlayer = null;
    for (final UnitCategory item : units) {
      // separate players with a separator
      if (!item.getOwner().equals(currentPlayer)) {
        currentPlayer = item.getOwner();
        panel.add(Box.createVerticalStrut(15));
      }
      final Optional<ImageIcon> unitIcon =
          uiContext.getUnitImageFactory().getIcon(ImageKey.of(item));
      if (unitIcon.isPresent()) {
        // overlay flag onto upper-right of icon
        final ImageIcon flagIcon =
            new ImageIcon(uiContext.getFlagImageFactory().getSmallFlag(item.getOwner()));
        final Icon flaggedUnitIcon =
            new OverlayIcon(
                unitIcon.get(),
                flagIcon,
                unitIcon.get().getIconWidth() - (flagIcon.getIconWidth() / 2),
                0);
        final JLabel label =
            new JLabel("x" + item.getUnits().size(), flaggedUnitIcon, SwingConstants.LEFT);
        final String toolTipText =
            "<html>"
                + item.getType().getName()
                + ": "
                + TooltipProperties.getInstance().getTooltip(item.getType(), currentPlayer)
                + "</html>";
        label.setToolTipText(toolTipText);
        panel.add(label);
      }
    }
    return panel;
  }
}
