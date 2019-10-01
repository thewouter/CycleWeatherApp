package nl.wouter.cycleweatherapp;

import android.app.Activity;
import android.app.IntentService;
import android.content.Intent;
import android.graphics.PointF;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

import static java.time.temporal.ChronoUnit.MINUTES;

public class WeatherCalculator extends IntentService {

    public static final String START_LON = "start_lon";
    public static final String END_LON = "end_lon";
    public static final String START_LAT = "start_lat";
    public static final String END_LAT = "end_lat";
    public static final String NOTIFICATION = "nl.wouter.cycleweatherapp";
    public static final String TIMES = "times";
    public static final String FORECAST = "forecast";
    public static final String RESULT = "result";


    private int result = Activity.RESULT_CANCELED;

    private static int SPEED = 20000/(60*60); // meters per second
    private static int MAX_MINUTES_FORECAST = 120;
    private static int FORECAST_INTERVAL = 5;
    private static int MILLISECONDS_SLEEP_FORECAST_API = 0;

    public WeatherCalculator() {
        super("WeatherCalculator");
    }

    protected void onHandleIntent(Intent intent) {
        double start_lon = intent.getDoubleExtra(START_LON,0);
        double start_lat = intent.getDoubleExtra(START_LAT,0);
        double end_lon = intent.getDoubleExtra(END_LON,0);
        double end_lat = intent.getDoubleExtra(END_LAT,0);
        HashMap<LocalTime, Double> combined = null;
        ArrayList<LocalTime> times = null;

        try {

            String route = getRoute(start_lat, start_lon, end_lat, end_lon);
            JSONObject routeJson = parseJson(route);
            JSONArray features = (JSONArray) routeJson.get("features");
            JSONArray coordinates = (JSONArray) ((JSONObject) ((JSONObject) features.get(0)).get("geometry")).get("coordinates");
            ArrayList<PointF> parsedCoordinates = new ArrayList<>();
            for (Object coordinate : coordinates) {
                double lat = (double) ((JSONArray) coordinate).get(1);
                double lon = (double) ((JSONArray) coordinate).get(0);
                parsedCoordinates.add(new PointF( (float) lat, (float) lon));
            }
            HashMap<LocalTime, PointF> filtered = filterRoute(parsedCoordinates);
            combined = getWeatherFromFilteredRoute(filtered);

            times = getFutureTimes();
            DecimalFormat df = new DecimalFormat("#.#");
            DecimalFormat df2 = new DecimalFormat("##.####");
            df.setRoundingMode(RoundingMode.CEILING);
            Set<LocalTime> forcastedTimes = combined.keySet();
            ArrayList<LocalTime> notForcasted = new ArrayList<>();
            for (LocalTime time : times) {
                PointF point = filtered.get(time);
                if (!forcastedTimes.contains(time)) {
                    notForcasted.add(time);
                    continue;
                }
                System.out.println(String.format("%s:  %s mm/h (%s, %s)", time, df.format(combined.get(time)), df2.format(point.x), df2.format(point.y)));
            }
            times.removeAll(notForcasted);
            result = Activity.RESULT_OK;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            publishResults(combined, times, result);
        }

    }

    private void publishResults(HashMap<LocalTime, Double> forecast, ArrayList<LocalTime> times, int result) {
        Intent intent = new Intent(NOTIFICATION);
        intent.putExtra(FORECAST, forecast);
        intent.putExtra(TIMES, times);
        intent.putExtra(RESULT, result);
        sendBroadcast(intent);
    }

