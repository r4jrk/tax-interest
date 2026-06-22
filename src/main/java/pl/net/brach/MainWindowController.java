package pl.net.brach;

import java.io.IOException;
import java.net.URL;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.scene.control.Button;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.net.brach.commons.data.InterestRateRepository;
import pl.net.brach.commons.nbp.NbpClient;
import pl.net.brach.commons.nbp.NbpUnavailableException;
import pl.net.brach.commons.ui.Dialogs;
import pl.net.brach.commons.ui.R4TechBannerView;

public class MainWindowController implements Initializable {

    private static final int DAYS_IN_A_YEAR = 365; // Polish tax convention: a fixed 365-day year (ignores leap years)
    private static final double INTEREST_AMOUNT_THRESHOLD = 8.7; //8,70 zł — statutory minimum below which interest is waived
    private static final List<String> DATE_FORMATS = Arrays.asList("dd-MM-yyyy", "dd/MM/yyyy", "ddMMyyyy", "dd.MM.yyyy",
            "yyyy-MM-dd", "yyyy/MM/dd", "yyyyMMdd", "yyyy.MM.dd");

    private static final Logger log = LoggerFactory.getLogger(MainWindowController.class);

    private final NbpClient nbpClient = new NbpClient();
    private final InterestRateRepository interestRateRepository = new InterestRateRepository();

    public DatePicker dpPaymentDeadline;
    public DatePicker dpPaymentDate;
    public TextField tfPaidAmount;
    public TextField tfDayCount;
    public ComboBox<String> cbInterestRate;
    public Button bOK;
    public Button bClose;
    public AnchorPane apMain;
    public StackPane bannerContainer;

    //Calculation fields
    private LocalDate effectivePaymentDeadline;
    private LocalDate effectivePaymentDate;
    private long daysDifference = 0;
    private double baseQuota = 0;
    private double interestRate = 0;
    private double interestAmount = 0;
    private double interestAmountRounded = 0;

    //Output fields
    private String paymentDeadlineOutput;
    private String paymentDateOutput;
    private String daysDifferenceOutput;
    private String amountPaidOutput;
    private String interestRateOutput;
    private String interestAmountOutput;
    private String interestAmountRoundedOutput;
    private String baseAmountOutput;
    private String baseAmountRoundedOutput;

    @FXML
    public void initialize(URL url, ResourceBundle rb) {
        bannerContainer.getChildren().add(new R4TechBannerView());

        //Populate interest rates in to a Combo Box
        cbInterestRate.getItems().clear();
        cbInterestRate.getItems().addAll("8,00 %");
        cbInterestRate.getSelectionModel().selectFirst();

        //Setup Text Field
        setupTextField(tfPaidAmount);

        //Setup Date Pickers
        setupDatePicker(dpPaymentDeadline);
        setupDatePicker(dpPaymentDate);
    }

    private Stage getCurrentStage() {
        return (Stage) bOK.getScene().getWindow();
    }

