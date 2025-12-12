package com.motorway.gui;

import com.motorway.enums.RoadBlockageLevel;
import com.motorway.enums.Severity;
import com.motorway.filehandler.IncidentFileHandler;
import com.motorway.filehandler.TeamFileHandler;
import com.motorway.manager.IncidentManager;
import com.motorway.model.*;
import com.motorway.service.AuthenticationService;
import com.motorway.service.MapService;
import com.motorway.service.WeatherService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.scene.web.WebEngine;
import javafx.stage.Modality;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class HelloApplication extends Application {

    // -------------------------- GLOBALS --------------------------
    private Stage primaryStage;
    private AuthenticationService auth = AuthenticationService.getInstance();
    private IncidentManager incidentManager = new IncidentManager();

    // LOCATION SELECTOR (stores latest clicked location)
    private double selectedLat = 0;
    private double selectedLng = 0;
    private String selectedAddress = "Unknown";

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        primaryStage.setTitle("Highway Management System");
        showLoginScreen();
    }

    // -------------------------- LOGIN SCREEN --------------------------
    private void showLoginScreen() {
        VBox root = new VBox(18);
        root.setPadding(new Insets(18));
        root.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("Highway Management System");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(14);
        grid.setAlignment(Pos.CENTER);

        Label userLabel = new Label("Username:");
        TextField usernameField = new TextField();
        GridPane.setConstraints(userLabel, 0, 0);
        GridPane.setConstraints(usernameField, 1, 0);

        Label passLabel = new Label("Password:");
        PasswordField passwordField = new PasswordField();
        GridPane.setConstraints(passLabel, 0, 1);
        GridPane.setConstraints(passwordField, 1, 1);

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");
        GridPane.setConstraints(errorLabel, 0, 2, 2, 1);

        grid.getChildren().addAll(userLabel, usernameField, passLabel, passwordField, errorLabel);

        HBox btnBox = new HBox(20);
        btnBox.setAlignment(Pos.CENTER);
        Button loginButton = new Button("Login");
        Button registerButton = new Button("Register");
        btnBox.getChildren().addAll(loginButton, registerButton);

        root.getChildren().addAll(title, grid, btnBox);

        loginButton.setOnAction(e -> {
            String u = usernameField.getText().trim();
            String p = passwordField.getText().trim();
            if (u.isEmpty() || p.isEmpty()) {
                errorLabel.setText("Enter username and password");
                return;
            }

            User user = auth.login(u, p);
            if (user == null) {
                errorLabel.setText("Invalid credentials");
                return;
            }

            incidentManager.getAllIncidents().clear();
            List<Incident> loaded = IncidentFileHandler.loadIncidents();
            if (loaded != null) {
                incidentManager.getAllIncidents().addAll(loaded);
                System.out.println("Loaded incidents: " + loaded.size());
            } else {
                System.out.println("Loaded incidents: 0 (null returned)");
            }

            List<Team> savedTeams = TeamFileHandler.loadTeams();
            if (savedTeams != null) {
                savedTeams.forEach(incidentManager::addTeam);
                System.out.println("Loaded teams: " + savedTeams.size());
            } else {
                System.out.println("Loaded teams: 0 (null returned)");
            }

            if (user.isAdmin()) showAdminDashboard();
            else showUserDashboard();
        });

        Scene scene = new Scene(root, 520, 600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // -------------------------- ADMIN DASHBOARD --------------------------
    private void showAdminDashboard() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));

        HBox top = new HBox(12);
        top.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label("Admin Dashboard");
        lbl.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button logout = new Button("Logout");
        top.getChildren().addAll(lbl, sp, logout);
        root.setTop(top);

        VBox left = new VBox(12);
        Label stats = new Label("Total: " + incidentManager.getAllIncidents().size());
        Button manageIncBtn = new Button("Manage Incidents");
        Button manageTeamBtn = new Button("Manage Teams");
        Button reportBtn = new Button("Overall Report");
        left.getChildren().addAll(stats, manageIncBtn, manageTeamBtn, reportBtn);
        root.setLeft(left);

        VBox center = new VBox(12);
        Label prioLabel = new Label("Priority Incidents:");
        ListView<String> prioList = new ListView<>();
        updatePriorityList(prioList);
        center.getChildren().addAll(prioLabel, prioList);
        root.setCenter(center);

        logout.setOnAction(e -> {
            auth.logout();
            showLoginScreen();
        });

        manageTeamBtn.setOnAction(e -> showManageTeams(stats, prioList));
        manageIncBtn.setOnAction(e -> showManageIncidents(prioList, stats));
        reportBtn.setOnAction(e -> showOverallReport());

        Scene sc = new Scene(root, 1200, 800);
        primaryStage.setScene(sc);
    }

    private void updatePriorityList(ListView<String> view) {
        List<Incident> list = incidentManager.getActiveIncidents();
        List<String> display = new ArrayList<>();
        for (Incident i : list) {
            String addr = i.getLocation() == null ? "N/A" : i.getLocation().getAddress();
            display.add(i.getType() + " (ID:" + i.getId() + ") - " + addr);
        }
        view.setItems(FXCollections.observableArrayList(display));
    }

    // -------------------------- MANAGE TEAMS --------------------------
    private void showManageTeams(Label stats, ListView<String> prioList) {
        Stage st = new Stage();
        st.initModality(Modality.APPLICATION_MODAL);
        st.setTitle("Manage Teams");

        VBox root = new VBox(12);
        root.setPadding(new Insets(12));

        ListView<String> list = new ListView<>();
        refreshTeamList(list);

        Button addBtn = new Button("Add Team");
        addBtn.setOnAction(e -> {
            TextInputDialog d = new TextInputDialog();
            d.setHeaderText("Enter team name:");
            Optional<String> r = d.showAndWait();
            r.ifPresent(n -> {
                Team t = new Team(incidentManager.getAllTeams().size() + 1, n, 5);
                incidentManager.addTeam(t);
                TeamFileHandler.saveTeams(incidentManager.getAllTeams());
                System.out.println("Saved teams: " + incidentManager.getAllTeams().size());
                refreshTeamList(list);
            });
        });

        root.getChildren().addAll(list, addBtn);
        st.setScene(new Scene(root, 400, 400));
        st.show();
    }

    private void refreshTeamList(ListView<String> v) {
        List<String> out = new ArrayList<>();
        for (Team t : incidentManager.getAllTeams()) {
            out.add(t.toString());
        }
        v.setItems(FXCollections.observableArrayList(out));
    }

    // -------------------------- MANAGE INCIDENTS (ADMIN) --------------------------
    private void showManageIncidents(ListView<String> prioList, Label stats) {
        Stage st = new Stage();
        st.initModality(Modality.APPLICATION_MODAL);
        st.setTitle("Manage Incidents");

        VBox root = new VBox(12);
        root.setPadding(new Insets(12));

        HBox controls = new HBox(12);
        Label fl = new Label("Filter:");
        ComboBox<String> filter = new ComboBox<>();
        filter.getItems().addAll("All", "REPORTED", "DISPATCHED", "RESOLVED");
        filter.setValue("All");

        Button refresh = new Button("Apply Filter");
        Button assign = new Button("Assign Team");
        Button resolve = new Button("Mark Resolved");
        Button showRep = new Button("Show Report");

        controls.getChildren().addAll(fl, filter, refresh, assign, resolve, showRep);

        TableView<Incident> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Incident, Integer> c1 = new TableColumn<>("ID");
        c1.setCellValueFactory(new PropertyValueFactory<>("id"));

        TableColumn<Incident, String> c2 = new TableColumn<>("Type");
        c2.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getType()));

        TableColumn<Incident, String> c3 = new TableColumn<>("Status");
        c3.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getStatus().name()));

        TableColumn<Incident, String> c4 = new TableColumn<>("Location");
        c4.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getLocation() == null ? "N/A" : d.getValue().getLocation().getAddress()
        ));

        table.getColumns().addAll(c1, c2, c3, c4);


        javafx.collections.ObservableList<Incident> tableData =
                FXCollections.observableArrayList(incidentManager.getAllIncidents());
        table.setItems(tableData);

        Runnable refreshTable = () -> {
            List<Incident> filtered = applyFilter(filter.getValue());
            tableData.setAll(filtered);
        };

        refreshTable.run();

        refresh.setOnAction(e -> refreshTable.run());

        assign.setOnAction(e -> {
            Incident selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                alert("Select an incident");
                return;
            }

            List<Team> avail = incidentManager.getAvailableTeams();
            if (avail.isEmpty()) {
                alert("No available teams");
                return;
            }

            ChoiceDialog<Team> dlg = new ChoiceDialog<>(avail.get(0), avail);
            dlg.setHeaderText("Assign Team to Incident ID " + selected.getId());
            Optional<Team> res = dlg.showAndWait();
            res.ifPresent(t -> {
                try {
                    incidentManager.assignTeamToIncident(selected.getId(), t.getId());
                    // Save and refresh from manager as the source of truth
                    IncidentFileHandler.saveIncidents(incidentManager.getAllIncidents());
                    System.out.println("Saved incidents after assign: " + incidentManager.getAllIncidents().size());
                    refreshTable.run();
                    updatePriorityList(prioList);
                    stats.setText("Total: " + incidentManager.getAllIncidents().size());
                } catch (Exception ex) {
                    alert("Failed: " + ex.getMessage());
                }
            });
        });

        resolve.setOnAction(e -> {
            Incident selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                alert("Select an incident");
                return;
            }
            try {
                incidentManager.resolveIncident(selected.getId());
                IncidentFileHandler.saveIncidents(incidentManager.getAllIncidents());
                System.out.println("Saved incidents after resolve: " + incidentManager.getAllIncidents().size());
                refreshTable.run();
                updatePriorityList(prioList);
            } catch (Exception ex) {
                alert(ex.getMessage());
            }
        });

        showRep.setOnAction(e -> {
            Incident selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                alert("Select an incident");
                return;
            }
            showLargeText("Incident Report", selected.generateReport());
        });

        root.getChildren().addAll(controls, table);
        st.setScene(new Scene(root, 900, 600));
        st.show();
    }

    private List<Incident> applyFilter(String f) {
        if (f == null || f.equals("All")) return new ArrayList<>(incidentManager.getAllIncidents());
        return new ArrayList<>(incidentManager.getIncidentsByStatus(f));
    }

    // -------------------------- REPORTS --------------------------
    private void showOverallReport() {
        List<Incident> all = incidentManager.getAllIncidents();
        StringBuilder sb = new StringBuilder();
        sb.append("Total: ").append(all.size()).append("\n");
        sb.append("Reported: ").append(incidentManager.getIncidentsByStatus("REPORTED").size()).append("\n");
        sb.append("Dispatched: ").append(incidentManager.getIncidentsByStatus("DISPATCHED").size()).append("\n");
        sb.append("Resolved: ").append(incidentManager.getIncidentsByStatus("RESOLVED").size()).append("\n\n");

        sb.append("---- Recent Incidents ----\n");
        all.stream().limit(20).forEach(i -> sb.append(i.generateReport()).append("\n"));

        showLargeText("Overall Report", sb.toString());
    }

    // -------------------------- USER DASHBOARD --------------------------
    private void showUserDashboard() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));

        HBox top = new HBox(12);
        top.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label("User Dashboard");
        lbl.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button logout = new Button("Logout");
        top.getChildren().addAll(lbl, sp, logout);
        root.setTop(top);

        VBox left = new VBox(12);
        Button report = new Button("Report Incident");
        left.getChildren().add(report);
        root.setLeft(left);

        VBox center = new VBox(12);
        ListView<String> list = new ListView<>();
        loadUserIncidents(list);
        center.getChildren().addAll(new Label("Active Incidents:"), list);
        root.setCenter(center);

        logout.setOnAction(e -> {
            auth.logout();
            showLoginScreen();
        });

        report.setOnAction(e -> showCreateIncident(list));

        primaryStage.setScene(new Scene(root, 1200, 800));
    }

    private void loadUserIncidents(ListView<String> view) {
        List<String> out = new ArrayList<>();
        for (Incident i : incidentManager.getActiveIncidents()) {
            out.add(i.getType() + " - " +
                    (i.getLocation() == null ? "N/A" : i.getLocation().getAddress()));
        }
        view.setItems(FXCollections.observableArrayList(out));
    }

    // -------------------------- CREATE INCIDENT --------------------------
    private void showCreateIncident(ListView<String> list) {
        Stage st = new Stage();
        st.initModality(Modality.APPLICATION_MODAL);
        st.setTitle("Report Incident");

        VBox root = new VBox(12);
        root.setPadding(new Insets(12));

        ComboBox<String> type = new ComboBox<>();
        type.getItems().addAll("Accident", "Breakdown", "Construction", "Weather Alert");
        type.setValue("Accident");

        TextField locField = new TextField();
        locField.setPromptText("Click map to choose location");

        Button pickLoc = new Button("Open Map");
        pickLoc.setOnAction(e -> {
            MapPicker picker = new MapPicker();
            picker.showMapPicker(locField);

            selectedLat = picker.getSelectedLat();
            selectedLng = picker.getSelectedLng();
            selectedAddress = picker.getSelectedAddress();
        });

        TextArea desc = new TextArea();
        desc.setPromptText("Description");

        HBox vehBox = new HBox(8);
        Label lv = new Label("Vehicles:");
        Spinner<Integer> spinVeh = new Spinner<>(0, 50, 1);
        vehBox.getChildren().addAll(lv, spinVeh);

        CheckBox injuries = new CheckBox("Injuries reported");

        ComboBox<RoadBlockageLevel> block = new ComboBox<>();
        block.getItems().addAll(RoadBlockageLevel.values());
        block.setValue(RoadBlockageLevel.NONE);

        ComboBox<Severity> sev = new ComboBox<>();
        sev.getItems().addAll(Severity.values());
        sev.setValue(Severity.MEDIUM);

        VBox svcBox = new VBox(5);
        Label svcLbl = new Label("Emergency services:");
        CheckBox amb = new CheckBox("Ambulance");
        CheckBox fire = new CheckBox("Fire");
        CheckBox police = new CheckBox("Police");
        CheckBox other = new CheckBox("Other:");
        TextField otherField = new TextField();

        svcBox.getChildren().addAll(svcLbl, amb, fire, police, other, otherField);

        Button submit = new Button("Submit");

        submit.setOnAction(e -> {
            int nextId = incidentManager.getAllIncidents().size() + 1;
            String d = desc.getText().trim();
            if (d.isEmpty()) d = type.getValue() + " reported";

            String typedAddr = locField.getText() == null ? "" : locField.getText().trim();
            String addr;
            if (!typedAddr.isEmpty()) {
                addr = typedAddr;
            } else {
                addr = (selectedAddress == null || selectedAddress.isBlank())
                        ? String.format("%.5f, %.5f", selectedLat, selectedLng)
                        : selectedAddress;
            }
            Location loc = new Location(selectedLat, selectedLng, addr);

            Incident inc = null;

            if (type.getValue().equals("Accident")) {
                List<String> needed = new ArrayList<>();
                if (amb.isSelected()) needed.add("Ambulance");
                if (fire.isSelected()) needed.add("Fire Brigade");
                if (police.isSelected()) needed.add("Police");
                if (other.isSelected() && !otherField.getText().trim().isEmpty())
                    needed.add(otherField.getText().trim());

                inc = new Accident(nextId, d, loc, sev.getValue(),
                        spinVeh.getValue(), injuries.isSelected(), needed, block.getValue());

            } else if (type.getValue().equals("Breakdown")) {
                inc = new Construction(nextId, d, loc, System.currentTimeMillis() + 2 * 60 * 60 * 1000);

            } else if (type.getValue().equals("Construction")) {
                inc = new Construction(nextId, d, loc, System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000);

            } else if (type.getValue().equals("Weather Alert")) {
                VisibilityInfo vis = WeatherService.getVisibilityInfo(loc.getLat(), loc.getLng());
                if (vis == null) vis = new VisibilityInfo(1000, 0, 0);
                inc = new WeatherAlert(nextId, d, loc, vis);
            }

            if (inc != null) {
                incidentManager.createIncident(inc);
                IncidentFileHandler.saveIncidents(incidentManager.getAllIncidents());
                loadUserIncidents(list);
                st.close();
            }
        });

        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(10);

        int r = 0;
        g.add(new Label("Type:"), 0, r);
        g.add(type, 1, r++);
        g.add(new Label("Location:"), 0, r);
        g.add(locField, 1, r++);
        g.add(pickLoc, 1, r++);
        g.add(new Label("Description:"), 0, r);
        g.add(desc, 1, r++);
        g.add(vehBox, 1, r++);
        g.add(injuries, 1, r++);
        g.add(block, 1, r++);
        g.add(sev, 1, r++);
        g.add(svcBox, 1, r++);
        g.add(submit, 1, r++);

        type.setOnAction(ev -> {
            boolean acc = type.getValue().equals("Accident");
            vehBox.setVisible(acc);
            injuries.setVisible(acc);
            block.setVisible(acc);
            sev.setVisible(acc);
            svcBox.setVisible(acc);
        });
        type.getOnAction().handle(null);

        root.getChildren().add(g);
        st.setScene(new Scene(root, 650, 700));
        st.show();
    }

    // -------------------------- MAP PICKER HELPER --------------------------
    public static class MapPicker {

        private double selectedLat;
        private double selectedLng;
        private String selectedAddress;

        public void showMapPicker(TextField locField) {

            Stage popup = new Stage();
            popup.setTitle("Pick Location");
            popup.initModality(Modality.APPLICATION_MODAL);

            VBox root = new VBox(10);
            root.setPadding(new Insets(10));

            WebView mapView = new WebView();
            mapView.setPrefSize(800, 550);

            Button selectBtn = new Button("Select Location");
            selectBtn.setDisable(true);

            WebEngine engine = mapView.getEngine();
            engine.loadContent(MapService.getMapHtml());

            mapView.setOnMouseClicked(e -> selectBtn.setDisable(false));

            selectBtn.setOnAction(e -> {
                double[] coords = MapService.getSelectedCoordinates(engine);
                if (coords != null) {
                    selectedLat = coords[0];
                    selectedLng = coords[1];
                    selectedAddress = "Selected location (" +
                            String.format("%.5f", selectedLat) + ", " +
                            String.format("%.5f", selectedLng) + ")";
                    locField.setText(selectedAddress);
                    popup.close();
                }
            });

            root.getChildren().addAll(mapView, selectBtn);

            Scene scene = new Scene(root, 820, 600);
            popup.setScene(scene);
            popup.showAndWait();
        }

        public double getSelectedLat() { return selectedLat; }
        public double getSelectedLng() { return selectedLng; }
        public String getSelectedAddress() { return selectedAddress; }
    }

    // -------------------------- UTILS --------------------------
    private void alert(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.showAndWait();
    }

    private void showLargeText(String title, String txt) {
        Stage st = new Stage();
        st.initModality(Modality.APPLICATION_MODAL);
        st.setTitle(title);
        TextArea t = new TextArea(txt);
        t.setWrapText(true);
        VBox root = new VBox(10, t);
        root.setPadding(new Insets(10));
        st.setScene(new Scene(root, 700, 600));
        st.show();
    }
}