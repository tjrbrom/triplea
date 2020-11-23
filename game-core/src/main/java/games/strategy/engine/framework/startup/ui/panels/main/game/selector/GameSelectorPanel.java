package games.strategy.engine.framework.startup.ui.panels.main.game.selector;

import static org.triplea.swing.SwingComponents.DialogWithLinksParams;
import static org.triplea.swing.SwingComponents.DialogWithLinksTypes;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.properties.IEditableProperty;
import games.strategy.engine.data.properties.PropertiesUi;
import games.strategy.engine.framework.HeadlessAutoSaveType;
import games.strategy.engine.framework.map.download.DownloadMapsWindow;
import games.strategy.engine.framework.map.file.system.loader.AvailableGamesFileSystemReader;
import games.strategy.engine.framework.startup.mc.ClientModel;
import games.strategy.engine.framework.startup.ui.FileBackedGamePropertiesCache;
import games.strategy.engine.framework.startup.ui.IGamePropertiesCache;
import games.strategy.engine.framework.system.SystemProperties;
import games.strategy.engine.framework.ui.GameChooser;
import games.strategy.engine.framework.ui.GameChooserModel;
import games.strategy.engine.framework.ui.background.BackgroundTaskRunner;
import games.strategy.engine.framework.ui.background.TaskRunner;
import games.strategy.triplea.ResourceLoader;
import games.strategy.triplea.UrlConstants;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import org.triplea.injection.Injections;
import org.triplea.swing.DialogBuilder;
import org.triplea.swing.JButtonBuilder;
import org.triplea.swing.SwingAction;
import org.triplea.swing.SwingComponents;
import org.triplea.swing.jpanel.GridBagConstraintsBuilder;

/**
 * Left hand side panel of the launcher screen that has various info, like selected game and engine
 * version.
 */
public final class GameSelectorPanel extends JPanel implements Observer {
  private static final long serialVersionUID = -4598107601238030020L;

  private final GameSelectorModel model;
  private final IGamePropertiesCache gamePropertiesCache = new FileBackedGamePropertiesCache();
  private final Map<String, Object> originalPropertiesMap = new HashMap<>();
  private final JLabel nameText = new JLabel();
  private final JLabel versionText = new JLabel();
  private final JLabel saveGameText = new JLabel();
  private final JLabel roundText = new JLabel();
  private final JButton loadSavedGame =
      new JButtonBuilder()
          .title("Open Saved Game")
          .toolTip("Open a previously saved game, or an autosave.")
          .build();
  private final JButton loadNewGame =
      new JButtonBuilder()
          .title("Select Map")
          .toolTip(
              "<html>Select a game from all the maps/games that come with TripleA, "
                  + "<br>and the ones you have downloaded.</html>")
          .build();
  private final JButton mapOptions =
      new JButtonBuilder()
          .title("Map Options")
          .toolTip(
              "<html>Set options for the currently selected game, <br>such as enabling/disabling "
                  + "Low Luck, or Technology, etc.</html>")
          .build();

