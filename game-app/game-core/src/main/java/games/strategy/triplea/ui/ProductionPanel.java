package games.strategy.triplea.ui;

import com.google.common.base.Preconditions;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.NamedAttachable;
import games.strategy.engine.data.ProductionRule;
import games.strategy.engine.data.Resource;
import games.strategy.engine.data.ResourceCollection;
import games.strategy.engine.data.UnitType;
import games.strategy.triplea.Constants;
import games.strategy.triplea.attachments.UnitAttachment;
import games.strategy.triplea.image.UnitImageFactory.ImageKey;
import games.strategy.ui.ScrollableTextField;
import games.strategy.ui.ScrollableTextFieldListener;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import org.triplea.java.collections.IntegerMap;
import org.triplea.swing.SwingAction;
import org.triplea.swing.key.binding.ButtonDownMask;
import org.triplea.swing.key.binding.KeyCode;
import org.triplea.swing.key.binding.KeyCombination;
import org.triplea.swing.key.binding.SwingKeyBinding;

class ProductionPanel extends JPanel {
  private static final long serialVersionUID = -1539053979479586609L;

  protected final UiContext uiContext;
  protected final List<Rule> rules = new ArrayList<>();
  protected final JLabel left = new JLabel();
  protected final JPanel remainingResources = new JPanel();
  protected JButton done;
  protected GamePlayer gamePlayer;
  protected GameData data;

  private JDialog dialog;
  private boolean bid;
  final Action doneAction = SwingAction.of("Done", () -> dialog.setVisible(false));

  ProductionPanel(final UiContext uiContext) {
    this.uiContext = uiContext;
  }

  static IntegerMap<ProductionRule> getProduction(
      final GamePlayer gamePlayer,
      final JFrame parent,
      final GameData data,
      final boolean bid,
      final IntegerMap<ProductionRule> initialPurchase,
      final UiContext uiContext) {
    return new ProductionPanel(uiContext).show(gamePlayer, parent, data, bid, initialPurchase);
  }

  private IntegerMap<ProductionRule> getProduction() {
    final IntegerMap<ProductionRule> prod = new IntegerMap<>();
    for (final Rule rule : rules) {
      final int quantity = rule.getQuantity();
      if (quantity != 0) {
        prod.put(rule.getProductionRule(), quantity);
      }
    }
    return prod;
  }

  /** Shows the production panel, and returns a map of selected rules. */
  IntegerMap<ProductionRule> show(
      final GamePlayer gamePlayer,
      final JFrame parent,
      final GameData data,
      final boolean bid,
      final IntegerMap<ProductionRule> initialPurchase) {
    dialog = new JDialog(parent, "Produce", true);
    dialog.getContentPane().add(this);

    SwingKeyBinding.addKeyBinding(
        dialog,
        KeyCombination.of(KeyCode.ENTER, ButtonDownMask.CTRL),
        () -> dialog.setVisible(false));
    SwingKeyBinding.addKeyBinding(dialog, KeyCode.ESCAPE, () -> dialog.setVisible(false));

    this.bid = bid;
    this.data = data;
    this.initRules(gamePlayer, initialPurchase);
    this.initLayout();
    this.calculateLimits();
    dialog.pack();
    dialog.setLocationRelativeTo(parent);
    SwingUtilities.invokeLater(() -> done.requestFocusInWindow());
    // making the dialog visible will block until it is closed
    dialog.setVisible(true);
    dialog.dispose();
    return getProduction();
  }

  // this method can be accessed by subclasses
  protected List<Rule> getRules() {
    return rules;
  }

  protected void initRules(
      final GamePlayer player, final IntegerMap<ProductionRule> initialPurchase) {
    this.data.acquireReadLock();
    try {
      gamePlayer = player;
      for (final ProductionRule productionRule : player.getProductionFrontier()) {
        final Rule rule = new Rule(productionRule, player);
        final int initialQuantity = initialPurchase.getInt(productionRule);
        rule.setQuantity(initialQuantity);
        rules.add(rule);
      }
    } finally {
      this.data.releaseReadLock();
    }
  }

