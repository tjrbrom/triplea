package games.strategy.engine.framework.map.download;

import com.google.common.collect.Maps;
import games.strategy.engine.ClientContext;
import games.strategy.engine.framework.map.file.system.loader.AvailableGamesFileSystemReader;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import org.triplea.swing.SwingComponents;
import org.triplea.swing.jpanel.JPanelBuilder;

/**
 * A small non-modal window that holds the progress bars for the current and pending map downloads.
 */
final class MapDownloadProgressPanel extends JPanel implements DownloadListener {

  private static final long serialVersionUID = -7288639737337542689L;

  private final DownloadCoordinator downloadCoordinator = ClientContext.downloadCoordinator();

  /*
   * Maintain grids that are placed east and west.
   * This gives us a minimal and uniform width for each column.
   */
  private final JPanel labelGrid = new JPanelBuilder().gridLayout(0, 1).build();
  private final JPanel progressGrid = new JPanelBuilder().gridLayout(0, 1).build();

  private final List<DownloadFileDescription> downloadList = new ArrayList<>();
  private final Map<DownloadFileDescription, JLabel> labels = Maps.newHashMap();
  private final Map<DownloadFileDescription, JProgressBar> progressBars = Maps.newHashMap();
  private final Map<DownloadFileDescription, MapDownloadProgressListener>
      mapDownloadProgressListeners = Maps.newHashMap();

  MapDownloadProgressPanel() {
    downloadCoordinator.addDownloadListener(this);
    addPendingDownloads();
  }

  private void addPendingDownloads() {
    downloadCoordinator.getPendingDownloads().stream()
        .map(DownloadFile::getDownload)
        .forEach(this::downloadStarted);
  }

  @Override
  public void removeNotify() {
    downloadCoordinator.removeDownloadListener(this);
    super.removeNotify();
  }

  void cancel() {
    downloadCoordinator.cancelDownloads();
  }

  void download(final Collection<DownloadFileDescription> newDownloads) {
    newDownloads.forEach(downloadCoordinator::accept);
  }

  @Override
  public void downloadStarted(final DownloadFileDescription download) {
    SwingUtilities.invokeLater(() -> getMapDownloadProgressListenerFor(download).downloadStarted());
  }

  private MapDownloadProgressListener addDownload(final DownloadFileDescription download) {
    assert SwingUtilities.isEventDispatchThread();

    // add new downloads to the head of the list, this will allow the user to see newly added items
    // directly,
    // rather than having to scroll down to see new items.
    downloadList.add(0, download);

    // space at the end of the label so the text does not end right at the progress bar
    labels.put(download, new JLabel(download.getMapName() + "  "));
    final JProgressBar progressBar = new JProgressBar();
    progressBars.put(download, progressBar);

    rebuildPanel();

    return new MapDownloadProgressListener(download, progressBar);
  }

  private void rebuildPanel() {
    assert SwingUtilities.isEventDispatchThread();

    final int itemCount = downloadList.size();
    this.removeAll();
    add(new JPanelBuilder().borderLayout().addWest(labelGrid).addEast(progressGrid).build());
    labelGrid.setLayout(new GridLayout(itemCount, 1));
    progressGrid.setLayout(new GridLayout(itemCount, 1));

    for (final DownloadFileDescription download : downloadList) {
      labelGrid.add(labels.get(download));
      progressGrid.add(progressBars.get(download));
    }

    SwingComponents.redraw(this);
  }

  @Override
  public void downloadUpdated(final DownloadFileDescription download, final long bytesReceived) {
    SwingUtilities.invokeLater(
        () -> getMapDownloadProgressListenerFor(download).downloadUpdated(bytesReceived));
  }

  @Override
  public void downloadComplete(final DownloadFileDescription download) {
    SwingUtilities.invokeLater(
        () -> getMapDownloadProgressListenerFor(download).downloadCompleted());
    AvailableGamesFileSystemReader.addNewMapToCache(download.getInstallLocation());
  }

  private MapDownloadProgressListener getMapDownloadProgressListenerFor(
      final DownloadFileDescription download) {
    assert SwingUtilities.isEventDispatchThread();

    return mapDownloadProgressListeners.computeIfAbsent(download, this::addDownload);
  }
}