    public static HashMap<LocalTime, Double> getWeatherFromFilteredRoute(HashMap<LocalTime, PointF> filtered) {
        HashMap<LocalTime, Double> combined = new HashMap<>();
        Collection<LocalTime> keys = filtered.keySet();

        for (LocalTime time: keys) {
            PointF coordinate = filtered.get(time);
            try {
                HashMap<LocalTime, Double> forecast = getRainfallData(coordinate.x, coordinate.y);
                if (forecast.get(time) != null){
                    combined.put(time, forecast.get(time));
                }
                Thread.sleep(MILLISECONDS_SLEEP_FORECAST_API);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
        return combined;
    }

    /**
     * Calculate distance between two points in latitude and longitude
     * Uses Haversine method as its base.
     *
     * lat1, lon1 Start point lat2, lon2 End point
     * @returns Distance in Meters
     */
    public static double distance(PointF start, PointF end) {
        double lat1 = start.x;
        double lon1 = start.y;
        double lat2 = end.x;
        double lon2 = end.y;

        final int R = 6371; // Radius of the earth

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c * 1000;
    }

    public static HashMap<LocalTime, PointF> filterRoute(ArrayList<PointF> coordinates){
        HashMap<LocalTime, PointF> filtered = new HashMap<>();
        ArrayList<LocalTime> times = getFutureTimes();
        System.out.println(times);
        LocalTime now = LocalTime.now();
        double totalTime = 0.0;
        PointF previous = null;
        for (PointF point: coordinates) {
            if (times.isEmpty()) {
                break;
            }
            if (previous == null) {
                previous = point;
            } else {
                totalTime += (distance(previous, point) / SPEED) / 60;
                if (MINUTES.between(times.get(0), now.plusMinutes((int) totalTime)) > 0 || Math.abs(MINUTES.between(times.get(0), now.plusMinutes((int) totalTime))) > MAX_MINUTES_FORECAST  ) {
                    LocalTime partial = times.get(0);
                    times.remove(0);
                    filtered.put(partial, point);
                }
                previous = point;
            }
        }
        return filtered;
    }

    public static ArrayList<LocalTime> getFutureTimes(){
        LocalTime now = LocalTime.now();
        int currentMinutes = now.getMinute();
        int currentHour = now.getHour();
        int upRoundedMinutes = (int) (5*(Math.floor(Math.abs(currentMinutes/5))));
        LocalTime startTime = LocalTime.of(currentHour, upRoundedMinutes,0).plusMinutes(FORECAST_INTERVAL);
        ArrayList<LocalTime> times = new ArrayList<>();
        times.add(startTime);
        for (int i = 0; i < MAX_MINUTES_FORECAST/FORECAST_INTERVAL - 1; i++) {
            startTime = startTime.plusMinutes(FORECAST_INTERVAL);
            times.add(startTime);
        }
        return times;
    }

    public static HashMap<LocalTime, Double> getRainfallData(double lat, double lon) throws IOException {
        URL url = new URL(String.format("https://gps.buienradar.nl/getrr.php?lon=%s&lat=%s", lon, lat));
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setDoOutput(true);
        HashMap<LocalTime, Double> rainfallData = new HashMap<>();

        try(BufferedReader br = new BufferedReader(
                new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            String line = "";
            while ((line = br.readLine()) != null) {
                String[] split = line.trim().split("\\|");
                LocalTime localTime = LocalTime.parse(split[1], DateTimeFormatter.ofPattern("HH:mm"));
                rainfallData.put(localTime, Math.pow(10,(((Double.valueOf(split[0]))-109.0)/32)));
            }
            return rainfallData;
        }
    }

    public static String getRoute(Double lon1, Double lat1, Double lon2, Double lat2) throws IOException {
        URL url = new URL(String.format("https://api.openrouteservice.org/v2/directions/cycling-regular?start=%s,%s&end=%s,%s", lat1, lon1, lat2, lon2));
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("Accept", "application/json, application/geo+json, application/gpx+xml, img/png; charset=utf-8");
        con.setRequestProperty("Authorization", "5b3ce3597851110001cf62482e8971e88f7c41448f3527bd42183b17");
        con.setDoOutput(true);


        try(BufferedReader br = new BufferedReader(
                new InputStreamReader(con.getInputStream(), "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String responseLine = null;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            return response.toString();
        }
    }

    public static JSONObject parseJson(String json) {
        JSONParser parser = new JSONParser();
        try {
            JSONObject array = (JSONObject) parser.parse(json);
            return array;
        } catch (org.json.simple.parser.ParseException e) {
            e.printStackTrace();
        }
        return null;
    }
}