  public GameSelectorPanel(final GameSelectorModel model) {
    this.model = model;
    final GameData data = model.getGameData();
    if (data != null) {
      setOriginalPropertiesMap(data);
      gamePropertiesCache.loadCachedGamePropertiesInto(data);
    }

    setLayout(new GridBagLayout());

    final JLabel logoLabel =
        new JLabel(
            new ImageIcon(
                ResourceLoader.loadImageAssert(Path.of("launch_screens", "triplea-logo.png"))));

    int row = 0;
    add(
        logoLabel,
        new GridBagConstraintsBuilder(0, row)
            .gridWidth(2)
            .insets(new Insets(10, 10, 3, 5))
            .build());
    row++;

    add(new JLabel("Java Version:"), buildGridCell(0, row, new Insets(10, 10, 3, 5)));
    add(
        new JLabel(SystemProperties.getJavaVersion()),
        buildGridCell(1, row, new Insets(10, 0, 3, 0)));
    row++;

    add(new JLabel("Engine Version:"), buildGridCell(0, row, new Insets(0, 10, 3, 5)));
    add(
        new JLabel(Injections.getInstance().getEngineVersion().toString()),
        buildGridCell(1, row, new Insets(0, 0, 3, 0)));
    row++;

    add(new JLabel("Map Name:"), buildGridCell(0, row, new Insets(0, 10, 3, 5)));
    add(nameText, buildGridCell(1, row, new Insets(0, 0, 3, 0)));
    row++;

    add(new JLabel("Map Version:"), buildGridCell(0, row, new Insets(0, 10, 3, 5)));
    add(versionText, buildGridCell(1, row, new Insets(0, 0, 3, 0)));
    row++;

    add(new JLabel("Game Round:"), buildGridCell(0, row, new Insets(0, 10, 3, 5)));
    add(roundText, buildGridCell(1, row, new Insets(0, 0, 3, 0)));
    row++;

    add(new JLabel("Loaded Savegame:"), buildGridCell(0, row, new Insets(20, 10, 3, 5)));
    row++;

    add(saveGameText, buildGridRow(0, row, new Insets(0, 10, 3, 5)));
    row++;

    add(loadNewGame, buildGridRow(0, row, new Insets(25, 10, 10, 10)));
    row++;

    add(loadSavedGame, buildGridRow(0, row, new Insets(0, 10, 10, 10)));
    row++;

    final JButton downloadMapButton =
        new JButtonBuilder()
            .title("Download Maps")
            .toolTip("Click this button to install additional maps")
            .actionListener(DownloadMapsWindow::showDownloadMapsWindow)
            .build();
    add(downloadMapButton, buildGridRow(0, row, new Insets(0, 10, 10, 10)));
    row++;

    add(mapOptions, buildGridRow(0, row, new Insets(25, 10, 10, 10)));
    row++;

    // spacer
    add(
        new JPanel(),
        new GridBagConstraints(
            0,
            row,
            2,
            1,
            1,
            1,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH,
            new Insets(0, 0, 0, 0),
            0,
            0));

    loadNewGame.addActionListener(
        e -> {
          if (canSelectLocalGameData()) {
            selectGameFile();
          } else if (canChangeHostBotGameData()) {
            final ClientModel clientModelForHostBots = model.getClientModelForHostBots();
            if (clientModelForHostBots != null) {
              clientModelForHostBots
                  .getHostBotSetMapClientAction(GameSelectorPanel.this)
                  .actionPerformed(e);
            }
          }
        });
    loadSavedGame.addActionListener(
        e -> {
          if (canSelectLocalGameData()) {
            selectSavedGameFile();
          } else if (canChangeHostBotGameData()) {
            final ClientModel clientModelForHostBots = model.getClientModelForHostBots();
            if (clientModelForHostBots != null) {
              final JPopupMenu menu = new JPopupMenu();
              menu.add(
                  clientModelForHostBots.getHostBotChangeGameToSaveGameClientAction(
                      JOptionPane.getFrameForComponent(this)));
              menu.add(
                  clientModelForHostBots.getHostBotChangeToAutosaveClientAction(
                      GameSelectorPanel.this, HeadlessAutoSaveType.DEFAULT));
              menu.add(
                  clientModelForHostBots.getHostBotChangeToAutosaveClientAction(
                      GameSelectorPanel.this, HeadlessAutoSaveType.ODD_ROUND));
              menu.add(
                  clientModelForHostBots.getHostBotChangeToAutosaveClientAction(
                      GameSelectorPanel.this, HeadlessAutoSaveType.EVEN_ROUND));
              menu.add(
                  clientModelForHostBots.getHostBotChangeToAutosaveClientAction(
                      GameSelectorPanel.this, HeadlessAutoSaveType.END_TURN));
              menu.add(
                  clientModelForHostBots.getHostBotChangeToAutosaveClientAction(
                      GameSelectorPanel.this, HeadlessAutoSaveType.BEFORE_BATTLE));
              menu.add(
                  clientModelForHostBots.getHostBotChangeToAutosaveClientAction(
                      GameSelectorPanel.this, HeadlessAutoSaveType.AFTER_BATTLE));
              menu.add(
                  clientModelForHostBots.getHostBotChangeToAutosaveClientAction(
                      GameSelectorPanel.this, HeadlessAutoSaveType.AFTER_COMBAT_MOVE));
              menu.add(
                  clientModelForHostBots.getHostBotChangeToAutosaveClientAction(
                      GameSelectorPanel.this, HeadlessAutoSaveType.AFTER_NON_COMBAT_MOVE));
              menu.add(
                  clientModelForHostBots.getHostBotGetGameSaveClientAction(GameSelectorPanel.this));
              final Point point = loadSavedGame.getLocation();
              menu.show(GameSelectorPanel.this, point.x + loadSavedGame.getWidth(), point.y);
            }
          }
        });
    mapOptions.addActionListener(
        e -> {
          if (canSelectLocalGameData()) {
            selectGameOptions();
          } else if (canChangeHostBotGameData()) {
            final ClientModel clientModelForHostBots = model.getClientModelForHostBots();
            if (clientModelForHostBots != null) {
              clientModelForHostBots
                  .getHostBotChangeGameOptionsClientAction(GameSelectorPanel.this)
                  .actionPerformed(e);
            }
          }
        });

    updateGameData();
  }

