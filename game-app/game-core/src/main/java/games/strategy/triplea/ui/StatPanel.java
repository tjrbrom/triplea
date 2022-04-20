package games.strategy.triplea.ui;

import games.strategy.engine.data.Change;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Resource;
import games.strategy.engine.data.events.GameDataChangeListener;
import games.strategy.engine.stats.IStat;
import games.strategy.engine.stats.ProductionStat;
import games.strategy.engine.stats.ResourceStat;
import games.strategy.engine.stats.TuvStat;
import games.strategy.engine.stats.UnitsStat;
import games.strategy.engine.stats.VictoryCityStat;
import games.strategy.triplea.Constants;
import games.strategy.triplea.attachments.PlayerAttachment;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.delegate.TechAdvance;
import games.strategy.triplea.delegate.TechTracker;
import games.strategy.triplea.ui.mapdata.MapData;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.Image;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

class StatPanel extends JPanel implements GameDataChangeListener {
  private static final long serialVersionUID = 4340684166664492498L;

  IStat[] stats;
  private final Map<GamePlayer, ImageIcon> mapPlayerImage = new HashMap<>();
  protected GameData gameData;
  private final UiContext uiContext;
  private final StatTableModel dataModel;
  private final TechTableModel techModel;

  StatPanel(final GameData data, final UiContext uiContext) {
    this.gameData = data;
    this.uiContext = uiContext;
    dataModel = new StatTableModel();
    techModel = new TechTableModel();
    fillPlayerIcons();
    initLayout();
    gameData.addDataChangeListener(this);
  }

  protected void initLayout() {
    final boolean hasTech =
        !TechAdvance.getTechAdvances(gameData.getTechnologyFrontier(), null).isEmpty();
    // do no include a grid box for tech if there is no tech
    setLayout(new GridLayout((hasTech ? 2 : 1), 1));
    final JTable statsTable = new JTable(dataModel);
    statsTable.getTableHeader().setReorderingAllowed(false);
    statsTable.getColumnModel().getColumn(0).setPreferredWidth(175);
    add(new JScrollPane(statsTable));
    // if no technologies, do not show the tech table
    if (!hasTech) {
      return;
    }
    final JTable techTable = new JTable(techModel);
    techTable.getTableHeader().setReorderingAllowed(false);
    techTable.getColumnModel().getColumn(0).setPreferredWidth(500);
    // show icons for players:
    final TableCellRenderer componentRenderer = new JComponentTableCellRenderer();
    for (int i = 1; i < techTable.getColumnCount(); i++) {
      final TableColumn column = techTable.getColumnModel().getColumn(i);
      column.setHeaderRenderer(componentRenderer);
      final String player = techTable.getColumnName(i);
      final JLabel value = new JLabel("", getIcon(player), SwingConstants.CENTER);
      value.setToolTipText(player);
      column.setHeaderValue(value);
    }
    add(new JScrollPane(techTable));
  }

  public void setGameData(final GameData data) {
    gameData.removeDataChangeListener(this);
    gameData = data;
    gameData.addDataChangeListener(this);
    gameDataChanged(null);
  }

  @Override
  public void gameDataChanged(final Change change) {
    dataModel.markDirty();
    techModel.markDirty();
    SwingUtilities.invokeLater(this::repaint);
  }

  /**
   * Gets the small flag for a given PlayerId.
   *
   * @param player the player to get the flag for
   * @return ImageIcon small flag
   */
  protected ImageIcon getIcon(final GamePlayer player) {
    ImageIcon icon = mapPlayerImage.get(player);
    if (icon == null && uiContext != null) {
      final Image img = uiContext.getFlagImageFactory().getSmallFlag(player);
      icon = new ImageIcon(img);
      icon.setDescription(player.getName());
      mapPlayerImage.put(player, icon);
    }
    return icon;
  }

  protected ImageIcon getIcon(final String playerName) {
    final GamePlayer player = gameData.getPlayerList().getPlayerId(playerName);
    if (player == null) {
      return null;
    }
    return getIcon(player);
  }

  private void fillPlayerIcons() {
    for (final GamePlayer p : gameData.getPlayerList().getPlayers()) {
      getIcon(p);
    }
  }

