package games.strategy.engine.framework.map.download;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import games.strategy.engine.framework.lookandfeel.LookAndFeelSwingFrameListener;
import games.strategy.engine.framework.map.download.DownloadFile.DownloadState;
import games.strategy.engine.framework.map.listing.MapListingFetcher;
import games.strategy.engine.framework.ui.background.BackgroundTaskRunner;
import games.strategy.triplea.EngineImageLoader;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import org.triplea.java.Interruptibles;
import org.triplea.java.ThreadRunner;
import org.triplea.swing.JButtonBuilder;
import org.triplea.swing.SwingComponents;
import org.triplea.swing.jpanel.JPanelBuilder;

/** Window that allows for map downloads and removal. */
@Slf4j
public class DownloadMapsWindow extends JFrame {
  private enum MapAction {
    INSTALL,
    UPDATE,
    REMOVE
  }

  private static final long serialVersionUID = -1542210716764178580L;
  private static final int WINDOW_WIDTH = 1200;
  private static final int WINDOW_HEIGHT = 700;
  private static final int DIVIDER_POSITION = WINDOW_HEIGHT - 150;
  private static final String MULTIPLE_SELECT_MSG =
      "You can select multiple maps by holding control or shift while clicking map names.";
  private static final SingletonManager SINGLETON_MANAGER = new SingletonManager();

  private final MapDownloadProgressPanel progressPanel;

  private DownloadMapsWindow(
      final Collection<String> pendingDownloadMapNames,
      final List<DownloadFileDescription> allDownloads) {
    super("Download Maps");

    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
    setLocationRelativeTo(null);
    setMinimumSize(new Dimension(200, 200));

    setIconImage(EngineImageLoader.loadFrameIcon());
    progressPanel = new MapDownloadProgressPanel();

    final Set<DownloadFileDescription> pendingDownloads = new HashSet<>();
    final Collection<String> unknownMapNames = new ArrayList<>();
    for (final String mapName : pendingDownloadMapNames) {
      findMap(mapName, allDownloads)
          .ifPresentOrElse(pendingDownloads::add, () -> unknownMapNames.add(mapName));
    }
    if (!pendingDownloads.isEmpty()) {
      progressPanel.download(pendingDownloads);
    }

    pendingDownloads.addAll(
        DownloadCoordinator.instance.getDownloads().stream()
            .filter(download -> download.getDownloadState() != DownloadState.CANCELLED)
            .map(DownloadFile::getDownload)
            .collect(Collectors.toList()));

    if (!unknownMapNames.isEmpty()) {
      SwingComponents.newMessageDialog(formatIgnoredPendingMapsMessage(unknownMapNames));
    }

    SwingComponents.addWindowClosingListener(this, progressPanel::cancel);

    final Component outerTabs = newAvailableInstalledTabbedPanel(allDownloads, pendingDownloads);

    final JSplitPane splitPane =
        new JSplitPane(
            JSplitPane.VERTICAL_SPLIT, outerTabs, SwingComponents.newJScrollPane(progressPanel));
    splitPane.setDividerLocation(DIVIDER_POSITION);
    add(splitPane);
  }

  /**
   * Shows the Download Maps window.
   *
   * @throws IllegalStateException If this method is not called from the EDT.
   */
  public static void showDownloadMapsWindow() {
    checkState(SwingUtilities.isEventDispatchThread());

    showDownloadMapsWindowAndDownload(List.of());
  }

  /**
   * Shows the Download Maps window and immediately begins downloading the specified map in the
   * background.
   *
   * <p>The user will be notified if the specified map is unknown.
   *
   * @param mapName The name of the map to download; must not be {@code null}.
   * @throws IllegalStateException If this method is not called from the EDT.
   */
  public static void showDownloadMapsWindowAndDownload(final String mapName) {
    checkState(SwingUtilities.isEventDispatchThread());
    checkNotNull(mapName);

    showDownloadMapsWindowAndDownload(List.of(mapName));
  }

