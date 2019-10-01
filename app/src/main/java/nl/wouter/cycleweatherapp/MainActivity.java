package nl.wouter.cycleweatherapp;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;


public class MainActivity extends AppCompatActivity {

    Location start;
    Location end;

    TextView log;

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                HashMap<LocalTime, Double> forecast = (HashMap<LocalTime, Double>) bundle.getSerializable(WeatherCalculator.FORECAST);
                ArrayList<LocalTime> times = (ArrayList<LocalTime>) bundle.getSerializable(WeatherCalculator.TIMES);
                int resultCode =  bundle.getInt(WeatherCalculator.RESULT);
                if (resultCode == RESULT_OK) {
                    Toast.makeText(MainActivity.this,
                            "Forecast fetched",
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this,
                            "Forecast failed",
                            Toast.LENGTH_LONG).show();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button start = findViewById(R.id.button2);
        Button end = findViewById(R.id.button3);
        Button calculate = findViewById(R.id.button4);

        start.setOnClickListener(v -> {
            Intent pickStartLocation = new Intent(v.getContext(), MapsActivity.class);
            startActivityForResult(pickStartLocation, 1);
        });

        end.setOnClickListener(v -> {
            Intent pickStartLocation = new Intent(v.getContext(), MapsActivity.class);
            startActivityForResult(pickStartLocation, 2);
        });

        calculate.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, WeatherCalculator.class);
            intent.putExtra(WeatherCalculator.START_LAT, start.getX());
            intent.putExtra(WeatherCalculator.START_LON, start.getY());
            intent.putExtra(WeatherCalculator.END_LAT, end.getX());
            intent.putExtra(WeatherCalculator.END_LON, end.getY());
            startService(intent);
            Toast.makeText(this, "Fetching forecast", Toast.LENGTH_SHORT).show();
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
}
