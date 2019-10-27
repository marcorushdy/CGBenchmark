package fr.svivien.cgbenchmark;

import com.google.gson.Gson;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class HTMLReportGenerator {

    private static final Log LOG = LogFactory.getLog(HTMLReportGenerator.class);

    private static final Pattern CODE_NAME_PATTERN = Pattern.compile("(.*)-\\d+\\.\\d+(_\\d+)*\\.txt");
    private static final Pattern ENEMY_LIST_PATTERN = Pattern.compile("\\s+([\\w\\]\\[]+_\\d+)+");
    private static final NumberFormat LOCAL_DOUBLE_FORMAT = NumberFormat.getInstance(Locale.getDefault());
    private static final String[] COLORS = {"#000000", "#FF0000", "#00FF00", "#0000FF", "#ABAB57", "#00FFFF", "#FF00FF", "#C0C0C0", "#808080", "#800000", "#808000", "#008000", "#800080", "#008080", "#000080"};
    private static final int MAX_POINT_RADIUS = 35;

    // ==============================================

    static class Encounter {
        int totalPlayedGames = 0;
        double totalWonGames = 0.0;

        public Encounter() {
        }

        public Encounter(int totalPlayedGames, double gwinrate) {
            this.totalPlayedGames = totalPlayedGames;
            this.totalWonGames = Math.round(2 * ((gwinrate / 100.0) * totalPlayedGames)) / 2.0;
        }

        void addResult(int playedGames, double wonGames) {
            totalPlayedGames += playedGames;
            totalWonGames += wonGames;
        }

        double getGWRPercentage() {
            return 100.0 * (totalWonGames / ((double) totalPlayedGames));
        }
    }

    static class CodeStats {
        Map<String, Encounter> encounters = new HashMap<>();

        void addResult(String enemyName, Encounter e) {
            if (encounters.get(enemyName) == null) encounters.put(enemyName, e);
            else encounters.get(enemyName).addResult(e.totalPlayedGames, e.totalWonGames);
        }

        void addResult(String enemyName, int playedGames, double wonGames) {
            if (encounters.get(enemyName) == null) encounters.put(enemyName, new Encounter());
            encounters.get(enemyName).addResult(playedGames, wonGames);
        }
    }

    static class CodesStats {
        Map<String, CodeStats> stats = new HashMap<>();

        void addResult(String codeName, String enemyName, Encounter e) {
            if (stats.get(codeName) == null) stats.put(codeName, new CodeStats());
            stats.get(codeName).addResult(enemyName, e);
        }

        void addResult(String codeName, String enemyName, int playedGames, double wonGames) {
            if (stats.get(codeName) == null) stats.put(codeName, new CodeStats());
            stats.get(codeName).addResult(enemyName, playedGames, wonGames);
        }
    }

    static class Dataset {
        private boolean fill = false;
        private String pointBackgroundColor = "rgba(0, 0, 0, 0.1)";
        String label;
        List<Double> data = new ArrayList<>();
        String borderColor;
        String backgroundColor;
        List<Integer> pointRadius = new ArrayList<>(); // 1 à 35
        List<Integer> pointHoverRadius = new ArrayList<>();
    }

    // ==============================================

    public static void generateHTMLReport() {
        // Pour chaque code, chaque ennemi rencontré, avec pour chacun d'entre eux, le nombre total de games jouées et le GWinrate

        CodesStats stats = extractStats();

        SortedSet<String> orderedEnemies = new TreeSet<>();
        int maxNumberOfGames = 0;
        for (Map.Entry<String, CodeStats> e : stats.stats.entrySet()) {
            for (Map.Entry<String, Encounter> ee : e.getValue().encounters.entrySet()) {
                orderedEnemies.add(ee.getKey());
                maxNumberOfGames = Math.max(ee.getValue().totalPlayedGames, maxNumberOfGames);
            }
        }

        List<Dataset> dataSets = new ArrayList<>();
        int colorIdx = 0;
        for (Map.Entry<String, CodeStats> e : stats.stats.entrySet()) {
            Dataset dataset = new Dataset();

            dataset.label = e.getKey();
            dataset.borderColor = COLORS[colorIdx];
            dataset.backgroundColor = COLORS[colorIdx];

            for (String enemy : orderedEnemies) {
                Encounter encounter = e.getValue().encounters.get(enemy);
                if (encounter != null && encounter.totalPlayedGames > 0) {
                    dataset.data.add(encounter.getGWRPercentage());
                    int pointRadius = (int) (1.0 + ((double) (MAX_POINT_RADIUS - 1) * (1.0 - ((double) encounter.totalPlayedGames / (double) maxNumberOfGames))));
                    dataset.pointRadius.add(pointRadius);
                    dataset.pointHoverRadius.add(pointRadius);
                } else {
                    dataset.data.add(null);
                    dataset.pointRadius.add(0);
                    dataset.pointHoverRadius.add(0);
                }
            }

            dataSets.add(dataset);
            colorIdx = (colorIdx + 1) % COLORS.length;
        }

        Gson gson = new Gson();

        final StringBuilder reportContent = new StringBuilder();
        Scanner sc = new Scanner(HTMLReportGenerator.class.getResourceAsStream("/stats.report.template.html"));

        while (sc.hasNext()) {
            String l = sc.nextLine();
            if (l.contains("##LABEL_LIST##")) {
                l = l.replace("##LABEL_LIST##", gson.toJson(orderedEnemies));
            } else if (l.contains("##DATASET_LIST##")) {
                l = l.replace("##DATASET_LIST##", gson.toJson(dataSets));
            } else if (l.contains("##EXTRA_INFO##")) {
                l = l.replace("##EXTRA_INFO##", generateHTMLTable(stats, orderedEnemies));
            }

            reportContent.append(l + System.lineSeparator());
        }

        writeReport(reportContent);
    }

    private static String generateHTMLTable(CodesStats stats, SortedSet<String> orderedEnemies) {
        StringBuilder sb = new StringBuilder();

        sb.append("<table>");
        sb.append("<tr>");
        sb.append("<th> </th>");
        for (String enemy : orderedEnemies) {
            sb.append("<th>" + enemy + "</th>");
        }
        sb.append("</tr>");

        for (Map.Entry<String, CodeStats> e : stats.stats.entrySet()) {
            sb.append("<tr>");
            sb.append("<td>" + e.getKey() + "</td>");
            for (String enemy : orderedEnemies) {
                Encounter encounter = e.getValue().encounters.get(enemy);
                if (encounter != null && encounter.totalPlayedGames > 0) {
                    sb.append("<td>" + encounter.totalPlayedGames + "</td>");
                } else {
                    sb.append("<td>0</td>");
                }
            }
            sb.append("</tr>");
        }

        sb.append("</table>");
        return sb.toString();
    }

    private static void writeReport(StringBuilder reportContent) {
        // Write report to external file
        String reportFileName = "statistics";

        // Add suffix to avoid overwriting existing report file
        File file = new File(reportFileName + ".html");
        if (file.exists() && !file.isDirectory()) {
            int suffix = -1;
            do {
                suffix++;
                file = new File(reportFileName + "_" + suffix + ".html");
            } while (file.exists() && !file.isDirectory());
            reportFileName += "_" + suffix;
        }
        reportFileName += ".html";

        LOG.info("Writing final report to : " + reportFileName);
        try (PrintWriter out = new PrintWriter(reportFileName)) {
            out.println(reportContent.toString());
        } catch (Exception e) {
            LOG.warn("An error has occurred when writing final report", e);
        }
    }

    private static CodesStats extractStats() {
        long start = System.currentTimeMillis();
        CodesStats stats = new CodesStats();
        try {
            Files.list(Paths.get(".")).filter(p -> !p.toFile().isDirectory() && isCGBTxtReport(p.getFileName())).forEach(p -> {
                List<String> enemyList = extractEnemyList(p);
                String codeName = extratCodeName(p.getFileName());
                for (String enemy : enemyList) {
                    Encounter encounter = extractEncounter(p, enemy);
                    if (encounter != null) stats.addResult(codeName, enemy, encounter);
                }
            });
        } catch (IOException e) {
            LOG.error(e);
        }
        System.out.println("---- Stats parsing took " + (System.currentTimeMillis() - start) + " ms");
        return stats;
    }

    private static String extratCodeName(Path reportPath) {
        Matcher matcher = CODE_NAME_PATTERN.matcher(reportPath.toString());
        matcher.find();
        return matcher.group(1);
    }

    private static Encounter extractEncounter(Path reportPath, String enemyName) {
        String enemyNick = enemyName.substring(0,enemyName.lastIndexOf("_")).replace("[", "\\[").replace("]", "\\]");
        Pattern pattern = Pattern.compile("\\s*" + enemyNick + "\\s*GW=([0-9,.]+)%.*\\[(\\d+)\\]");

        try (Stream<String> stream = Files.lines(reportPath)) {
            return stream
                    .filter(line -> {
                        Matcher matcher = pattern.matcher(line);
                        return matcher.find();
                    })
                    .map(line -> {
                        Matcher matcher = pattern.matcher(line);
                        matcher.find();

                        Number number = null;
                        try {
                            number = LOCAL_DOUBLE_FORMAT.parse(matcher.group(1));
                        } catch (ParseException e) {
                            LOG.error(e);
                        }
                        double d = number.doubleValue();

                        return new Encounter(Integer.valueOf(matcher.group(2)), d);
                    }).sorted((a, b) -> b.totalPlayedGames - a.totalPlayedGames).findFirst().get();
        } catch (Exception e) {
            LOG.error(e);
        }
        return null;
    }

    private static List<String> extractEnemyList(Path reportPath) {
        List<String> enemyList = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(reportPath.toFile()))) {
            String line = br.readLine();
            Matcher matcher = ENEMY_LIST_PATTERN.matcher(line);
            while (matcher.find()) {
                enemyList.add(matcher.group(1));
            }
        } catch (IOException e) {
            LOG.error(e);
        }
        return enemyList;
    }

    private static boolean isCGBTxtReport(Path path) {
        if (path.toString().endsWith(".txt")) {
            try (BufferedReader br = new BufferedReader(new FileReader(path.toFile()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if ((line.contains("Testing") || line.contains("Launching")) && line.contains("against")) return true;
                }
            } catch (IOException e) {
                LOG.error(e);
            }
        }
        return false;
    }

}
