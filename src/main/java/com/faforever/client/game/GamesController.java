package com.faforever.client.game;

import com.faforever.client.chat.PlayerInfoBean;
import com.faforever.client.fx.WindowController;
import com.faforever.client.i18n.I18n;
import com.faforever.client.map.MapDetailController;
import com.faforever.client.map.MapService;
import com.faforever.client.notification.Action;
import com.faforever.client.notification.DismissAction;
import com.faforever.client.notification.ImmediateNotification;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.ReportAction;
import com.faforever.client.notification.Severity;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.domain.GameState;
import com.faforever.client.reporting.ReportingService;
import com.faforever.client.util.RatingUtil;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static com.faforever.client.fx.WindowController.WindowButtonType.CLOSE;
import static com.faforever.client.notification.Severity.ERROR;
import static java.util.Arrays.asList;
import static javafx.beans.binding.Bindings.createObjectBinding;
import static javafx.beans.binding.Bindings.createStringBinding;

public class GamesController {

  private static final Predicate<GameInfoBean> OPEN_GAMES_PREDICATE = gameInfoBean -> gameInfoBean.getStatus() == GameState.OPEN;
  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @FXML
  ToggleButton tableButton;
  @FXML
  ToggleButton tilesButton;
  @FXML
  ToggleGroup viewToggleGroup;
  @FXML
  VBox teamListPane;
  @FXML
  Label mapLabel;
  @FXML
  Button createGameButton;
  @FXML
  Pane gameViewContainer;
  @FXML
  Node gamesRoot;
  @FXML
  ImageView mapImageView;
  @FXML
  Label gameTitleLabel;
  @FXML
  Label numberOfPlayersLabel;
  @FXML
  Label hostLabel;
  @FXML
  Label gameTypeLabel;
  @FXML
  ScrollPane gameDetailPane;

  @Resource
  ApplicationContext applicationContext;
  @Resource
  I18n i18n;
  @Resource
  PlayerService playerService;
  @Resource
  GameService gameService;
  @Resource
  MapService mapService;
  @Resource
  CreateGameController createGameController;
  @Resource
  EnterPasswordController enterPasswordController;
  @Resource
  PreferencesService preferencesService;
  @Resource
  NotificationService notificationService;
  @Resource
  ReportingService reportingService;

  private Popup createGamePopup;
  private Popup passwordPopup;
  private FilteredList<GameInfoBean> filteredItems;
  private Stage mapDetailPopup;

  private GameInfoBean currentGameInfoBean;

  @FXML
  void initialize() {
    gameDetailPane.managedProperty().bind(gameDetailPane.visibleProperty());
    gameDetailPane.setVisible(false);
  }

