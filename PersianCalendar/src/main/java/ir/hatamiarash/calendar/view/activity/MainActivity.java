package ir.hatamiarash.calendar.view.activity;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import calendar.PersianDate;
import ir.hatamiarash.calendar.Constants;
import ir.hatamiarash.calendar.R;
import ir.hatamiarash.calendar.adapter.DrawerAdapter;
import ir.hatamiarash.calendar.entity.EventEntity;
import ir.hatamiarash.calendar.mine.AppController;
import ir.hatamiarash.calendar.service.ApplicationService;
import ir.hatamiarash.calendar.util.UpdateUtils;
import ir.hatamiarash.calendar.util.Utils;
import ir.hatamiarash.calendar.view.fragment.AboutFragment;
import ir.hatamiarash.calendar.view.fragment.ApplicationPreferenceFragment;
import ir.hatamiarash.calendar.view.fragment.CalendarFragment;
import ir.hatamiarash.calendar.view.fragment.CompassFragment;
import ir.hatamiarash.calendar.view.fragment.ConverterFragment;

public class MainActivity extends AppCompatActivity {

    private final String TAG = MainActivity.class.getName();
    private Utils utils;
    private UpdateUtils updateUtils;

    private DrawerLayout drawerLayout;
    private DrawerAdapter adapter;

    private Class<?>[] fragments = {
            null,
            CalendarFragment.class,
            ConverterFragment.class,
            CompassFragment.class,
            ApplicationPreferenceFragment.class,
            AboutFragment.class
    };

    private static final int CALENDAR = 1;
    private static final int CONVERTER = 2;
    private static final int COMPASS = 3;
    private static final int PREFERENCE = 4;
    private static final int ABOUT = 5;
    private static final int EXIT = 6;

    // Default selected fragment
    private static final int DEFAULT = CALENDAR;

    private int menuPosition = 0; // it should be zero otherwise #selectItem won't be called

    private String lastLocale;
    private String lastTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        utils = Utils.getInstance(getApplicationContext());
        utils.setTheme(this);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        utils.changeAppLanguage(this);
        utils.loadLanguageResource();
        lastLocale = utils.getAppLanguage();
        lastTheme = utils.getTheme();
        updateUtils = UpdateUtils.getInstance(getApplicationContext());

        if (!Utils.getInstance(this).isServiceRunning(ApplicationService.class))
            startService(new Intent(getBaseContext(), ApplicationService.class));

