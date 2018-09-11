package com.core;

import com.core.data.CoreLink;
import com.core.data.CoreNode;
import com.core.graph.NetworkGraph;
import com.core.rest.ConfigOption;
import com.core.rest.CoreApi;
import com.core.rest.GetConfig;
import com.core.rest.SetConfig;
import com.core.ui.*;
import com.core.websocket.CoreWebSocket;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.Data;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.event.ItemEvent;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Data
public class Controller implements Initializable {
    private static final Logger logger = LogManager.getLogger();

    @FXML
    private StackPane stackPane;

    @FXML
    private BorderPane borderPane;

    @FXML
    private SwingNode swingNode;

    private Application application;
    private Stage window;

    // core client utilities
    private CoreWebSocket coreWebSocket;
    private CoreApi coreApi;
    private CoreClient coreClient;

    // ui elements
    private NetworkGraph networkGraph = new NetworkGraph(this);
    private AnnotationToolbar annotationToolbar = new AnnotationToolbar(networkGraph);
    private NodeDetails nodeDetails = new NodeDetails();
    private LinkDetails linkDetails = new LinkDetails(networkGraph);
    private GraphToolbar graphToolbar = new GraphToolbar(this);

    // dialogs
    private SessionsDialog sessionsDialog = new SessionsDialog(this);
    private ServiceDialog serviceDialog = new ServiceDialog(this);
    private NodeServicesDialog nodeServicesDialog = new NodeServicesDialog(this);
    private NodeEmaneDialog nodeEmaneDialog = new NodeEmaneDialog(this);
    private NodeWlanDialog nodeWlanDialog = new NodeWlanDialog(this);
    private ConfigDialog configDialog = new ConfigDialog(this);
    private HooksDialog hooksDialog = new HooksDialog(this);

    public Controller() {
        // load configuration
        Properties properties = getConfiguration();
        String coreUrl = properties.getProperty("core-rest");
        logger.info("core rest: {}", coreUrl);

        // start web socket thread
        try {
            coreWebSocket = new CoreWebSocket(this, coreUrl);
            coreWebSocket.start();
        } catch (URISyntaxException ex) {
            logger.error("error starting web socket", ex);
        }

        coreApi = new CoreApi(coreUrl);
        coreClient = new CoreClient(this);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            try {
                coreClient.initialJoin();
            } catch (IOException ex) {
                logger.error("failure during initial join", ex);
                Toast.error(String.format("Initial join failure: %s", ex.getMessage()));
            }
        });
    }

    public void setWindow(Stage window) {
        this.window = window;
        sessionsDialog.setOwner(window);
        hooksDialog.setOwner(window);
        nodeServicesDialog.setOwner(window);
        serviceDialog.setOwner(window);
        nodeWlanDialog.setOwner(window);
        nodeEmaneDialog.setOwner(window);
        configDialog.setOwner(window);
    }

    @FXML
    private void onHelpMenuWebsite(ActionEvent event) {
        application.getHostServices().showDocument("https://github.com/coreemu/core");
    }

    @FXML
    private void onHelpMenuDocumentation(ActionEvent event) {
        application.getHostServices().showDocument("http://coreemu.github.io/core/");
    }

    @FXML
    private void onHelpMenuMailingList(ActionEvent event) {
        application.getHostServices().showDocument("https://publists.nrl.navy.mil/mailman/listinfo/core-users");
    }

    @FXML
    private void onOpenXmlAction() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Session");
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML", "*.xml"));
        File file = fileChooser.showOpenDialog(window);
        if (file != null) {
            logger.info("opening session xml: {}", file.getPath());
            try {
                coreClient.openSession(file);
            } catch (IOException ex) {
                logger.error("error opening session xml", ex);
            }
        }
    }

    @FXML
    private void onSaveXmlAction() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Session");
        fileChooser.setInitialFileName("session.xml");
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML", "*.xml"));
        File file = fileChooser.showSaveDialog(window);
        if (file != null) {
            logger.info("saving session xml: {}", file.getPath());
            try {
                coreClient.saveSession(file);
            } catch (IOException ex) {
                logger.error("error saving session xml", ex);
            }
        }
    }

    @FXML
    private void onJoinSessionMenu(ActionEvent event) {
        logger.info("join sessions menu clicked!");
        try {
            sessionsDialog.showDialog();
        } catch (IOException ex) {
            logger.error("error getting session dialog", ex);
        }
    }

    @FXML
    private void onSessionHooksMenu(ActionEvent event) {
        hooksDialog.showDialog();
    }

    @FXML
    private void onSessionOptionsMenu(ActionEvent event) {
        try {
            GetConfig config = coreClient.getSessionConfig();
            configDialog.showDialog("Session Options", config, () -> {
                List<ConfigOption> options = configDialog.getOptions();
                SetConfig setConfig = new SetConfig(options);
                try {
                    boolean result = coreClient.setSessionConfig(setConfig);
                    if (result) {
                        Toast.info("Session options saved");
                    } else {
                        Toast.error("Failure to set session config");
                    }
                } catch (IOException ex) {
                    logger.error("error getting session config");
                }
            });
        } catch (IOException ex) {
            logger.error("error getting session config");
        }
    }

    private Properties getConfiguration() {
        try {
            Properties properties = new Properties();
            properties.load(getClass().getResourceAsStream("/config.properties"));
            return properties;
        } catch (IOException ex) {
            logger.error("error reading config file");
            throw new RuntimeException("configuration file did not exist");
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("controller initialize");
        swingNode.setContent(networkGraph.getGraphViewer());

        // set graph toolbar
        borderPane.setLeft(graphToolbar);

        // setup snackbar
        Toast.setSnackbarRoot(stackPane);

        // node details
        networkGraph.getGraphViewer().getPickedVertexState().addItemListener(event -> {
            CoreNode node = (CoreNode) event.getItem();
            logger.info("picked: {}", node.getName());
            if (event.getStateChange() == ItemEvent.SELECTED) {
                Platform.runLater(() -> {
                    nodeDetails.setNode(node);
                    borderPane.setRight(nodeDetails);
                });
            } else {
                Platform.runLater(() -> borderPane.setRight(null));
            }
        });

        // edge details
        networkGraph.getGraphViewer().getPickedEdgeState().addItemListener(event -> {
            CoreLink link = (CoreLink) event.getItem();
            logger.info("picked: {} - {}", link.getNodeOne(), link.getNodeTwo());
            if (event.getStateChange() == ItemEvent.SELECTED) {
                Platform.runLater(() -> {
                    linkDetails.setLink(link);
                    borderPane.setRight(linkDetails);
                });
            } else {
                Platform.runLater(() -> borderPane.setRight(null));
            }
        });
    }
}