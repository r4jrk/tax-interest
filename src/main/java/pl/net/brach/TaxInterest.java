package pl.net.brach;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import pl.net.brach.commons.ui.Branding;

import java.io.IOException;

public class TaxInterest extends Application {

    static final String BRACHSOFT_TITLE = "r4_tech - Odsetki podatkowe v.1.2";
    static final String STYLE_PATH = "pl/net/brach/style.css";

    public static void main(String[] args) {
        launch();
    }

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(this.getClass().getResource("MainWindow.fxml"));
        Parent root = fxmlLoader.load();

        Scene scene = new Scene(root);
        scene.getStylesheets().add(STYLE_PATH);

        stage.setTitle(BRACHSOFT_TITLE);
        Branding.applyIcon(stage);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    protected static void displaySummary(String[] args) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(TaxInterest.class.getResource("Summary.fxml"));
        Pane root = fxmlLoader.load();

        Scene scene = new Scene(root);
        scene.getStylesheets().add(STYLE_PATH);

        Stage stage = new Stage();

        stage.setTitle(BRACHSOFT_TITLE + " - Podsumowanie");
        Branding.applyIcon(stage);
        stage.setScene(scene);
        stage.setResizable(false);

        SummaryController summaryController = fxmlLoader.getController();
        summaryController.populateSummaryFields(args);

        stage.show();
    }
}