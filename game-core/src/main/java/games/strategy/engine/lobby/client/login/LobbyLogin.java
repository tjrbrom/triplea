package games.strategy.engine.lobby.client.login;

import com.google.common.base.Strings;
import feign.FeignException;
import games.strategy.engine.framework.ui.background.BackgroundTaskRunner;
import java.io.IOException;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.swing.JFrame;
import org.triplea.domain.data.ApiKey;
import org.triplea.domain.data.UserName;
import org.triplea.http.client.HttpInteractionException;
import org.triplea.http.client.SystemIdHeader;
import org.triplea.http.client.forgot.password.ForgotPasswordClient;
import org.triplea.http.client.forgot.password.ForgotPasswordRequest;
import org.triplea.http.client.lobby.login.CreateAccountResponse;
import org.triplea.http.client.lobby.login.LobbyLoginClient;
import org.triplea.http.client.lobby.login.LobbyLoginResponse;
import org.triplea.live.servers.ServerProperties;
import org.triplea.swing.DialogBuilder;
import org.triplea.swing.SwingComponents;

/**
 * The client side of the lobby authentication protocol.
 *
 * <p>The client is responsible for sending the initial authentication request to the server
 * containing the user's name. The server will send back an authentication challenge. The client
 * then sends a response to the challenge to prove the user knows the correct password.
 */
public class LobbyLogin {
  private static final String CONNECTING_TO_LOBBY = "Connecting to lobby...";
  private final JFrame parentWindow;

  private final ServerProperties serverProperties;
  private final LobbyLoginClient lobbyLoginClient;

  public LobbyLogin(final JFrame parentWindow, final ServerProperties serverProperties) {
    this.parentWindow = parentWindow;
    this.serverProperties = serverProperties;
    lobbyLoginClient = LobbyLoginClient.newClient(serverProperties.getUri());
  }

  /**
   * Executes a login sequence prompting the user for their lobby username+password and sends it to
   * server. If successful the user is presented with the lobby frame. Failure cases are handled and
   * user is presented with another try or they can abort. In the abort case this method is a no-op.
   */
  public Optional<LoginResult> promptLogin(final LoginMode loginMode) {
    final Optional<LoginResult> lobbyClient = loginToServer(loginMode);

    lobbyClient.ifPresent(
        loginResult -> {
          if (loginResult.isPasswordChangeRequired()) {
            try {
              final boolean passwordChanged =
                  ChangePasswordPanel.doPasswordChange(
                      parentWindow,
                      serverProperties.getUri(),
                      loginResult.getApiKey(),
                      ChangePasswordPanel.AllowCancelMode.DO_NOT_SHOW_CANCEL_BUTTON);

              if (passwordChanged) {
                DialogBuilder.builder()
                    .parent(parentWindow)
                    .title("Success")
                    .infoMessage("Password successfully updated!")
                    .showDialog();
              } else {
                notifyTempPasswordInvalid(parentWindow, null);
              }

            } catch (final HttpInteractionException e) {
              notifyTempPasswordInvalid(parentWindow, e);
            }
          }
        });
    return lobbyClient;
  }

  private static void notifyTempPasswordInvalid(
      final JFrame parentWindow, final @Nullable Exception exception) {
    DialogBuilder.builder()
        .parent(parentWindow)
        .title("Password Not Updated")
        .errorMessage(
            "Password not updated, your temporary password is expired.\n"
                + "Use the account menu to reset your password."
                + Optional.ofNullable(exception).map(e -> "\nError: " + e.getMessage()).orElse(""))
        .showDialog();
  }