  protected void initLayout() {
    final Insets nullInsets = new Insets(0, 0, 0, 0);
    this.removeAll();
    this.setLayout(new GridBagLayout());
    final JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());
    final JLabel legendLabel = new JLabel("Attack | Defense | Movement");
    this.add(
        legendLabel,
        new GridBagConstraints(
            0,
            0,
            30,
            1,
            1,
            1,
            GridBagConstraints.EAST,
            GridBagConstraints.HORIZONTAL,
            new Insets(8, 8, 8, 0),
            0,
            0));
    int rows = rules.size() / 7;
    rows = Math.max(2, rows);
    for (int x = 0; x < rules.size(); x++) {
      panel.add(
          rules.get(x).getPanelComponent(),
          new GridBagConstraints(
              x / rows,
              (x % rows),
              1,
              1,
              10,
              10,
              GridBagConstraints.EAST,
              GridBagConstraints.BOTH,
              nullInsets,
              0,
              0));
    }
    final JScrollPane scroll = new JScrollPane(panel);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    final Dimension screenResolution = Toolkit.getDefaultToolkit().getScreenSize();
    final int availHeight = screenResolution.height - 80;
    final int availWidth = screenResolution.width - 30;
    final int availWidthRules = availWidth - 16;
    final int availHeightRules = availHeight - 116;
    scroll.setPreferredSize(
        new Dimension(
            (scroll.getPreferredSize().width > availWidthRules
                ? availWidthRules
                : scroll.getPreferredSize().width
                    + (scroll.getPreferredSize().height > availHeightRules ? 25 : 0)),
            (scroll.getPreferredSize().height > availHeightRules
                ? availHeightRules
                : scroll.getPreferredSize().height
                    + (scroll.getPreferredSize().width > availWidthRules ? 25 : 0))));
    this.add(
        scroll,
        new GridBagConstraints(
            0,
            1,
            30,
            1,
            100,
            100,
            GridBagConstraints.WEST,
            GridBagConstraints.BOTH,
            new Insets(8, 8, 8, 4),
            0,
            0));
    this.add(
        left,
        new GridBagConstraints(
            0,
            2,
            1,
            1,
            1,
            1,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(8, 8, 0, 12),
            0,
            0));
    this.add(
        remainingResources,
        new GridBagConstraints(
            1,
            2,
            1,
            1,
            1,
            1,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(8, 8, 0, 12),
            0,
            0));
    done = new JButton(doneAction);
    done.setToolTipText(
        "Click this button or press 'ctrl+enter' to confirm purchase and close this window");
    this.add(
        done,
        new GridBagConstraints(
            0,
            3,
            30,
            1,
            1,
            1,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(0, 0, 8, 0),
            0,
            0));
    this.setMaximumSize(new Dimension(availWidth, availHeight));
  }

  private void setLeft(final ResourceCollection resourceCollection, final int totalUnits) {
    remainingResources.removeAll();
    left.setText(String.format("%d total units purchased. Remaining resources: ", totalUnits));
    if (resourceCollection != null) {
      remainingResources.add(
          uiContext.getResourceImageFactory().getResourcesPanel(resourceCollection, gamePlayer));
    }
  }

  // This method can be overridden by subclasses
  protected void calculateLimits() {
    final ResourceCollection resources = getResources();
    final ResourceCollection spent = new ResourceCollection(data);
    int totalUnits = 0;
    for (final Rule current : rules) {
      spent.add(current.getCost(), current.getQuantity());
      totalUnits += current.getQuantity() * current.getProductionRule().getResults().totalValues();
    }
    final ResourceCollection leftToSpend = resources.difference(spent);
    setLeft(leftToSpend, totalUnits);
    for (final Rule current : rules) {
      int max = leftToSpend.fitsHowOften(current.getCost());
      max += current.getQuantity();
      current.setMax(max);
    }
  }

  protected ResourceCollection getResources() {
    if (bid) {
      Preconditions.checkState(gamePlayer != null, "bid was true while id is null");
      // TODO bid only allows you to add PU's to the bid... maybe upgrading Bids so multiple
      // resources can be given?
      final String propertyName = gamePlayer.getName() + " bid";
      final int bid = data.getProperties().get(propertyName, 0);
      final ResourceCollection bidCollection = new ResourceCollection(data);
      data.acquireReadLock();
      try {
        bidCollection.addResource(data.getResourceList().getResource(Constants.PUS), bid);
      } finally {
        data.releaseReadLock();
      }
      return bidCollection;
    }

    return (gamePlayer == null || gamePlayer.isNull())
        ? new ResourceCollection(data)
        : gamePlayer.getResources();
  }

  class Rule {
    private final IntegerMap<Resource> cost;
    private int quantity;
    private final ProductionRule rule;
    private final GamePlayer player;
    private final Set<ScrollableTextField> textFields = new HashSet<>();
    private final ScrollableTextFieldListener listener =
        new ScrollableTextFieldListener() {
          @Override
          public void changedValue(final ScrollableTextField stf) {
            if (stf.getValue() != quantity) {
              quantity = stf.getValue();
              calculateLimits();
              for (final ScrollableTextField textField : textFields) {
                if (!stf.equals(textField)) {
                  textField.setValue(quantity);
                }
              }
            }
          }
        };

    Rule(final ProductionRule rule, final GamePlayer player) {
      this.rule = rule;
      cost = rule.getCosts();
      this.player = player;
    }

    protected JPanel getPanelComponent() {
      final JPanel panel = new JPanel();
      panel.setLayout(new GridBagLayout());

      final JLabel name = new JLabel("  ");
      name.setFont(
          name.getFont().deriveFont(Map.of(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD)));
      final JLabel info = new JLabel("  ");
      final Color defaultForegroundLabelColor = name.getForeground();
      Optional<ImageIcon> icon = Optional.empty();
      final StringBuilder tooltip = new StringBuilder();
      final Set<NamedAttachable> results = new HashSet<>(rule.getResults().keySet());
      final Iterator<NamedAttachable> iter = results.iterator();
      while (iter.hasNext()) {
        final NamedAttachable resourceOrUnit = iter.next();
        if (resourceOrUnit instanceof UnitType) {
          final UnitType type = (UnitType) resourceOrUnit;
          icon =
              uiContext
                  .getUnitImageFactory()
                  .getIcon(ImageKey.builder().type(type).player(player).build());
          final UnitAttachment attach = UnitAttachment.get(type);
          final int attack = attach.getAttack(player);
          final int movement = attach.getMovement(player);
          final int defense = attach.getDefense(player);
          info.setText(attack + " | " + defense + " | " + movement);
          tooltip
              .append(type.getName())
              .append(": ")
              .append(TooltipProperties.getInstance().getTooltip(type, player));
          name.setText(type.getName());
          if (attach.getConsumesUnits() != null && attach.getConsumesUnits().totalValues() == 1) {
            name.setForeground(Color.CYAN);
          } else if (attach.getConsumesUnits() != null
              && attach.getConsumesUnits().totalValues() > 1) {
            name.setForeground(Color.BLUE);
          } else {
            name.setForeground(defaultForegroundLabelColor);
          }
        } else if (resourceOrUnit instanceof Resource) {
          final Resource resource = (Resource) resourceOrUnit;
          icon = Optional.of(uiContext.getResourceImageFactory().getIcon(resource, true));
          info.setText("resource");
          tooltip.append(resource.getName()).append(": resource");
          name.setText(resource.getName());
          name.setForeground(Color.GREEN);
        }
        if (iter.hasNext()) {
          tooltip.append("<br /><br /><br /><br />");
        }
      }

      final int numberOfUnitsGiven = rule.getResults().totalValues();
      final JPanel costPanel = new JPanel();
      costPanel.setLayout(new GridBagLayout());
      costPanel.setBorder(new EmptyBorder(0, 5, 0, 0));
      int count = 0;
      for (final Resource resource : cost.keySet()) {
        final JLabel resourceLabel = uiContext.getResourceImageFactory().getLabel(resource, cost);
        costPanel.add(
            resourceLabel,
            new GridBagConstraints(
                0,
                count++,
                1,
                1,
                1,
                1,
                GridBagConstraints.WEST,
                GridBagConstraints.NONE,
                new Insets(1, 0, 0, 0),
                0,
                0));
      }
      if (numberOfUnitsGiven > 1) {
        final JLabel numberOfUnitsLabel =
            new JLabel("<html>for " + numberOfUnitsGiven + "<br>" + " units</html>");
        numberOfUnitsLabel.setFont(numberOfUnitsLabel.getFont().deriveFont(12f));
        costPanel.add(
            numberOfUnitsLabel,
            new GridBagConstraints(
                0,
                count,
                1,
                1,
                1,
                1,
                GridBagConstraints.WEST,
                GridBagConstraints.NONE,
                new Insets(1, 0, 0, 0),
                0,
                0));
      }
      final JPanel label = new JPanel();
      icon.ifPresent(imageIcon -> label.add(new JLabel(imageIcon)));
      label.add(costPanel);

      final ScrollableTextField textField = new ScrollableTextField(0, Integer.MAX_VALUE);
      textField.setValue(quantity);

      final String toolTipText = "<html>" + tooltip + "</html>";
      info.setToolTipText(toolTipText);
      label.setToolTipText(toolTipText);

      final int space = 5;
      panel.add(
          name,
          new GridBagConstraints(
              0,
              0,
              1,
              1,
              0,
              0,
              GridBagConstraints.NORTH,
              GridBagConstraints.NONE,
              new Insets(2, 0, 0, 0),
              0,
              0));
      panel.add(
          info,
          new GridBagConstraints(
              0,
              1,
              1,
              1,
              0,
              0,
              GridBagConstraints.SOUTH,
              GridBagConstraints.NONE,
              new Insets(space, space, space, space),
              0,
              0));
      panel.add(
          label,
          new GridBagConstraints(
              0,
              2,
              1,
              1,
              1,
              1,
              GridBagConstraints.CENTER,
              GridBagConstraints.NONE,
              new Insets(space, space, space, space),
              0,
              0));
      panel.add(
          textField,
          new GridBagConstraints(
              0,
              3,
              1,
              1,
              0,
              0,
              GridBagConstraints.SOUTH,
              GridBagConstraints.NONE,
              new Insets(space, space, space, space),
              0,
              0));
      textField.addChangeListener(listener);
      textFields.add(textField);
      panel.setBorder(new EtchedBorder());
      return panel;
    }

    IntegerMap<Resource> getCost() {
      return cost;
    }

    int getQuantity() {
      return quantity;
    }

    void setQuantity(final int quantity) {
      this.quantity = quantity;
      for (final ScrollableTextField textField : textFields) {
        if (textField.getValue() != quantity) {
          textField.setValue(quantity);
        }
      }
    }

    ProductionRule getProductionRule() {
      return rule;
    }

    void setMax(final int max) {
      for (final ScrollableTextField textField : textFields) {
        textField.setMax(max);
      }
    }
  }
}
