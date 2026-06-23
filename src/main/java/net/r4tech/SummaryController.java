package net.r4tech;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.scene.control.Button;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class SummaryController {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH);

    @FXML
    private Button bClose;
    @FXML
    private Label lsPaymentDeadline;
    @FXML
    private Label lsPaymentDate;
    @FXML
    private Label lsDayCount;
    @FXML
    private Label lsPaidAmount;
    @FXML
    private Label lsInterestRate;
    @FXML
    private Label lsInterestAmount;
    @FXML
    private Label lsBaseAmount;

    public void populateSummaryFields(String[] params) {
        lsPaymentDeadline.setText(LocalDate.parse(params[0]).format(DATE_FORMAT));
        lsPaymentDate.setText(LocalDate.parse(params[1]).format(DATE_FORMAT));
        lsDayCount.setText(params[2]);
        lsPaidAmount.setText(params[3]);
        lsInterestRate.setText(params[4]);
        lsInterestAmount.setText(params[5]);
        lsBaseAmount.setText(params[6]);
    }

    @FXML
    private void closeWindow() {
        Stage stage = (Stage) bClose.getScene().getWindow();
        stage.close();
    }
}