  static class JComponentTableCellRenderer implements TableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(
        final JTable table,
        final Object value,
        final boolean isSelected,
        final boolean hasFocus,
        final int row,
        final int column) {
      return (JComponent) value;
    }
  }

  /** Custom table model. This model is thread safe. */
  class StatTableModel extends AbstractTableModel {
    private static final long serialVersionUID = -6156153062049822444L;
    /* Underlying data for the table. If null, needs to be computed. */
    private String[][] collectedData;

    StatTableModel() {
      setStatColumns();
    }

    void setStatColumns() {
      stats = new IStat[] {new PuStat(), new ProductionStat(), new UnitsStat(), new TuvStat()};
      if (gameData.getMap().getTerritories().stream().anyMatch(Matches.territoryIsVictoryCity())) {
        final List<IStat> stats = new ArrayList<>(List.of(StatPanel.this.stats));
        stats.add(new VictoryCityStat());
        StatPanel.this.stats = stats.toArray(new IStat[0]);
      }
      // only add the vps in pacific
      if (gameData.getProperties().get(Constants.PACIFIC_THEATER, false)) {
        final List<IStat> stats = new ArrayList<>(List.of(StatPanel.this.stats));
        stats.add(new VpStat());
        StatPanel.this.stats = stats.toArray(new IStat[0]);
      }
    }

    private synchronized void loadData() {
      // copy so acquire/release read lock are on the same object!
      final GameData gameData = StatPanel.this.gameData;
      gameData.acquireReadLock();
      try {
        final List<GamePlayer> players = gameData.getPlayerList().getSortedPlayers();
        final Collection<String> alliances = gameData.getAllianceTracker().getAlliances();
        collectedData = new String[players.size() + alliances.size()][stats.length + 1];
        int row = 0;
        for (final GamePlayer player : players) {
          collectedData[row][0] = player.getName();
          for (int i = 0; i < stats.length; i++) {
            collectedData[row][i + 1] =
                IStat.DECIMAL_FORMAT.format(
                    stats[i].getValue(player, gameData, uiContext.getMapData()));
          }
          row++;
        }
        for (final String alliance : alliances) {
          collectedData[row][0] = alliance;
          for (int i = 0; i < stats.length; i++) {
            collectedData[row][i + 1] =
                IStat.DECIMAL_FORMAT.format(
                    stats[i].getValue(alliance, gameData, uiContext.getMapData()));
          }
          row++;
        }
      } finally {
        gameData.releaseReadLock();
      }
    }

    /*
     * Re-calcs the underlying data in a lazy manner.
     * Limitation: This is not a thread-safe implementation.
     */
    @Override
    public synchronized Object getValueAt(final int row, final int col) {
      return getCollectedData()[row][col];
    }

    // Trivial implementations of required methods
    @Override
    public String getColumnName(final int col) {
      if (col == 0) {
        return "Player";
      }
      return stats[col - 1].getName();
    }

    @Override
    public int getColumnCount() {
      return stats.length + 1;
    }

    @Override
    public int getRowCount() {
      return getCollectedData().length;
    }

    synchronized void markDirty() {
      collectedData = null;
    }

    private synchronized String[][] getCollectedData() {
      if (collectedData == null) {
        loadData();
      }
      return collectedData;
    }
  }

  class TechTableModel extends AbstractTableModel {
    private static final long serialVersionUID = -4612476336419396081L;
    /* Flag to indicate whether data needs to be recalculated */
    private boolean isDirty = true;
    /* Column Header Names */
    /* Row Header Names */
    private String[] colList;
    /* Underlying data for the table */
    private final String[][] data;
    /* Convenience mapping of country names -> col */
    private final Map<String, Integer> colMap = new HashMap<>();
    /* Convenience mapping of technology names -> row */
    private final Map<String, Integer> rowMap = new HashMap<>();

    TechTableModel() {
      initColList();
      /* Load the country -> col mapping */
      for (int i = 0; i < colList.length; i++) {
        colMap.put(colList[i], i + 1);
      }
      boolean useTech = false;
      final GameData gameData = StatPanel.this.gameData;
      try {
        gameData.acquireReadLock();
        final int numTechs = TechAdvance.getTechAdvances(gameData.getTechnologyFrontier()).size();
        if (gameData.getResourceList().getResource(Constants.TECH_TOKENS) != null) {
          useTech = true;
          data = new String[numTechs + 1][colList.length + 2];
        } else {
          data = new String[numTechs][colList.length + 1];
        }
      } finally {
        gameData.releaseReadLock();
      }
      /* Load the technology -> row mapping */
      int row = 0;
      if (useTech) {
        rowMap.put("Tokens", row);
        data[row][0] = "Tokens";
        row++;
      }
      final List<TechAdvance> techAdvances =
          TechAdvance.getTechAdvances(gameData.getTechnologyFrontier(), null);
      for (final TechAdvance tech : techAdvances) {
        rowMap.put(tech.getName(), row);
        data[row][0] = tech.getName();
        row++;
      }
      clearAdvances();
    }

    private void clearAdvances() {
      /* Initialize the table with the tech names */
      for (int i = 0; i < data.length; i++) {
        for (int j = 1; j <= colList.length; j++) {
          data[i][j] = "";
        }
      }
    }

    private void initColList() {
      final List<GamePlayer> players = gameData.getPlayerList().getPlayers();
      colList = new String[players.size()];
      for (int i = 0; i < players.size(); i++) {
        colList[i] = players.get(i).getName();
      }
      Arrays.sort(colList);
    }

    void update() {
      clearAdvances();
      // copy so acquire/release read lock are on the same object!
      final GameData gameData = StatPanel.this.gameData;
      gameData.acquireReadLock();
      try {
        for (final GamePlayer pid : gameData.getPlayerList().getPlayers()) {
          if (colMap.get(pid.getName()) == null) {
            throw new IllegalStateException(
                "Unexpected player in GameData.getPlayerList()" + pid.getName());
          }
          final int col = colMap.get(pid.getName());
          int row = 0;
          if (StatPanel.this.gameData.getResourceList().getResource(Constants.TECH_TOKENS)
              != null) {
            final int tokens = pid.getResources().getQuantity(Constants.TECH_TOKENS);
            data[row][col] = Integer.toString(tokens);
          }
          final List<TechAdvance> advancesAll =
              TechAdvance.getTechAdvances(StatPanel.this.gameData.getTechnologyFrontier());
          final List<TechAdvance> has =
              TechAdvance.getTechAdvances(StatPanel.this.gameData.getTechnologyFrontier(), pid);
          for (final TechAdvance advance : advancesAll) {
            if (!has.contains(advance)) {
              row = rowMap.get(advance.getName());
              data[row][col] = "-";
            }
          }
          for (final TechAdvance advance :
              TechTracker.getCurrentTechAdvances(
                  pid, StatPanel.this.gameData.getTechnologyFrontier())) {
            row = rowMap.get(advance.getName());
            data[row][col] = "X";
          }
        }
      } finally {
        gameData.releaseReadLock();
      }
    }

    @Override
    public String getColumnName(final int col) {
      if (col == 0) {
        return "Technology";
      }
      return colList[col - 1];
    }

    /*
     * Recalcs the underlying data in a lazy manner.
     * Limitation: This is not a thread-safe implementation.
     */
    @Override
    public Object getValueAt(final int row, final int col) {
      if (isDirty) {
        update();
        isDirty = false;
      }
      return data[row][col];
    }

    // Trivial implementations of required methods
    @Override
    public int getColumnCount() {
      return colList.length + 1;
    }

    @Override
    public int getRowCount() {
      return data.length;
    }

    void markDirty() {
      isDirty = true;
    }
  }

  class PuStat extends ResourceStat {
    PuStat() {
      super(getResourcePUs(gameData));
    }
  }

  private static Resource getResourcePUs(final GameData data) {
    final Resource pus;
    try {
      data.acquireReadLock();
      pus = data.getResourceList().getResource(Constants.PUS);
    } finally {
      data.releaseReadLock();
    }
    return pus;
  }

  static class VpStat implements IStat {
    @Override
    public String getName() {
      return "VPs";
    }

    @Override
    public double getValue(final GamePlayer player, final GameData data, final MapData mapData) {
      final PlayerAttachment pa = PlayerAttachment.get(player);
      if (pa != null) {
        return pa.getVps();
      }
      return 0;
    }
  }
}
