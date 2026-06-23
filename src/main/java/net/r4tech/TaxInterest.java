package net.r4tech;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import net.r4tech.commons.ui.Branding;

import java.io.IOException;

public class TaxInterest extends Application {

    static final String R4_TECH_TITLE = "R4_TECH - Odsetki podatkowe v.1.3";
    static final String STYLE_PATH = "net/r4tech/style.css";
    static final String APP_ICON = "/net/r4tech/tax-interest-icon.png";

    public static void main(String[] args) {
        launch();
    }

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(this.getClass().getResource("MainWindow.fxml"));
        Parent root = fxmlLoader.load();

        Scene scene = new Scene(root);
        scene.getStylesheets().add(STYLE_PATH);

        stage.setTitle(R4_TECH_TITLE);
        Branding.applyIcon(stage, APP_ICON, TaxInterest.class);
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

        stage.setTitle(R4_TECH_TITLE + " - Podsumowanie");
        Branding.applyIcon(stage, APP_ICON, TaxInterest.class);
        stage.setScene(scene);
        stage.setResizable(false);

        SummaryController summaryController = fxmlLoader.getController();
        summaryController.populateSummaryFields(args);

        stage.show();
    }
}