  /**
   * Shows the Download Maps window and immediately begins downloading the specified maps in the
   * background.
   *
   * <p>The user will be notified if any of the specified maps are unknown.
   *
   * @param mapNamesToDownload The collection containing the names of the maps to download; must not
   *     be {@code null}.
   * @throws IllegalStateException If this method is not called from the EDT.
   */
  public static void showDownloadMapsWindowAndDownload(
      final Collection<String> mapNamesToDownload) {
    checkState(SwingUtilities.isEventDispatchThread());
    checkNotNull(mapNamesToDownload);

    SINGLETON_MANAGER.showAndDownload(mapNamesToDownload);
  }

  private static final class SingletonManager {
    private enum State {
      UNINITIALIZED,
      INITIALIZING,
      INITIALIZED
    }

    private State state;
    private DownloadMapsWindow window;

    SingletonManager() {
      uninitialize();
    }

    private void uninitialize() {
      assert SwingUtilities.isEventDispatchThread();

      state = State.UNINITIALIZED;
      window = null;
    }

    void showAndDownload(final Collection<String> mapNamesToDownload) {
      assert SwingUtilities.isEventDispatchThread();

      switch (state) {
        case UNINITIALIZED:
          initialize(mapNamesToDownload);
          break;

        case INITIALIZING:
          logMapDownloadRequestIgnored(mapNamesToDownload);
          // do nothing; window will be shown when initialization is complete
          break;

        case INITIALIZED:
          logMapDownloadRequestIgnored(mapNamesToDownload);
          show();
          break;

        default:
          throw new AssertionError("unknown state");
      }
    }

    private void initialize(final Collection<String> mapNamesToDownload) {
      assert SwingUtilities.isEventDispatchThread();
      assert state == State.UNINITIALIZED;

      Interruptibles.awaitResult(SingletonManager::getMapDownloadListInBackground)
          .result
          .ifPresent(
              downloads -> {
                state = State.INITIALIZING;
                createAndShow(mapNamesToDownload, downloads);
              });
    }

    private static List<DownloadFileDescription> getMapDownloadListInBackground()
        throws InterruptedException {
      return BackgroundTaskRunner.runInBackgroundAndReturn(
          "Downloading list of available maps...", MapListingFetcher::getMapDownloadList);
    }

    private void createAndShow(
        final Collection<String> mapNamesToDownload,
        final List<DownloadFileDescription> availableDownloads) {
      assert SwingUtilities.isEventDispatchThread();
      assert state == State.INITIALIZING;
      assert window == null;

      window = new DownloadMapsWindow(mapNamesToDownload, availableDownloads);
      SwingComponents.addWindowClosedListener(window, this::uninitialize);
      LookAndFeelSwingFrameListener.register(window);
      state = State.INITIALIZED;

      show();
    }

    private void show() {
      assert SwingUtilities.isEventDispatchThread();
      assert state == State.INITIALIZED;
      assert window != null;

      window.setVisible(true);
      window.requestFocus();
      window.toFront();
    }
  }

  private static void logMapDownloadRequestIgnored(final Collection<String> mapNames) {
    if (!mapNames.isEmpty()) {
      log.info(
          "ignoring request to download maps because window initialization has already started");
    }
  }

  private static String formatIgnoredPendingMapsMessage(final Collection<String> unknownMapNames) {
    final StringBuilder sb = new StringBuilder();
    sb.append("<html>");
    sb.append("Some maps were not downloaded.<br>");

    if (!unknownMapNames.isEmpty()) {
      sb.append("<br>");
      sb.append("Could not find the following map(s):<br>");
      sb.append("<ul>");
      for (final String mapName : unknownMapNames) {
        sb.append("<li>").append(mapName).append("</li>");
      }
      sb.append("</ul>");
    }

    sb.append("</html>");
    return sb.toString();
  }

  private static Optional<DownloadFileDescription> findMap(
      final String mapName, final List<DownloadFileDescription> games) {

    final String normalizedName = normalizeName(mapName);
    for (final DownloadFileDescription download : games) {
      if (download.getMapName().equalsIgnoreCase(mapName)
          || normalizedName.equals(normalizeName(download.getMapName()))) {
        return Optional.of(download);
      }
    }
    return Optional.empty();
  }