    private void setupTextField(TextField textFieldName) {
        textFieldName.textProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (!newValue.matches("^\\d*,\\d{2}")) {
                        textFieldName.setText(newValue.replaceAll("[^\\d,]", ""));
                    }
                }
        );
    }

    private void setupDatePicker(DatePicker datePickerName) {
        datePickerName.getEditor().addEventFilter(KeyEvent.KEY_PRESSED, (KeyEvent keyEvent) -> {
            if (datePickerName.getValue() != null) {
                if (keyEvent.getCode() == KeyCode.DOWN) {
                    datePickerName.setValue(datePickerName.getValue().minusDays(1));
                    keyEvent.consume();
                }
                if (keyEvent.getCode() == KeyCode.UP) {
                    datePickerName.setValue(datePickerName.getValue().plusDays(1));
                    keyEvent.consume();
                }
            }
        });

        datePickerName.getEditor().textProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (newValue.length() > 10) {
                        // Cap at a full date (dd-MM-yyyy) instead of wiping the whole field.
                        datePickerName.getEditor().setText(newValue.substring(0, 10));
                    }
                    if (dpPaymentDeadline.getEditor().getText() != null && !dpPaymentDeadline.getEditor().getText().isEmpty() &&
                            dpPaymentDate.getEditor().getText() != null && !dpPaymentDate.getEditor().getText().isEmpty()) {
                        calculateDaysDifference();
                        getEffectiveInterestRate();
                    }
                }
        );

        datePickerName.setConverter(new StringConverter<LocalDate>() {
            @Override
            public String toString(LocalDate date) {
                if (date != null) {
                    for (String pattern : DATE_FORMATS) {
                        try {
                            if (date.isAfter(LocalDate.now())) {
                                return DateTimeFormatter.ofPattern(pattern).format(LocalDate.now().minusDays(1));
                            } else {
                                return DateTimeFormatter.ofPattern(pattern).format(date);
                            }
                        } catch (DateTimeException dte) {
                            log.debug("Nie udało się sformatować daty wg wzorca {}", pattern);
                        }
                    }
                }
                return "";
            }

            @Override
            public LocalDate fromString(String string) {
                if (string != null && !string.isEmpty()) {
                    for (String pattern : DATE_FORMATS) {
                        try {
                            return LocalDate.parse(string, DateTimeFormatter.ofPattern(pattern));
                        } catch (DateTimeParseException ignored) { }
                    }
                    calculateDaysDifference();
                    getEffectiveInterestRate();
                }
                return null;
            }
        });
    }

    private LocalDate getDateInput(String datePickerName) {
        Stage stage = getCurrentStage();
        DatePicker datePicker = (DatePicker) stage.getScene().lookup("#" + datePickerName);

        String dateInput = datePicker.getEditor().getText();

        if (dateInput.length() == 10) { //Full date provided
            LocalDate extractedDate = null;
            for (String pattern : DATE_FORMATS) {
                try {
                    extractedDate = LocalDate.parse(dateInput, DateTimeFormatter.ofPattern(pattern));
                } catch (DateTimeParseException ignored) { }
            }
            return extractedDate;
        }
        return null;
    }

    private void calculateDaysDifference() {
        LocalDate paymentDeadline = getDateInput("dpPaymentDeadline");
        LocalDate paymentDate = getDateInput("dpPaymentDate");

        if (paymentDeadline != null && paymentDate != null) {
            effectivePaymentDeadline = checkForBankHolidays(paymentDeadline);
            effectivePaymentDate = checkForBankHolidays(paymentDate);

            if (effectivePaymentDeadline != null && effectivePaymentDate != null) {
                daysDifference = ChronoUnit.DAYS.between(effectivePaymentDeadline, effectivePaymentDate);
                daysDifferenceOutput = String.valueOf(daysDifference);
                tfDayCount.setText(daysDifferenceOutput);
                paymentDeadlineOutput = effectivePaymentDeadline.toString();
                paymentDateOutput = effectivePaymentDate.toString();
            }
        }
    }

    /**
     * Rolls {@code date} forward to the nearest business day (a day NBP published rates for),
     * covering weekends and holidays. Returns {@code null} if the NBP API can't be reached —
     * callers already guard against {@code null}.
     */
    private LocalDate checkForBankHolidays(LocalDate date) {
        try {
            return nbpClient.nearestPublicationOnOrAfter(date);
        } catch (NbpUnavailableException e) {
            // Called on every keystroke during recalculation, so only log here; the OK action
            // (displaySummary) surfaces a dialog when results can't be produced.
            log.warn("Nie udało się ustalić dnia roboczego z API NBP dla {}: {}", date, e.getMessage());
            return null;
        }
    }

    private void getAmountPaid() {
        String inputValue = tfPaidAmount.getText();

        if (inputValue != null && !inputValue.equals("")) {
            baseQuota = Double.parseDouble(tfPaidAmount.getText().replace(",", "."));
            amountPaidOutput = String.format("%.2f", baseQuota) + " zł";
        }
    }

    private void getEffectiveInterestRate() {
        List<List<String>> ratesFromFile = interestRateRepository.getInterestRates();

        ArrayList daysSpentArrayList = new ArrayList();
        ArrayList ratesArrayList = new ArrayList();

        LocalDate ratesPeriodStartDate;
        LocalDate ratesPeriodEndDate;

        long daysBetweenPaymentDeadlineAndPaymentDate;
        long daysBetweenPaymentDeadlineAndPeriodEnd;
        long daysInCurrentPeriod;
        long periodCounter = 0;
        long daysLeftToSpend;
        double nominalRate;
        double intRate = 0.0;
        long daysSpentSum = 0;

        if (effectivePaymentDeadline != null && effectivePaymentDate != null) {
            daysBetweenPaymentDeadlineAndPaymentDate = ChronoUnit.DAYS.between(effectivePaymentDeadline, effectivePaymentDate);

            daysLeftToSpend = daysBetweenPaymentDeadlineAndPaymentDate;

            for (int i = 1; i < ratesFromFile.size(); i++) { //Start from i = 1, because at i = 0 is header

                if (!ratesFromFile.get(i).get(1).equals("")) { //For last period in Rates CSV endDate is empty, hence assign today's date to ratesPeriodEndDate
                    ratesPeriodEndDate = LocalDate.parse(ratesFromFile.get(i).get(1), DateTimeFormatter.ofPattern(DATE_FORMATS.get(4)));
                } else {
                    ratesPeriodEndDate = LocalDate.now();
                }

                ratesPeriodStartDate = LocalDate.parse(ratesFromFile.get(i).get(0), DateTimeFormatter.ofPattern(DATE_FORMATS.get(4)));

                daysBetweenPaymentDeadlineAndPeriodEnd = ChronoUnit.DAYS.between(effectivePaymentDeadline, ratesPeriodEndDate);

                if ((effectivePaymentDeadline.isAfter(ratesPeriodStartDate) || effectivePaymentDeadline.isEqual(ratesPeriodStartDate)) &&
                        (effectivePaymentDate.isAfter(ratesPeriodStartDate) || effectivePaymentDate.isEqual(ratesPeriodStartDate)) &&
                        (effectivePaymentDeadline.isBefore(ratesPeriodEndDate) || effectivePaymentDeadline.isEqual(ratesPeriodEndDate)) &&
                        (effectivePaymentDate.isBefore(ratesPeriodEndDate) || effectivePaymentDate.isEqual(ratesPeriodEndDate))) { //Just one period
                    long daysSpent = ChronoUnit.DAYS.between(effectivePaymentDeadline, effectivePaymentDate);
                    interestRate = Double.parseDouble(ratesFromFile.get(i).get(2));
                    daysSpentArrayList.add(daysSpent);
                    ratesArrayList.add(interestRate);
                    break;
                } else if ((effectivePaymentDeadline.isAfter(ratesPeriodEndDate) || (effectivePaymentDeadline.isEqual(ratesPeriodEndDate)))
                        && (effectivePaymentDate.isAfter(ratesPeriodEndDate) || effectivePaymentDate.isEqual(ratesPeriodEndDate))) {
                } else { //Two or more periods
                    periodCounter++;
                    nominalRate = Double.parseDouble(ratesFromFile.get(i).get(2));

                    log.debug("Rates period {}: start {}, end {}, nominal rate {}",
                            periodCounter, ratesPeriodStartDate, ratesPeriodEndDate, nominalRate);

                    long daysSpent;

                    if (periodCounter == 1) { //First period of periods
                        daysSpent = daysBetweenPaymentDeadlineAndPeriodEnd + 1;
                        daysLeftToSpend = daysLeftToSpend - daysSpent;
                        daysSpentArrayList.add(daysSpent);
                        ratesArrayList.add(Double.parseDouble(ratesFromFile.get(i).get(2)));
                        log.debug("Days spent in period 1: {}, days left to spend: {}", daysSpent, daysLeftToSpend);
                    } else { //Second or further period
                        daysInCurrentPeriod = ChronoUnit.DAYS.between(ratesPeriodStartDate, ratesPeriodEndDate);
                        boolean isDaysLeftToSpendPositive = (daysLeftToSpend - daysInCurrentPeriod) > 0;

                        if (isDaysLeftToSpendPositive) { //There will be another period
                            daysSpent = daysInCurrentPeriod;
                            daysLeftToSpend = daysLeftToSpend - daysSpent;
                            daysSpentArrayList.add(daysSpent);
                            ratesArrayList.add(Double.parseDouble(ratesFromFile.get(i).get(2)));
                            log.debug("Days spent in period {}: {}, days left to spend: {}", periodCounter, daysSpent, daysLeftToSpend);
                        } else { //There won't be another period
                            daysSpent = daysLeftToSpend;
                            daysLeftToSpend = 0;
                            daysSpentArrayList.add(daysSpent);
                            ratesArrayList.add(Double.parseDouble(ratesFromFile.get(i).get(2)));
                            log.debug("Days spent in period {}: {}, days left to spend: {}", periodCounter, daysSpent, daysLeftToSpend);
                            break;
                        }
                    }
                }
            }

            for (int j = 0; j < daysSpentArrayList.size(); j++) {
                daysSpentSum += Long.parseLong((daysSpentArrayList.get(j).toString()));
                intRate += Double.parseDouble((daysSpentArrayList.get(j).toString())) * Double.parseDouble(ratesArrayList.get(j).toString());
            }

            interestRate = intRate / daysSpentSum;
            daysDifference = daysSpentSum;

            interestRateOutput = String.format("%.2f", interestRate) + " %";

            cbInterestRate.getItems().clear();
            cbInterestRate.getItems().addAll(interestRateOutput);
            cbInterestRate.getSelectionModel().selectFirst();
        } else {
            cbInterestRate.getSelectionModel().selectFirst();
        }
    }

    private void calculateInterestAmount() {
        if (daysDifference != 0 && baseQuota != 0 && interestRate != 0) {
            interestAmount = (daysDifference * baseQuota * interestRate / 100) /
                    (DAYS_IN_A_YEAR + daysDifference * interestRate / 100);
            interestAmountOutput = String.format("%.2f", interestAmount) + " zł";

            interestAmountRounded = Math.round(interestAmount);
            interestAmountRoundedOutput = String.format("%.2f", interestAmountRounded) + " zł";

            if (interestAmount < INTEREST_AMOUNT_THRESHOLD) {
                interestAmount = 0;
                interestAmountOutput = String.format("%.2f", interestAmount) + " zł";
                interestAmountRounded = 0;
                interestAmountRoundedOutput = String.format("%.2f", interestAmountRounded) + " zł";
            }
        }
    }

    private void calculateBaseQuota() {
        if (baseQuota != 0) {
            double baseAmount = Math.round((baseQuota - interestAmount) * 100.00) / 100.00;
            baseAmountOutput = String.format("%.2f", baseAmount) + " zł";
            double baseAmountRounded = Math.round(baseQuota - interestAmountRounded);
            baseAmountRoundedOutput = String.format("%.2f", baseAmountRounded) + " zł";
        }
    }

    @FXML
    private void displaySummary() throws IOException {
        //Order of execution is important

        if (dpPaymentDeadline.getEditor().getText() != null && !dpPaymentDeadline.getEditor().getText().isEmpty() &&
                dpPaymentDate.getEditor().getText() != null && !dpPaymentDate.getEditor().getText().isEmpty() &&
                tfPaidAmount.getText() != null && !tfPaidAmount.getText().isEmpty()
        ) {

            calculateDaysDifference();
            getAmountPaid();
            getEffectiveInterestRate();
            calculateInterestAmount();
            calculateBaseQuota();

            if (paymentDeadlineOutput != null &&
                    paymentDateOutput != null &&
                    daysDifferenceOutput != null &&
                    amountPaidOutput != null &&
                    interestRateOutput != null &&
                    interestAmountOutput != null &&
                    baseAmountOutput != null) {

                if (daysDifference > 0) {
                    String[] summaryParams = new String[8];
                    summaryParams[0] = paymentDeadlineOutput;
                    summaryParams[1] = paymentDateOutput;
                    summaryParams[2] = daysDifferenceOutput;
                    summaryParams[3] = amountPaidOutput;
                    summaryParams[4] = interestRateOutput;
                    if (interestAmount != interestAmountRounded) {
                        summaryParams[5] = interestAmountOutput + " (" + interestAmountRoundedOutput + ")";
                        summaryParams[6] = baseAmountOutput + " (" + baseAmountRoundedOutput + ")";
                    } else {
                        summaryParams[5] = interestAmountOutput;
                        summaryParams[6] = baseAmountOutput;
                    }

                    TaxInterest.displaySummary(summaryParams);
                } else if (daysDifference == 0) {
                    Dialogs.info("Brak odsetek", "Płatność wykonano w dniu wymagalności.");
                } else {
                    Dialogs.info("Brak odsetek", "Płatność wykonano przed dniem wymagalności.");
                }
            }
        } else {
            Dialogs.error("Niepełne dane", "Wprowadź termin płatności, datę zapłaty oraz zapłaconą kwotę.");
        }
    }

    @FXML
    public void closeWindow() {
        Stage stage = (Stage) bClose.getScene().getWindow();
        stage.close();
    }
}