  private Optional<LoginResult> login(final LoginPanel panel, final LoginMode loginMode) {
    try {

      final LobbyLoginResponse loginResponse =
          BackgroundTaskRunner.runInBackgroundAndReturn(
              CONNECTING_TO_LOBBY,
              () -> lobbyLoginClient.login(panel.getUserName(), panel.getPassword()));

      if (loginResponse.getFailReason() == null) {
        return Optional.of(
            LoginResult.builder()
                .anonymousLogin(Strings.nullToEmpty(panel.getPassword()).isEmpty())
                .username(UserName.of(panel.getUserName()))
                .apiKey(ApiKey.of(loginResponse.getApiKey()))
                .moderator(loginResponse.isModerator())
                .passwordChangeRequired(loginResponse.isPasswordChangeRequired())
                .build());
      } else {
        showError("Login Failed", loginResponse.getFailReason());
        return loginToServer(loginMode);
      }
    } catch (final FeignException e) {
      showError("Could Not Connect To Lobby", "Error: " + e.getMessage());
      return Optional.empty();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }

  private void showError(final String title, final String message) {
    // We use 'null' parentWindow in case there is an async failure connecting to the lobby
    // server. In the async case, we close the parent window while still connecting, the close
    // of the parent window will close the child dialog error message as well.
    SwingComponents.showError(null, title, message);
  }

  private Optional<LoginResult> loginToServer(final LoginMode loginMode) {
    final LoginPanel loginPanel = new LoginPanel(loginMode);
    final LoginPanel.ReturnValue returnValue = loginPanel.show(parentWindow);
    switch (returnValue) {
      case LOGON:
        return login(loginPanel, loginMode);
      case CANCEL:
        return Optional.empty();
      case CREATE_ACCOUNT:
        return createAccount();
      case FORGOT_PASSWORD:
        forgotPassword();
        return Optional.empty();
      default:
        throw new AssertionError("unknown login panel return value: " + returnValue);
    }
  }

  private Optional<LoginResult> createAccount() {
    final CreateAccountPanel createAccountPanel = new CreateAccountPanel();
    final CreateAccountPanel.ReturnValue returnValue = createAccountPanel.show(parentWindow);
    switch (returnValue) {
      case OK:
        return createAccount(createAccountPanel)
            .map(
                lobbyLoginResponse ->
                    LoginResult.builder()
                        .username(UserName.of(createAccountPanel.getUsername()))
                        .apiKey(ApiKey.of(lobbyLoginResponse.getApiKey()))
                        .passwordChangeRequired(lobbyLoginResponse.isPasswordChangeRequired())
                        .build());
      case CANCEL:
        return Optional.empty();
      default:
        throw new AssertionError("unknown create account panel return value: " + returnValue);
    }
  }

  private Optional<LobbyLoginResponse> createAccount(final CreateAccountPanel panel) {
    try {
      final CreateAccountResponse createAccountResponse = sendCreateAccountRequest(panel);
      if (!createAccountResponse.isSuccess()) {
        showError("Account Creation Failed", createAccountResponse.getErrorMessage());
        return Optional.empty();
      }

      final LobbyLoginResponse loginResponse = sendLoginRequest(panel);
      if (loginResponse.getFailReason() != null) {
        throw new LoginFailure(loginResponse.getFailReason());
      }
      return Optional.of(loginResponse);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }

  private CreateAccountResponse sendCreateAccountRequest(final CreateAccountPanel panel)
      throws InterruptedException {
    return BackgroundTaskRunner.runInBackgroundAndReturn(
        "Creating account...",
        () ->
            lobbyLoginClient.createAccount(
                panel.getUsername(), panel.getEmail(), panel.getPassword()));
  }

  private LobbyLoginResponse sendLoginRequest(final CreateAccountPanel panel)
      throws InterruptedException {
    return BackgroundTaskRunner.runInBackgroundAndReturn(
        CONNECTING_TO_LOBBY,
        () -> lobbyLoginClient.login(panel.getUsername(), panel.getPassword()));
  }

  private static class LoginFailure extends RuntimeException {
    private static final long serialVersionUID = -8525998378819156909L;

    LoginFailure(final String failReason) {
      super("Unexpected error logging in to server: " + failReason);
    }
  }

  private void forgotPassword() {
    final ForgotPasswordPanel forgotPasswordPanel = ForgotPasswordPanel.newForgotPasswordPanel();
    final ForgotPasswordPanel.ReturnValue returnValue = forgotPasswordPanel.show(parentWindow);
    switch (returnValue) {
      case OK:
        forgotPassword(forgotPasswordPanel);
        return;
      case CANCEL:
        return;
      default:
        throw new AssertionError("unknown forgot password panel return value: " + returnValue);
    }
  }

  private void forgotPassword(final ForgotPasswordPanel panel) {
    try {
      final String response =
          BackgroundTaskRunner.runInBackgroundAndReturn(
                  "Sending forgot password request...",
                  () ->
                      ForgotPasswordClient.newClient(serverProperties.getUri())
                          .sendForgotPasswordRequest(
                              SystemIdHeader.headers(),
                              ForgotPasswordRequest.builder()
                                  .username(panel.getUserName())
                                  .email(panel.getEmail())
                                  .build()),
                  IOException.class)
              .getResponseMessage();
      DialogBuilder.builder()
          .parent(parentWindow)
          .title("Server Response")
          .infoMessage(response)
          .showDialog();
    } catch (final IOException e) {
      showError("Error", "Failed to generate a temporary password: " + e.getMessage());
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