  private static GridBagConstraints buildGridCell(final int x, final int y, final Insets insets) {
    return buildGrid(x, y, insets, 1);
  }

  private static GridBagConstraints buildGridRow(final int x, final int y, final Insets insets) {
    return buildGrid(x, y, insets, 2);
  }

  private static GridBagConstraints buildGrid(
      final int x, final int y, final Insets insets, final int width) {
    final int gridHeight = 1;
    final double weigthX = 0;
    final double weigthY = 0;
    final int anchor = GridBagConstraints.WEST;
    final int fill = GridBagConstraints.NONE;
    final int ipadx = 0;
    final int ipady = 0;

    return new GridBagConstraints(
        x, y, width, gridHeight, weigthX, weigthY, anchor, fill, insets, ipadx, ipady);
  }

  private void setOriginalPropertiesMap(final GameData data) {
    originalPropertiesMap.clear();
    if (data != null) {
      for (final IEditableProperty<?> property : data.getProperties().getEditableProperties()) {
        originalPropertiesMap.put(property.getName(), property.getValue());
      }
    }
  }

  private void selectGameOptions() {
    // backup current game properties before showing dialog
    final Map<String, Object> currentPropertiesMap = new HashMap<>();
    for (final IEditableProperty<?> property :
        model.getGameData().getProperties().getEditableProperties()) {
      currentPropertiesMap.put(property.getName(), property.getValue());
    }
    final PropertiesUi panel = new PropertiesUi(model.getGameData().getProperties(), true);
    final JScrollPane scroll = new JScrollPane(panel);
    scroll.setBorder(null);
    scroll.getViewport().setBorder(null);
    final JOptionPane pane = new JOptionPane(scroll, JOptionPane.PLAIN_MESSAGE);
    final String ok = "OK";
    final String cancel = "Cancel";
    final String makeDefault = "Make Default";
    final String reset = "Reset";
    pane.setOptions(new Object[] {ok, makeDefault, reset, cancel});
    final JDialog window = pane.createDialog(JOptionPane.getFrameForComponent(this), "Map Options");
    window.setVisible(true);
    final Object buttonPressed = pane.getValue();
    if (buttonPressed == null || buttonPressed.equals(cancel)) {
      // restore properties, if cancel was pressed, or window was closed
      for (final IEditableProperty<?> property :
          model.getGameData().getProperties().getEditableProperties()) {
        property.validateAndSet(currentPropertiesMap.get(property.getName()));
      }
    } else if (buttonPressed.equals(reset)) {
      if (!originalPropertiesMap.isEmpty()) {
        // restore properties, if cancel was pressed, or window was closed
        for (final IEditableProperty<?> property :
            model.getGameData().getProperties().getEditableProperties()) {
          property.validateAndSet(originalPropertiesMap.get(property.getName()));
        }
        selectGameOptions();
      }
    } else if (buttonPressed.equals(makeDefault)) {
      gamePropertiesCache.cacheGameProperties(model.getGameData());
    }
  }

