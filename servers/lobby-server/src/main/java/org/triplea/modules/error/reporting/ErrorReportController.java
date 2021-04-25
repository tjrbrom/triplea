package org.triplea.modules.error.reporting;

import com.google.common.base.Preconditions;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import lombok.Builder;
import org.jdbi.v3.core.Jdbi;
import org.triplea.http.HttpController;
import org.triplea.http.LobbyServerConfig;
import org.triplea.http.client.SystemIdHeader;
import org.triplea.http.client.error.report.CanUploadErrorReportResponse;
import org.triplea.http.client.error.report.CanUploadRequest;
import org.triplea.http.client.error.report.ErrorReportClient;
import org.triplea.http.client.error.report.ErrorReportRequest;
import org.triplea.http.client.error.report.ErrorReportResponse;
import org.triplea.http.client.github.GithubApiClient;

/** Http controller that binds the error upload endpoint with the error report upload handler. */
@Builder
public class ErrorReportController extends HttpController {
  @Nonnull private final Function<CreateIssueParams, ErrorReportResponse> errorReportIngestion;
  @Nonnull private final Function<CanUploadRequest, CanUploadErrorReportResponse> canReportModule;

  /** Factory method. */
  public static ErrorReportController build(
      final LobbyServerConfig configuration, final Jdbi jdbi) {
    final boolean isTest = configuration.getGithubApiToken().equals("test");

    final GithubApiClient githubApiClient =
        GithubApiClient.builder()
            .uri(LobbyServerConfig.GITHUB_WEB_SERVICE_API_URL)
            .authToken(configuration.getGithubApiToken())
            .isTest(isTest)
            .build();

    if (isTest) {
      Preconditions.checkState(!configuration.isProd());
    }

    return ErrorReportController.builder()
        .errorReportIngestion(
            CreateIssueStrategy.build(
                LobbyServerConfig.GITHUB_ORG, configuration.getGithubRepo(), githubApiClient, jdbi))
        .canReportModule(CanUploadErrorReportStrategy.build(jdbi))
        .build();
  }

  @POST
  @Path(ErrorReportClient.CAN_UPLOAD_ERROR_REPORT_PATH)
  public CanUploadErrorReportResponse canUploadErrorReport(
      final CanUploadRequest canUploadRequest) {
    if (canUploadRequest == null
        || canUploadRequest.getErrorTitle() == null
        || canUploadRequest.getGameVersion() == null) {
      throw new IllegalArgumentException("Missing request attributes title or game version");
    }

    return canReportModule.apply(canUploadRequest);
  }

  /**
   * Endpoint where users can submit an error report, the server will use an API token of a generic
   * user to in turn create a github issue using the data from the error report.
   */
  @POST
  @Path(ErrorReportClient.ERROR_REPORT_PATH)
  public ErrorReportResponse uploadErrorReport(
      @Context final HttpServletRequest request, final ErrorReportRequest errorReport) {

    if (errorReport == null
        || errorReport.getBody() == null
        || errorReport.getTitle() == null
        || errorReport.getGameVersion() == null) {
      throw new IllegalArgumentException("Missing attribute, body, title, or game version");
    }

    return errorReportIngestion.apply(
        CreateIssueParams.builder()
            .ip(request.getRemoteAddr())
            .systemId(request.getHeader(SystemIdHeader.SYSTEM_ID_HEADER))
            .errorReportRequest(errorReport)
            .build());
  }
}
