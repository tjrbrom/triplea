package org.triplea.maps.indexing;

import io.dropwizard.lifecycle.Managed;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.function.BiPredicate;
import lombok.experimental.UtilityClass;
import org.jdbi.v3.core.Jdbi;
import org.triplea.http.client.github.GithubApiClient;
import org.triplea.http.client.github.MapRepoListing;
import org.triplea.maps.MapsModuleConfig;
import org.triplea.maps.indexing.tasks.CommitDateFetcher;
import org.triplea.maps.indexing.tasks.DownloadSizeFetcher;
import org.triplea.maps.indexing.tasks.MapDescriptionReader;
import org.triplea.maps.indexing.tasks.MapNameReader;
import org.triplea.maps.indexing.tasks.SkipMapIndexingCheck;
import org.triplea.server.lib.scheduled.tasks.ScheduledTask;

@UtilityClass
public class MapsIndexingObjectFactory {
  /**
   * Factory method to create indexing task on a schedule. This does not start indexing, the
   * 'start()' method must be called for map indexing to begin.
   */
  public static Managed buildMapsIndexingSchedule(
      final MapsModuleConfig configuration, final Jdbi jdbi) {

    return ScheduledTask.builder()
        .taskName("Map-Indexing")
        .delay(Duration.ofSeconds(10))
        .period(Duration.ofMinutes(configuration.getMapIndexingPeriodMinutes()))
        .task(mapIndexingTaskRunner(configuration, jdbi))
        .build();
  }

  MapIndexingTaskRunner mapIndexingTaskRunner(
      final MapsModuleConfig configuration, final Jdbi jdbi) {
    final var githubApiClient =
        githubApiClient(configuration.getGithubWebServiceUrl(), configuration.getGithubApiToken());

    return MapIndexingTaskRunner.builder()
        .githubOrgName(configuration.getGithubMapsOrgName())
        .githubApiClient(githubApiClient)
        .mapIndexer(
            mapIndexingTask(
                configuration.getGithubMapsOrgName(), githubApiClient, skipMapIndexingCheck(jdbi)))
        .mapIndexDao(jdbi.onDemand(MapIndexDao.class))
        .indexingTaskDelaySeconds(configuration.getIndexingTaskDelaySeconds())
        .build();
  }

  GithubApiClient githubApiClient(final String webserviceUrl, final String apiToken) {
    return GithubApiClient.builder().uri(URI.create(webserviceUrl)).authToken(apiToken).build();
  }

  MapIndexingTask mapIndexingTask(
      final String githubMapsOrgName,
      final GithubApiClient githubApiClient,
      final BiPredicate<MapRepoListing, Instant> skipMapIndexingCheck) {
    return MapIndexingTask.builder()
        .lastCommitDateFetcher(
            CommitDateFetcher.builder()
                .githubApiClient(githubApiClient)
                .githubOrgName(githubMapsOrgName)
                .build())
        .skipMapIndexingCheck(skipMapIndexingCheck)
        .mapNameReader(MapNameReader.builder().build())
        .mapDescriptionReader(new MapDescriptionReader())
        .downloadSizeFetcher(new DownloadSizeFetcher())
        .build();
  }

  BiPredicate<MapRepoListing, Instant> skipMapIndexingCheck(final Jdbi jdbi) {
    return new SkipMapIndexingCheck(jdbi.onDemand(MapIndexDao.class));
  }
}
