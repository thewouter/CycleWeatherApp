package nl.wouter.cycleweatherapp;

import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.View;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;


public class MainActivity extends AppCompatActivity {

    Location start;
    Location end;

    TextView log;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button start = findViewById(R.id.button2);
        Button end = findViewById(R.id.button3);

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent pickStartLocation = new Intent(v.getContext(), MapsActivity.class);
                startActivityForResult(pickStartLocation, 1);
            }
        });

        end.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent pickStartLocation = new Intent(v.getContext(), MapsActivity.class);
                startActivityForResult(pickStartLocation, 2);
            }
        });

        log = findViewById(R.id.log);




    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == 1) {
            if(resultCode == Activity.RESULT_OK){
                double lon = Double.valueOf(data.getStringExtra("lon"));
                double lat = Double.valueOf(data.getStringExtra("lat"));
                start = new Location("");
                start.setLatitude(lat);
                start.setLongitude(lon);
                log.setText(start.toString());
            }
        }
        if (requestCode == 2) {
            if(resultCode == Activity.RESULT_OK){
                double lon = Double.valueOf(data.getStringExtra("lon"));
                double lat = Double.valueOf(data.getStringExtra("lat"));
                end = new Location("");
                end.setLatitude(lat);
                end.setLongitude(lon);
                log.setText(end.toString());
            }
        }
    }

    static int SPEED = 20000/(60*60); // meters per second
    static int MAX_MINUTES_FORECAST = 120;
    static int FORECAST_INTERVAL = 5;
    static int MILLISECONDS_SLEEP_FORECAST_API = 0;

    public static void main(String[] args) {
        try {
            String route = getRoute(52.264840,6.803042,52.445256,6.400096);
            JSONObject routeJson = parseJson(route);
            JSONArray features = (JSONArray) routeJson.get("features");
            JSONArray coordinates = (JSONArray) ((JSONObject)((JSONObject)features.get(0)).get("geometry")).get("coordinates");
            double lat = 0.0, lon = 0.0;
            ArrayList<Point2D> parsedCoordinates = new ArrayList<>();
            for (Object coordinate: coordinates) {
                lat = (double) ((JSONArray) coordinate).get(1);
                lon = (double) ((JSONArray) coordinate).get(0);
                parsedCoordinates.add(new Point2D.Double(lat, lon));
            }
            HashMap<LocalTime, Point2D> filtered = filterRoute(parsedCoordinates);
            HashMap<LocalTime, Double> combined = getWeatherFromFilteredRoute(filtered);

            ArrayList<LocalTime> times = getFutureTimes();
            DecimalFormat df = new DecimalFormat("#.#");
            DecimalFormat df2 = new DecimalFormat("##.####");
            df.setRoundingMode(RoundingMode.CEILING);
            Set<LocalTime> forcastedTimes = combined.keySet();
            ArrayList<LocalTime> notForcasted = new ArrayList<>();
            for (LocalTime time: times) {
                Point2D point = filtered.get(time);
                if (!forcastedTimes.contains(time)) {
                    notForcasted.add(time);
                    continue;
                }
                System.out.println(String.format("%s:  %s mm/h (%s, %s)", time, df.format(combined.get(time)), df2.format(point.getX()), df2.format(point.getY())));
            }
            times.removeAll(notForcasted);

        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    public static HashMap<LocalTime, Double> getWeatherFromFilteredRoute(HashMap<LocalTime, Point2D> filtered) {
        HashMap<LocalTime, Double> combined = new HashMap<>();
        Collection<LocalTime> keys = filtered.keySet();

        for (LocalTime time: keys) {
            Point2D coordinate = filtered.get(time);
            try {
                HashMap<LocalTime, Double> forecast = getRainfallData(coordinate.getX(), coordinate.getY());
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
    public static double distance(Point2D start, Point2D end) {
        double lat1 = start.getX();
        double lon1 = start.getY();
        double lat2 = end.getX();
        double lon2 = end.getY();

        final int R = 6371; // Radius of the earth

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c * 1000;
    }

    public static HashMap<LocalTime, Point2D> filterRoute(ArrayList<Point2D> coordinates){
        HashMap<LocalTime, Point2D> filtered = new HashMap<>();
        ArrayList<LocalTime> times = getFutureTimes();
        System.out.println(times);
        LocalTime now = LocalTime.now();
        double totalTime = 0.0;
        Point2D previous = null;
        for (Point2D point: coordinates) {
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
        JSONObject array = null;
        try {
            array = (JSONObject) parser.parse(json);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return array;
    }

}