  private void updateGameData() {
    SwingAction.invokeNowOrLater(
        () -> {
          nameText.setText(model.getGameName());
          versionText.setText(model.getGameVersion());
          roundText.setText(model.getGameRound());
          saveGameText.setText(model.getFileName());

          final boolean canSelectGameData = canSelectLocalGameData();
          final boolean canChangeHostBotGameData = canChangeHostBotGameData();
          loadSavedGame.setEnabled(canSelectGameData || canChangeHostBotGameData);
          loadNewGame.setEnabled(canSelectGameData || canChangeHostBotGameData);
          // Disable game options if there are none.
          if (canChangeHostBotGameData
              || (canSelectGameData
                  && model.getGameData() != null
                  && !model.getGameData().getProperties().getEditableProperties().isEmpty())) {
            mapOptions.setEnabled(true);
          } else {
            mapOptions.setEnabled(false);
          }
        });
  }

  private boolean canSelectLocalGameData() {
    return model != null && model.isCanSelect();
  }

  private boolean canChangeHostBotGameData() {
    return model != null && model.isHostIsHeadlessBot();
  }

  @Override
  public void update(final Observable o, final Object arg) {
    updateGameData();
  }

  private void selectSavedGameFile() {
    GameFileSelector.builder()
        .fileDoesNotExistAction(
            file ->
                DialogBuilder.builder()
                    .parent(this)
                    .title("Save Game File Not Found")
                    .errorMessage("File does not exist: " + file.getAbsolutePath())
                    .showDialog())
        .build()
        .selectGameFile(JOptionPane.getFrameForComponent(this))
        .ifPresent(
            file ->
                TaskRunner.builder()
                    .waitDialogTitle("Loading Save Game")
                    .exceptionHandler(
                        e ->
                            SwingComponents.showDialogWithLinks(
                                DialogWithLinksParams.builder()
                                    .title("Failed To Load Save Game")
                                    .dialogType(DialogWithLinksTypes.ERROR)
                                    .dialogText(
                                        String.format(
                                            "<html>Error: %s<br/><br/>"
                                                + "If this is not expected, please "
                                                + "file a <a href=%s>bug report</a><br/>"
                                                + "and attach the error message above and the "
                                                + "save game you are trying to load.",
                                            e.getMessage(), UrlConstants.GITHUB_ISSUES))
                                    .build()))
                    .build()
                    .run(
                        () -> {
                          if (model.load(file)) {
                            setOriginalPropertiesMap(model.getGameData());
                          }
                        }));
  }

  private void selectGameFile() {
    try {
      final GameChooserModel gameChooserModel =
          new GameChooserModel(
              BackgroundTaskRunner.runInBackgroundAndReturn(
                  "Loading all available games...", AvailableGamesFileSystemReader::parseMapFiles));
      final URI gameUri =
          GameChooser.chooseGame(
                  JOptionPane.getFrameForComponent(this), gameChooserModel, model.getGameName())
              .orElse(null);
      if (gameUri != null) {
        BackgroundTaskRunner.runInBackground("Loading map...", () -> model.load(gameUri));
        // warning: NPE check is not to protect against concurrency, another thread could still null
        // out game data.
        // The NPE check is to protect against the case where there are errors loading game, in
        // which case
        // we'll have a null game data.
        if (model.getGameData() != null) {
          setOriginalPropertiesMap(model.getGameData());
          // only for new games, not saved games, we set the default options, and set them only once
          // (the first time it is loaded)
          gamePropertiesCache.loadCachedGamePropertiesInto(model.getGameData());
        }
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
