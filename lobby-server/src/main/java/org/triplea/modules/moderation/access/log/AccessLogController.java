package org.triplea.modules.moderation.access.log;

import com.google.common.base.Preconditions;
import javax.annotation.Nonnull;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import lombok.Builder;
import org.jdbi.v3.core.Jdbi;
import org.triplea.db.dao.user.role.UserRole;
import org.triplea.http.HttpController;
import org.triplea.http.client.lobby.moderator.toolbox.log.AccessLogRequest;
import org.triplea.http.client.lobby.moderator.toolbox.log.ToolboxAccessLogClient;

/** Controller to query the access log table, for us by moderators. */
@Builder
@RolesAllowed(UserRole.MODERATOR)
public class AccessLogController extends HttpController {
  @Nonnull private final AccessLogService accessLogService;

  public static AccessLogController build(final Jdbi jdbi) {
    return AccessLogController.builder() //
        .accessLogService(AccessLogService.build(jdbi))
        .build();
  }

  @POST
  @Path(ToolboxAccessLogClient.FETCH_ACCESS_LOG_PATH)
  public Response fetchAccessLog(final AccessLogRequest accessLogRequest) {
    Preconditions.checkNotNull(accessLogRequest);
    Preconditions.checkNotNull(accessLogRequest.getAccessLogSearchRequest());
    Preconditions.checkNotNull(accessLogRequest.getPagingParams());
    return Response.ok().entity(accessLogService.fetchAccessLog(accessLogRequest)).build();
  }
}
