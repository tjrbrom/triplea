package org.triplea.modules.user.account.login;

import java.util.function.Consumer;
import javax.annotation.Nonnull;
import lombok.Builder;
import org.jdbi.v3.core.Jdbi;
import org.triplea.db.dao.access.log.AccessLogDao;
import org.triplea.java.Postconditions;

@Builder
class AccessLogUpdater implements Consumer<LoginRecord> {

  @Nonnull private final AccessLogDao accessLogDao;

  public static AccessLogUpdater build(final Jdbi jdbi) {
    return AccessLogUpdater.builder() //
        .accessLogDao(jdbi.onDemand(AccessLogDao.class))
        .build();
  }

  @Override
  public void accept(final LoginRecord loginRecord) {
    final int updateCount =
        accessLogDao.insertUserAccessRecord(
            loginRecord.getUserName().getValue(),
            loginRecord.getIp(),
            loginRecord.getSystemId().getValue());

    Postconditions.assertState(updateCount == 1);
  }
}