  private static String normalizeName(final String mapName) {
    return mapName.replace(' ', '_').toLowerCase();
  }

  private JTabbedPane newAvailableInstalledTabbedPanel(
      final List<DownloadFileDescription> downloads,
      final Set<DownloadFileDescription> pendingDownloads) {
    final AvailableMapsListing mapList = new AvailableMapsListing(downloads);

    final JTabbedPane tabbedPane = new JTabbedPane();

    final List<DownloadFileDescription> outOfDateDownloads =
        mapList.getOutOfDateExcluding(pendingDownloads);
    // For the UX, always show an available maps tab, even if it is empty
    final JPanel available =
        newMapSelectionPanel(mapList.getAvailableExcluding(pendingDownloads), MapAction.INSTALL);
    tabbedPane.addTab("Available", available);

    if (!outOfDateDownloads.isEmpty()) {
      final JPanel outOfDate = newMapSelectionPanel(outOfDateDownloads, MapAction.UPDATE);
      tabbedPane.addTab("Update", outOfDate);
    }

    if (!mapList.getInstalled().isEmpty()) {
      final JPanel installed = newMapSelectionPanel(mapList.getInstalled(), MapAction.REMOVE);
      tabbedPane.addTab("Installed", installed);
    }
    return tabbedPane;
  }

  private JPanel newMapSelectionPanel(
      final List<DownloadFileDescription> unsortedMaps, final MapAction action) {
    final JPanel main = new JPanelBuilder().border(30).borderLayout().build();
    final JEditorPane descriptionPane = SwingComponents.newHtmlJEditorPane();
    main.add(SwingComponents.newJScrollPane(descriptionPane), BorderLayout.CENTER);

    final JLabel mapSizeLabel = new JLabel(" ");

    if (!unsortedMaps.isEmpty()) {
      final MapDownloadSwingTable mapDownloadSwingTable = new MapDownloadSwingTable(unsortedMaps);
      final JTable gamesList = mapDownloadSwingTable.getSwingComponent();
      mapDownloadSwingTable.addMapSelectionListener(
          mapSelections ->
              newDescriptionPanelUpdatingSelectionListener(
                  mapSelections.get(0), descriptionPane, unsortedMaps, mapSizeLabel));

      descriptionPane.setText(unsortedMaps.get(0).toHtmlString());
      descriptionPane.scrollRectToVisible(new Rectangle(0, 0, 0, 0));

      main.add(SwingComponents.newJScrollPane(gamesList), BorderLayout.WEST);
      final JPanel southPanel =
          new JPanelBuilder()
              .gridLayout(2, 1)
              .add(mapSizeLabel)
              .add(
                  newButtonsPanel(
                      action,
                      mapDownloadSwingTable::getSelectedMapNames,
                      unsortedMaps,
                      mapDownloadSwingTable::removeMapRow))
              .build();
      main.add(southPanel, BorderLayout.SOUTH);
    }

    return main;
  }

  private static void newDescriptionPanelUpdatingSelectionListener(
      final String mapName,
      final JEditorPane descriptionPanel,
      final List<DownloadFileDescription> maps,
      final JLabel mapSizeLabelToUpdate) {

    // find the map description by map name and update the map download detail panel
    maps.stream()
        .filter(mapDescription -> mapDescription.getMapName().equals(mapName))
        .findAny()
        .ifPresent(
            map -> {
              final String text = map.toHtmlString();
              descriptionPanel.setText(text);
              descriptionPanel.scrollRectToVisible(new Rectangle(0, 0, 0, 0));
              updateMapUrlAndSizeLabel(map, mapSizeLabelToUpdate);
            });
  }

  private static void updateMapUrlAndSizeLabel(
      final DownloadFileDescription map, final JLabel mapSizeLabel) {
    mapSizeLabel.setText(" ");
    ThreadRunner.runInNewThread(
        () -> {
          final String labelText = newLabelText(map);
          SwingUtilities.invokeLater(() -> mapSizeLabel.setText(labelText));
        });
  }