        updateUtils.update(true);
        CustomEvents();

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window w = getWindow();
            w.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

        } else
            toolbar.setPadding(0, 0, 0, 0);

        RecyclerView navigation = (RecyclerView) findViewById(R.id.navigation_view);
        navigation.setHasFixedSize(true);
        adapter = new DrawerAdapter(this);
        navigation.setAdapter(adapter);

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        navigation.setLayoutManager(layoutManager);

        drawerLayout = (DrawerLayout) findViewById(R.id.drawer);
        final View appMainView = findViewById(R.id.app_main_layout);
        ActionBarDrawerToggle drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.openDrawer, R.string.closeDrawer) {
            int slidingDirection = +1;

            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
                    if (isRTL())
                        slidingDirection = -1;
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
            }

            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                super.onDrawerSlide(drawerView, slideOffset);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    slidingAnimation(drawerView, slideOffset);
                }
            }

            @TargetApi(Build.VERSION_CODES.HONEYCOMB)
            private void slidingAnimation(View drawerView, float slideOffset) {
                appMainView.setTranslationX(slideOffset * drawerView.getWidth() * slidingDirection);
                drawerLayout.bringChildToFront(drawerView);
                drawerLayout.requestLayout();
            }
        };

        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        selectItem(DEFAULT);

        LocalBroadcastManager.getInstance(this).registerReceiver(dayPassedReceiver,
                new IntentFilter(Constants.LOCAL_INTENT_DAY_PASSED));
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private boolean isRTL() {
        return getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        utils.changeAppLanguage(this);
        View v = findViewById(R.id.drawer);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            v.setLayoutDirection(isRTL() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    }

    public boolean dayIsPassed = false;

    private BroadcastReceiver dayPassedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            dayIsPassed = true;
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        if (dayIsPassed) {
            dayIsPassed = false;
            restartActivity();
        }
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(dayPassedReceiver);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START))
            drawerLayout.closeDrawers();
        else if (menuPosition != DEFAULT)
            selectItem(DEFAULT);
        else
            finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Checking for the "menu" key
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (drawerLayout.isDrawerOpen(GravityCompat.START))
                drawerLayout.closeDrawers();
            else
                drawerLayout.openDrawer(GravityCompat.START);
            return true;
        } else
            return super.onKeyDown(keyCode, event);
    }

    private void beforeMenuChange(int position) {
        if (position != menuPosition)
            // reset app lang on menu changes, ugly hack but it seems is needed
            utils.changeAppLanguage(this);

        // only if we are returning from preferences
        if (menuPosition != PREFERENCE)
            return;

        utils.updateStoredPreference();
        updateUtils.update(true);

        boolean needsActivityRestart = false;

        String locale = utils.getAppLanguage();
        if (!locale.equals(lastLocale)) {
            lastLocale = locale;
            utils.changeAppLanguage(this);
            utils.loadLanguageResource();
            needsActivityRestart = true;
        }

        if (!lastTheme.equals(utils.getTheme())) {
            needsActivityRestart = true;
            lastTheme = utils.getTheme();
        }

        if (needsActivityRestart)
            restartActivity();
    }

    private void restartActivity() {
        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }

    public void selectItem(int item) {
        if (item == EXIT) {
            finish();
            return;
        }

        beforeMenuChange(item);
        if (menuPosition != item)
            try {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(
                                R.id.fragment_holder,
                                (Fragment) fragments[item].newInstance(),
                                fragments[item].getName()
                        ).commit();
                menuPosition = item;
            } catch (Exception e) {
                Log.e(TAG, item + " is selected as an index", e);
            }

        adapter.setSelectedItem(menuPosition);

        drawerLayout.closeDrawers();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == Constants.LOCATION_PERMISSION_REQUEST_CODE)
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                    new Intent(Constants.LOCATION_PERMISSION_RESULT));
    }

    // Get events from server
    private void CustomEvents() {
        String string_req = "req_fetch";
        StringRequest strReq = new StringRequest(Request.Method.POST, "http://cl.zimia.ir/client.php", new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                Log.w(TAG, "Volley Response: " + response);
                try {
                    JSONObject jObj = new JSONObject(response);
                    boolean error = jObj.getBoolean("error");
                    if (!error)
                        UpdateEvents(jObj.getJSONArray("event"));
                    else
                        Log.w("Error", jObj.getString("error_msg"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.w(TAG, "Volley Error" + error.getMessage());
            }
        }) {
            @Override
            protected java.util.Map<String, String> getParams() {
                java.util.Map<String, String> params = new HashMap<>();
                params.put("tag", "get_events");
                return params;
            }
        };
        AppController.getInstance().addToRequestQueue(strReq, string_req);
    }

    // update events with given JSONArray
    private void UpdateEvents(JSONArray events) {
        int length = events.length();
        List<EventEntity> new_events = new ArrayList<>();
        try {
            for (int i = 0; i < length; ++i) {
                JSONObject event = events.getJSONObject(i);
                int year = event.getInt("year");
                int month = event.getInt("month");
                int day = event.getInt("day");
                String title = event.getString("title");
                String type = event.getString("type");
                int h = event.getInt("holiday");
                boolean holiday = h != 0;
                if (!Utils.EventExists(title))
                    new_events.add(new EventEntity(new PersianDate(year, month, day), title, holiday, type));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        List<EventEntity> current_events = Utils.GetEvents();

        for (EventEntity event : current_events)
            new_events.add(event);

        Utils.UpdateEvents(new_events);
    }
}