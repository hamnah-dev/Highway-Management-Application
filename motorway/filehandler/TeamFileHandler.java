package com.motorway.filehandler;

import com.motorway.model.Team;

import java.util.ArrayList;
import java.util.List;

public class TeamFileHandler {
    private static final String TEAMS_FILE = "teams.dat";

    public static void saveTeams(List<Team> teams) {
        FileManager.saveToFile(new ArrayList<>(teams), TEAMS_FILE);
    }

    @SuppressWarnings("unchecked")
    public static List<Team> loadTeams() {
        Object obj = FileManager.loadFromFile(TEAMS_FILE);
        if (obj instanceof List) {
            return (List<Team>) obj;
        }
        return new ArrayList<>();
    }

    public static boolean hasExistingData() {
        return FileManager.fileExists(TEAMS_FILE);
    }
}