package com.example.robert.sunshine;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Encapsulates fetching the forecast and displaying it as a {@link ListView} layout.
 */
public class ForecastFragment extends Fragment {

    public ArrayAdapter<String> mForecastAdapter;

    public ForecastFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Create some dummy data for the ListView.  Here's a sample weekly forecast
        String[] data = {getString(R.string.fetching_weather_data)};
        List<String> weekForecast = new ArrayList<String>(Arrays.asList(data));

        // Now that we have some dummy forecast data, create an ArrayAdapter.
        // The ArrayAdapter will take data from a source (like our dummy forecast) and
        // use it to populate the ListView it's attached to.
        mForecastAdapter =
                new ArrayAdapter<String>(
                        getActivity(), // The current context (this activity)
                        R.layout.list_item_forecast, // The name of the layout ID.
                        R.id.list_item_forecast_textview, // The ID of the textview to populate.
                        weekForecast);

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        // Get a reference to the ListView, and attach this adapter to it.
        final ListView listView = (ListView) rootView.findViewById(R.id.listview_forecast);
        listView.setAdapter(mForecastAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String text = mForecastAdapter.getItem(position);
                Intent detailIntent = new Intent(getActivity(), DetailActivity.class);
                detailIntent.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(detailIntent);

            }
        });

        return rootView;
    }

    @Override
    public void onStart(){
        super.onStart();
        updateWeather();
    }

    public class FetchWeatherTask extends AsyncTask<String, Void, String[]> {

        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();
        final String BASE_FORECAST_URL = "http://api.openweathermap.org/data/2.5/forecast/daily";
        final String QUERY_PARAM = "q";
        final String MODE_PARAM = "mode";
        final String UNITS_PARAM = "units";
        final String COUNT_PARAM = "cnt";

        final String NUM_DAYS = "7";

        @Override
        protected String[] doInBackground(String... params) {

            if (params.length < 2){
                return null;
            }
            String postalCode = params[0];
            String units = params[1];
            // These two need to be declared outside the try/catch
            // so that they can be closed in the finally block.
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            // Will contain the raw JSON response as a string.
            String forecastJsonStr = null;
            String forecastArray[] = null;

            try {
                // Construct the URL for the OpenWeatherMap query
                // Possible parameters are avaiable at OWM's forecast API page, at
                // http://openweathermap.org/API#forecast
                Uri forecast_url = Uri.parse(BASE_FORECAST_URL).buildUpon()
                        .appendQueryParameter(QUERY_PARAM, postalCode)
                        .appendQueryParameter(MODE_PARAM, "json")
                        .appendQueryParameter(UNITS_PARAM, units)
                        .appendQueryParameter(COUNT_PARAM, NUM_DAYS)
                        .build();

                URL url = new URL(forecast_url.toString());

                // Create the request to OpenWeatherMap, and open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                // Read the input stream into a String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    // Nothing to do.
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                    // But it does make debugging a *lot* easier if you print out the completed
                    // buffer for debugging.
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing.
                    return null;
                }
                forecastJsonStr = buffer.toString();





            } catch (IOException e) {
                Log.e(LOG_TAG, "Error ", e);
                // If the code didn't successfully get the weather data, there's no point in attemping
                // to parse it.
                return null;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }
            try {
                return getWeekForecasts(forecastJsonStr);
            } catch (JSONException e) {
                Log.e(LOG_TAG, "Error: ", e);
                return null;
            }

        }

        protected void onPostExecute(String[] data){
            if(data != null) {
                mForecastAdapter.clear();
                for (String forecast : data) {
                    mForecastAdapter.add(forecast);
                }
            }
       }

        protected String[] getWeekForecasts(String jsonStr) throws JSONException{
            final String OWM_LIST = "list";
            final String OWM_TEMP = "temp";
            final String OWM_MAX = "max";
            final String OWM_MIN = "min";
            final String OWM_WEATHER = "weather";
            final String OWM_MAIN = "main";


            int numDays = Integer.parseInt(NUM_DAYS);
            String[] forecastArray = new String[numDays];
            JSONObject weatherJSON = new JSONObject(jsonStr);
            JSONArray weatherArray = weatherJSON.getJSONArray(OWM_LIST);

            Locale locale = Locale.getDefault();
            Calendar calendar = Calendar.getInstance(locale);
            calendar.setTimeInMillis(System.currentTimeMillis());

            for(int dayIndex = 0; dayIndex < numDays; dayIndex++){
                int dayMin;
                int dayMax;
                String dayDate;
                String dayDesc;

                JSONObject dayForecastJSON = weatherArray.getJSONObject(dayIndex);
                JSONObject dayTemp = dayForecastJSON.getJSONObject(OWM_TEMP);
                dayMax = (int)Math.round(dayTemp.getDouble(OWM_MAX));
                dayMin = (int)Math.round(dayTemp.getDouble(OWM_MIN));

                JSONObject dayWeather = dayForecastJSON.getJSONArray(OWM_WEATHER)
                        .getJSONObject(0);
                dayDesc = dayWeather.getString(OWM_MAIN);

                if (dayIndex == 0) {
                    dayDate = "Today";
                } else if (dayIndex == 1) {
                    dayDate = "Tomorrow";
                } else {
                    dayDate = String.format("%s %s %s",
                            calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, locale),
                            calendar.getDisplayName(Calendar.MONTH, Calendar.SHORT, locale),
                            calendar.get(Calendar.DAY_OF_MONTH));
                }
                calendar.add(Calendar.DATE, 1);

                forecastArray[dayIndex] = String.format("%s - %s - %d/%d",
                        dayDate,
                        dayDesc,
                        dayMax,
                        dayMin);
            }
            return forecastArray;
        }
    }

    private void updateWeather(){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String location = prefs.getString(getString(R.string.pref_location_key), getString(R.string.pref_location_default));
        String units = prefs.getString(getString(R.string.pref_units_key), getString(R.string.pref_units_default));
        new FetchWeatherTask().execute(location, units);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.forecastfragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_refresh) {
            updateWeather();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
