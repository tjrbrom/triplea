package games.strategy.engine.framework.map.download;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Class that accepts and queues download requests. Download requests are started in background
 * thread, this class ensures N are in progress until all are done.
 */
public final class DownloadCoordinator {
  private static final int MAX_CONCURRENT_DOWNLOADS = 3;

  private final Object lock = new Object();

  private final Queue<DownloadFile> pendingDownloads = new ArrayDeque<>();
  private final Set<DownloadFile> activeDownloads = new HashSet<>();
  private final Set<DownloadFile> terminatedDownloads = new HashSet<>();

  private final List<DownloadListener> downloadListeners = new CopyOnWriteArrayList<>();

  void addDownloadListener(final DownloadListener downloadListener) {
    downloadListeners.add(downloadListener);
  }

  void removeDownloadListener(final DownloadListener downloadListener) {
    downloadListeners.remove(downloadListener);
  }

  void accept(final DownloadFileDescription download) {
    synchronized (lock) {
      if (isNewDownload(download)) {
        pendingDownloads.add(new DownloadFile(download, new Listener()));
        updateQueue();
      }
    }
  }

  private boolean isNewDownload(final DownloadFileDescription download) {
    assert Thread.holdsLock(lock);

    return !containsDownload(pendingDownloads, download)
        && !containsDownload(activeDownloads, download);
  }

  private static boolean containsDownload(
      final Collection<DownloadFile> downloadFiles, final DownloadFileDescription download) {
    return downloadFiles.stream().map(DownloadFile::getDownload).anyMatch(download::equals);
  }

  private void updateQueue() {
    assert Thread.holdsLock(lock);

    if (activeDownloads.size() < MAX_CONCURRENT_DOWNLOADS && !pendingDownloads.isEmpty()) {
      final DownloadFile downloadFile = pendingDownloads.remove();
      downloadFile.startAsyncDownload();
      activeDownloads.add(downloadFile);
    }
  }

  void cancelDownloads() {
    synchronized (lock) {
      for (final DownloadFile download : pendingDownloads) {
        download.cancelDownload(); // CAUTION: calling out to external code while holding lock
        terminatedDownloads.add(download);
      }
      pendingDownloads.clear();

      for (final DownloadFile download : activeDownloads) {
        download.cancelDownload(); // CAUTION: calling out to external code while holding lock
        terminatedDownloads.add(download);
      }
      activeDownloads.clear();
    }
  }

  Set<DownloadFile> pollTerminatedDownloads() {
    synchronized (lock) {
      final Set<DownloadFile> set = new HashSet<>(terminatedDownloads);
      terminatedDownloads.clear();
      return set;
    }
  }

  Set<DownloadFile> getPendingDownloads() {
    synchronized (lock) {
      return new HashSet<>(pendingDownloads);
    }
  }

  Set<DownloadFile> getDownloads() {
    synchronized (lock) {
      final Set<DownloadFile> result = getPendingDownloads();
      result.addAll(activeDownloads);
      result.addAll(pollTerminatedDownloads());
      return result;
    }
  }

  private final class Listener implements DownloadListener {
    @Override
    public void downloadStarted(final DownloadFileDescription download) {
      downloadListeners.forEach(it -> it.downloadStarted(download));
    }

    @Override
    public void downloadUpdated(final DownloadFileDescription download, final long bytesReceived) {
      downloadListeners.forEach(it -> it.downloadUpdated(download, bytesReceived));
    }

    @Override
    public void downloadComplete(final DownloadFileDescription download) {
      downloadListeners.forEach(it -> it.downloadComplete(download));

      synchronized (lock) {
        final Iterator<DownloadFile> iterator = activeDownloads.iterator();
        while (iterator.hasNext()) {
          final DownloadFile downloadFile = iterator.next();
          if (downloadFile.isDone()) {
            iterator.remove();
            terminatedDownloads.add(downloadFile);
            break;
          }
        }
        updateQueue();
      }
    }
  }
}
