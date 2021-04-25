package games.strategy.net;

import games.strategy.engine.framework.startup.login.ClientLogin;
import games.strategy.engine.framework.startup.mc.ClientModel;
import java.io.IOException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.triplea.domain.data.SystemIdLoader;

/** Factory class for implementations of {@link IClientMessenger}. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class ClientMessengerFactory {

  /** Creates a client messenger suitable for connecting to a hosted game server. */
  public static IClientMessenger newClientMessenger(
      final ClientModel.ClientProps props,
      final IObjectStreamFactory objectStreamFactory,
      final ClientLogin clientLogin)
      throws IOException {
    log.info(String.format("Connecting to bot: %s:%s", props.getHost(), props.getPort()));
    return new ClientMessenger(
        props.getHost(),
        props.getPort(),
        props.getName(),
        SystemIdLoader.load(),
        objectStreamFactory,
        clientLogin);
  }
}