  @PostConstruct
  void postConstruct() {
    passwordPopup = new Popup();
    passwordPopup.setAutoHide(true);
    passwordPopup.setAnchorLocation(PopupWindow.AnchorLocation.CONTENT_TOP_LEFT);
    passwordPopup.getContent().setAll(enterPasswordController.getRoot());

    createGamePopup = new Popup();
    createGamePopup.setAutoFix(false);
    createGamePopup.setAutoHide(true);
    createGamePopup.setAnchorLocation(PopupWindow.AnchorLocation.CONTENT_TOP_LEFT);
    createGamePopup.getContent().setAll(createGameController.getRoot());

    enterPasswordController.setOnPasswordEnteredListener(this::doJoinGame);

    ObservableList<GameInfoBean> gameInfoBeans = gameService.getGameInfoBeans();

    filteredItems = new FilteredList<>(gameInfoBeans);
    filteredItems.setPredicate(OPEN_GAMES_PREDICATE);

    if (tilesButton.getId().equals(preferencesService.getPreferences().getGamesViewMode())) {
      viewToggleGroup.selectToggle(tilesButton);
      tilesButton.getOnAction().handle(null);
    } else {
      viewToggleGroup.selectToggle(tableButton);
      tableButton.getOnAction().handle(null);
    }
    viewToggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
      preferencesService.getPreferences().setGamesViewMode(((ToggleButton) newValue).getId());
      preferencesService.storeInBackground();
    });
  }

  public void setSelectedGame(GameInfoBean gameInfoBean) {
    if (gameInfoBean == null) {
      gameDetailPane.setVisible(false);
      return;
    }

    gameDetailPane.setVisible(true);
    currentGameInfoBean = gameInfoBean;
    gameTitleLabel.setText(gameInfoBean.getTitle());
    mapImageView.setImage(mapService.loadLargePreview(gameInfoBean.getMapTechnicalName()));

    gameTitleLabel.textProperty().bind(gameInfoBean.mapTechnicalNameProperty());

    mapImageView.imageProperty().bind(createObjectBinding(
        () -> mapService.loadLargePreview(gameInfoBean.getMapTechnicalName()),
        gameInfoBean.mapTechnicalNameProperty()
    ));

    numberOfPlayersLabel.textProperty().bind(createStringBinding(
        () -> i18n.get("game.detail.players.format", gameInfoBean.getNumPlayers(), gameInfoBean.getMaxPlayers()),
        gameInfoBean.numPlayersProperty(),
        gameInfoBean.maxPlayersProperty()
    ));

    hostLabel.textProperty().bind(gameInfoBean.hostProperty());
    mapLabel.textProperty().bind(gameInfoBean.mapTechnicalNameProperty());

    gameTypeLabel.textProperty().bind(createStringBinding(() -> {
      GameTypeBean gameType = gameService.getGameTypeByString(gameInfoBean.getFeaturedMod());
      String fullName = gameType != null ? gameType.getFullName() : null;
      return StringUtils.defaultString(fullName);
    }, gameInfoBean.featuredModProperty()));

    createTeams(gameInfoBean.getTeams());
  }

  private void createTeams(ObservableMap<? extends String, ? extends List<String>> playersByTeamNumber) {
    teamListPane.getChildren().clear();
    for (Map.Entry<? extends String, ? extends List<String>> entry : playersByTeamNumber.entrySet()) {
      TeamCardController teamCardController = applicationContext.getBean(TeamCardController.class);
      teamCardController.setPlayersInTeam(entry.getKey(), entry.getValue());
      teamListPane.getChildren().add(teamCardController.getRoot());
    }
  }

  @FXML
  void onShowPrivateGames(ActionEvent actionEvent) {
    CheckBox checkBox = (CheckBox) actionEvent.getSource();
    boolean selected = checkBox.isSelected();
    if (selected) {
      filteredItems.setPredicate(OPEN_GAMES_PREDICATE);
    } else {
      filteredItems.setPredicate(OPEN_GAMES_PREDICATE.and(gameInfoBean -> !gameInfoBean.getPasswordProtected()));
    }
  }

  public void onJoinGame(GameInfoBean gameInfoBean, String password, double screenX, double screenY) {
    PlayerInfoBean currentPlayer = playerService.getCurrentPlayer();
    int playerRating = RatingUtil.getGlobalRating(currentPlayer);

    if ((playerRating < gameInfoBean.getMinRating() || playerRating > gameInfoBean.getMaxRating())) {
      showRatingOutOfBoundsConfirmation(playerRating, gameInfoBean, screenX, screenY);
    } else {
      doJoinGame(gameInfoBean, password, screenX, screenY);
    }
  }

  private void showRatingOutOfBoundsConfirmation(int playerRating, GameInfoBean gameInfoBean, double screenX, double screenY) {
    notificationService.addNotification(new ImmediateNotification(
        i18n.get("game.joinGameRatingConfirmation.title"),
        i18n.get("game.joinGameRatingConfirmation.text", gameInfoBean.getMinRating(), gameInfoBean.getMaxRating(), playerRating),
        Severity.INFO,
        asList(
            new Action(i18n.get("game.join"), event -> doJoinGame(gameInfoBean, null, screenX, screenY)),
            new Action(i18n.get("game.cancel"))
        )
    ));
  }

  private void doJoinGame(GameInfoBean gameInfoBean, String password, double screenX, double screenY) {
    if (preferencesService.getPreferences().getForgedAlliance().getPath() == null) {
      preferencesService.letUserChoseGameDirectory()
          .thenAccept(isPathValid -> {
            if (isPathValid != null && !isPathValid) {
              doJoinGame(gameInfoBean, password, screenX, screenY);
            }
          });
      return;
    }

    if (gameInfoBean.getPasswordProtected() && password == null) {
      enterPasswordController.setGameInfoBean(gameInfoBean);
      passwordPopup.show(gamesRoot.getScene().getWindow(), screenX, screenY);
    } else {
      gameService.joinGame(gameInfoBean, password)
          .exceptionally(throwable -> {
            logger.warn("Game could not be joined", throwable);
            notificationService.addNotification(
                new ImmediateNotification(
                    i18n.get("errorTitle"),
                    i18n.get("games.couldNotJoin"),
                    ERROR, asList(new DismissAction(i18n), new ReportAction(i18n, reportingService, throwable))));
            return null;
          });
    }
  }

  @FXML
  void onCreateGameButtonClicked(ActionEvent actionEvent) {
    Button button = (Button) actionEvent.getSource();

    if (preferencesService.getPreferences().getForgedAlliance().getPath() == null) {
      preferencesService.letUserChoseGameDirectory()
          .thenAccept(isPathValid -> {
            if (isPathValid != null && !isPathValid) {
              onCreateGameButtonClicked(actionEvent);
            }
          });
      return;
    }

    Bounds screenBounds = createGameButton.localToScreen(createGameButton.getBoundsInLocal());
    createGamePopup.show(button.getScene().getWindow(), screenBounds.getMinX(), screenBounds.getMaxY());
  }

  @FXML
  void onMapLargePreview() {
    if (currentGameInfoBean == null) {
      return;
    }
    mapDetailPopup = getMapDetailPopup();
    MapDetailController mapDetailController = applicationContext.getBean(MapDetailController.class);
    MapInfoBean mapInfoBean = mapService.getMapInfoBeanFromVaultByName(currentGameInfoBean.getMapTechnicalName());
    if (mapInfoBean == null) {
      mapDetailPopup.hide();
      String title = i18n.get("errorTitle");
      String message = i18n.get("mapPreview.loadFailure.message");
      notificationService.addNotification(new ImmediateNotification(title, message, Severity.WARN));
    } else {
      mapDetailController.createPreview(mapInfoBean);
      WindowController windowController = applicationContext.getBean(WindowController.class);
      windowController.configure(mapDetailPopup, mapDetailController.getRoot(), false, CLOSE);
      mapDetailPopup.centerOnScreen();
      mapDetailPopup.show();
    }
  }

  private Stage getMapDetailPopup() {
    if (mapDetailPopup == null) {
      mapDetailPopup = new Stage(StageStyle.TRANSPARENT);
      mapDetailPopup.initModality(Modality.NONE);
      mapDetailPopup.initOwner(getRoot().getScene().getWindow());
    }
    return mapDetailPopup;
  }

  public Node getRoot() {
    return gamesRoot;
  }

  @FXML
  void onTableButtonClicked() {
    GamesTableController gamesTableController = applicationContext.getBean(GamesTableController.class);
    Platform.runLater(() -> {
      gamesTableController.initializeGameTable(filteredItems);

      Node root = gamesTableController.getRoot();
      populateContainer(root);
    });
  }

  private void populateContainer(Node root) {
    gameViewContainer.getChildren().setAll(root);
    AnchorPane.setBottomAnchor(root, 0d);
    AnchorPane.setLeftAnchor(root, 0d);
    AnchorPane.setRightAnchor(root, 0d);
    AnchorPane.setTopAnchor(root, 0d);
  }

  @FXML
  void onTilesButtonClicked() {
    GamesTilesContainerController gamesTilesContainerController = applicationContext.getBean(GamesTilesContainerController.class);
    gamesTilesContainerController.createTiledFlowPane(filteredItems);

    Node root = gamesTilesContainerController.getRoot();
    populateContainer(root);
  }
}