  private static String newLabelText(final DownloadFileDescription map) {
    final String doubleSpace = "&nbsp;&nbsp;";

    final StringBuilder sb = new StringBuilder();
    sb.append("<html>")
        .append(map.getMapName())
        .append(doubleSpace)
        .append(" v")
        .append(map.getVersion());

    if (map.getInstallLocation().isEmpty()) {
      final String mapUrl = map.getUrl();
      if (mapUrl != null) {
        DownloadConfiguration.downloadLengthReader()
            .getDownloadLength(mapUrl)
            .ifPresent(
                downloadSize ->
                    sb.append(doubleSpace)
                        .append(" (")
                        .append(newSizeLabel(downloadSize))
                        .append(")"));
      }
    } else {
      sb.append(doubleSpace)
          .append(" (")
          .append(newSizeLabel(map.getInstallLocation().get().length()))
          .append(")");
      sb.append("<br>").append(map.getInstallLocation().get().getAbsolutePath());
    }
    sb.append("<br>");
    sb.append("</html>");

    return sb.toString();
  }

  private static String newSizeLabel(final long bytes) {
    final long kiloBytes = (bytes / 1024);
    final long megaBytes = kiloBytes / 1024;
    final long kbDigits = ((kiloBytes % 1000) / 100);
    return megaBytes + "." + kbDigits + " MB";
  }

  private JPanel newButtonsPanel(
      final MapAction action,
      final Supplier<List<String>> mapSelection,
      final List<DownloadFileDescription> maps,
      final Consumer<String> tableRemoveAction) {

    return new JPanelBuilder()
        .border(20)
        .gridLayout(1, 5)
        .add(buildMapActionButton(action, mapSelection, maps, tableRemoveAction))
        .add(Box.createGlue())
        .add(
            new JButtonBuilder()
                .title("Close")
                .toolTip(
                    "Click this button to close the map download window and "
                        + "cancel any in-progress downloads.")
                .actionListener(
                    () -> {
                      setVisible(false);
                      dispose();
                    })
                .build())
        .build();
  }

  private JButton buildMapActionButton(
      final MapAction action,
      final Supplier<List<String>> mapSelection,
      final List<DownloadFileDescription> maps,
      final Consumer<String> tableRemoveAction) {
    final JButton actionButton;

    if (action == MapAction.REMOVE) {
      actionButton =
          new JButtonBuilder()
              .title("Remove")
              .toolTip(
                  "Click this button to remove the maps selected above from your computer. "
                      + MULTIPLE_SELECT_MSG)
              .actionListener(removeAction(mapSelection, maps, tableRemoveAction))
              .build();
    } else {
      actionButton =
          new JButtonBuilder()
              .title((action == MapAction.INSTALL) ? "Install" : "Update")
              .toolTip(
                  "Click this button to download and install the maps selected above. "
                      + MULTIPLE_SELECT_MSG)
              .actionListener(installAction(mapSelection, maps, tableRemoveAction))
              .build();
    }
    return actionButton;
  }

  private static Runnable removeAction(
      final Supplier<List<String>> mapSelection,
      final List<DownloadFileDescription> maps,
      final Consumer<String> tableRemoveAction) {
    return () -> {
      final List<String> selectedValues = mapSelection.get();
      final List<DownloadFileDescription> selectedMaps =
          maps.stream()
              .filter(map -> selectedValues.contains(map.getMapName()))
              .collect(Collectors.toList());
      if (!selectedMaps.isEmpty()) {
        FileSystemAccessStrategy.remove(selectedMaps, tableRemoveAction);
      }
    };
  }

  private Runnable installAction(
      final Supplier<List<String>> mapSelection,
      final List<DownloadFileDescription> maps,
      final Consumer<String> tableRemoveAction) {
    return () -> {
      final List<String> selectedValues = mapSelection.get();
      final List<DownloadFileDescription> downloadList =
          maps.stream()
              .filter(map -> selectedValues.contains(map.getMapName()))
              .collect(Collectors.toList());

      if (!downloadList.isEmpty()) {
        progressPanel.download(downloadList);
      }

      downloadList.stream().map(DownloadFileDescription::getMapName).forEach(tableRemoveAction);
    };
  }
}
