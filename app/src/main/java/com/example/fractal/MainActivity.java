package com.example.fractal;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import androidx.navigation.NavGraph;
import android.widget.ImageView;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import androidx.navigation.NavOptions;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.fractal.databinding.ActivityMainBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    private static final String PREFS_NAME       = "fractal_device_prefs";
    private static final String KEY_LAST_SYNCED  = "last_synced_email_";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.core.splashscreen.SplashScreen splashScreen =
                androidx.core.splashscreen.SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowInsetsControllerCompat windowController = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        windowController.setAppearanceLightStatusBars(true);

        splashScreen.setKeepOnScreenCondition(() -> {
            FractalApplication app = (FractalApplication) getApplication();
            return app.globalState == null;
        });

        setupNavigationUI();

        Looper.myQueue().addIdleHandler(() -> {
            initFirebaseRemoteConfig();
            startHardwareSyncThread();

            AppGlobal.app_config config = ((FractalApplication) getApplicationContext()).getGlobalState().getAppConfig();
            if (config != null && config.getOverNightUtilization()) {
                android.util.Log.i("MainActivity", "Overnight is ON. Starting Supervisor Service.");
                android.content.Intent serviceIntent = new android.content.Intent(MainActivity.this, OvernightManagerService.class);
                androidx.core.content.ContextCompat.startForegroundService(MainActivity.this, serviceIntent);
            }

            return false;
        });
    }

    private void initFirebaseRemoteConfig() {
        FirebaseRemoteConfig mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600)
                .build();
        mFirebaseRemoteConfig.setConfigSettingsAsync(configSettings);

        Map<String, Object> defaults = new HashMap<>();
        defaults.put("server_protocol", "https");
        defaults.put("server_ip", "api.fractalgrid.dpdns.org");
        defaults.put("server_port", "");

        mFirebaseRemoteConfig.setDefaultsAsync(defaults);
        mFirebaseRemoteConfig.fetchAndActivate().addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                android.util.Log.i("RemoteConfig", "Cloud Server URLs updated successfully!");
            }
        });
    }

    private void startHardwareSyncThread() {
        Context appContext = getApplicationContext();

        Thread registeredInfoSender = new Thread(() -> {
            AppBackend.Network.RegisteredInfo.RegistrationManager regManager =
                    new AppBackend.Network.RegisteredInfo.RegistrationManager(appContext);

            AppBackend.Network.RegisteredInfo.Registered_DTO dto = regManager.generateNewRegistrationData();
            com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();

            int waited = 0;
            while (auth.getCurrentUser() == null && waited < 5) {
                try { Thread.sleep(1000); } catch (InterruptedException ie) { return; }
                waited++;
            }

            com.google.firebase.auth.FirebaseUser currentUser = auth.getCurrentUser();
            if (currentUser != null) {
                try { currentUser.reload(); } catch (Exception ignored) {}
                dto.setUsername(currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty() ? currentUser.getDisplayName() : "Authorized User");
                dto.setEmail(currentUser.getEmail() != null ? currentUser.getEmail() : "Unknown Email");
                dto.setJoinedOn(currentUser.getMetadata() != null ? new java.text.SimpleDateFormat("dd MMM, yyyy", java.util.Locale.getDefault()).format(new java.util.Date(currentUser.getMetadata().getCreationTimestamp())) : "N/A");
                dto.setStatus("registered");

                try {
                    com.google.firebase.firestore.FirebaseFirestore firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance();
                    com.google.firebase.firestore.DocumentSnapshot userDoc = com.google.android.gms.tasks.Tasks.await(firestore.collection("users").document(dto.getEmail()).get());
                    if (userDoc.exists()) {
                        dto.setPhoneNumber(userDoc.getString("phone") != null ? userDoc.getString("phone") : "Unknown");
                        dto.setCarrier(userDoc.getString("carrier") != null ? userDoc.getString("carrier") : "Unknown");
                    }
                } catch (Exception ignored) {}
            } else {
                dto.setUsername("Unregistered Device");
                dto.setEmail("Not Authenticated");
                dto.setJoinedOn("N/A");
                dto.setStatus("not_registered");
            }

            SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String syncKey = KEY_LAST_SYNCED + dto.getHardwareID();
            String lastSyncedEmail = prefs.getString(syncKey, "");

            if (lastSyncedEmail.isEmpty() || !lastSyncedEmail.equals(dto.getEmail())) {
                AppBackend.Network.Server_DAO.Server_DAO dao = new AppBackend.Network.Server_DAO.Server_DAO();
                boolean sent = false;
                while (!sent) {
                    try {
                        sent = dao.POST_SendRegisteredInfo(dto);
                        if (sent) prefs.edit().putString(syncKey, dto.getEmail()).apply();
                        else Thread.sleep(10_000);
                    } catch (Exception e) {
                        try { Thread.sleep(10_000); } catch (InterruptedException ie2) { break; }
                    }
                }
            }
        });
        registeredInfoSender.setDaemon(true);
        registeredInfoSender.start();
    }

    private void setupNavigationUI() {
        DrawerLayout drawerLayout        = findViewById(R.id.drawer_layout);
        View customHeader                = findViewById(R.id.custom_header);
        ImageButton headerDrawerButton   = findViewById(R.id.header_drawer);
        BottomNavigationView navView     = findViewById(R.id.nav_view);
        ImageView borderLine             = findViewById(R.id.imageView);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_activity_main);
        if (navHostFragment == null) return;
        NavController navController = navHostFragment.getNavController();

        NavGraph navGraph = navController.getNavInflater().inflate(R.navigation.mobile_navigation);

        SharedPreferences runPrefs = getSharedPreferences("app_run_stats", MODE_PRIVATE);
        boolean isFirstRun = runPrefs.getBoolean("is_first_run", true);

        if (isFirstRun) {
            navGraph.setStartDestination(R.id.navigation_get_started);
        } else {
            navGraph.setStartDestination(R.id.navigation_home);
        }

        navController.setGraph(navGraph);

        if (navView != null) {
            navView.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                int currentDest = navController.getCurrentDestination() != null ? navController.getCurrentDestination().getId() : -1;

                // ── THE FIX: Disabled Save & Restore State ──
                // This forces the tabs to perform a clean refresh and clears "ghost" screens like Settings
                NavOptions navOptions = new NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .setRestoreState(false) // Changed to false
                        .setPopUpTo(navController.getGraph().getStartDestinationId(), false, false) // Changed saveState to false
                        .build();

                if (itemId == R.id.navigation_home && currentDest != R.id.navigation_home) {
                    navController.navigate(R.id.navigation_home, null, navOptions);
                } else if (itemId == R.id.navigation_device) {
                    if (currentDest != R.id.navigation_device) navController.navigate(R.id.navigation_device, null, navOptions);
                    Bundle result = new Bundle();
                    result.putInt("page", 0);
                    getSupportFragmentManager().setFragmentResult("tab_change", result);
                } else if (itemId == R.id.navigation_model && currentDest != R.id.navigation_reward_bank) {
                    navController.navigate(R.id.navigation_reward_bank, null, navOptions);
                }
                navView.getMenu().findItem(itemId).setChecked(true);
                navView.post(() -> animateNavIcons(navView, itemId));
                return true;
            });
            getSupportFragmentManager().setFragmentResultListener("pager_swiped", this, (requestKey, bundle) -> {
                if (bundle.getInt("page") == 0) {
                    navView.getMenu().findItem(R.id.navigation_device).setChecked(true);
                    navView.post(() -> animateNavIcons(navView, R.id.navigation_device));
                }
            });
        }

        if (headerDrawerButton != null && drawerLayout != null) {
            headerDrawerButton.setOnClickListener(v -> {
                if (!drawerLayout.isDrawerOpen(GravityCompat.START)) drawerLayout.openDrawer(GravityCompat.START);
            });
        }

        View customSidebar = findViewById(R.id.custom_sidebar);
        if (customSidebar != null && drawerLayout != null) {
            ImageButton sidebarCloseBtn = customSidebar.findViewById(R.id.sidebar_close_btn);
            if (sidebarCloseBtn != null) sidebarCloseBtn.setOnClickListener(v -> drawerLayout.closeDrawer(GravityCompat.START));

            setupSidebarNav(customSidebar, R.id.nav_item_settings, R.id.navigation_settings, drawerLayout, navController);
            setupSidebarNav(customSidebar, R.id.nav_item_device_insights, R.id.nav_device_insights, drawerLayout, navController);
            setupSidebarNav(customSidebar, R.id.nav_item_about, R.id.navigation_about, drawerLayout, navController);
            setupSidebarNav(customSidebar, R.id.nav_item_reg_device, R.id.navigation_device_auth, drawerLayout, navController);
            setupSidebarNav(customSidebar, R.id.nav_item_unreg_device, R.id.navigation_device_unregister, drawerLayout, navController);
            setupSidebarNav(customSidebar, R.id.nav_item_reg_info, R.id.navigation_registered_info, drawerLayout, navController);
            setupSidebarNav(customSidebar, R.id.fractal_logo_sidebar, R.id.navigation_training_logs, drawerLayout, navController);
        }

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int destId = destination.getId();
            boolean isGetStarted = (destId == R.id.navigation_get_started);
            boolean isRewardBank = (destId == R.id.navigation_reward_bank);

            if (isGetStarted || isRewardBank) {
                if (customHeader != null) customHeader.setVisibility(View.GONE);
                if (navView != null && isGetStarted) navView.setVisibility(View.GONE);
                if (borderLine != null) borderLine.setVisibility(View.GONE);
            } else {
                if (customHeader != null) customHeader.setVisibility(View.VISIBLE);
                if (navView != null) navView.setVisibility(View.VISIBLE);
                if (borderLine != null) borderLine.setVisibility(View.VISIBLE);
            }

            if (navView != null) {
                if (destId == R.id.navigation_about || isRewardBank) {
                    navView.setBackgroundColor(Color.parseColor("#000000"));
                    ColorStateList whiteIcons = AppCompatResources.getColorStateList(this, R.color.nav_colors_black_bg);
                    navView.setItemIconTintList(whiteIcons);
                    navView.setItemTextColor(whiteIcons);
                    if (borderLine != null) {
                        borderLine.setBackgroundTintList(null);
                        borderLine.setBackgroundColor(Color.parseColor("#000000"));
                        borderLine.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                        int padding = (int) (getResources().getDisplayMetrics().widthPixels * 0.38);
                        borderLine.setPadding(padding, 0, padding, 0);
                        borderLine.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    }
                } else {
                    navView.setBackgroundColor(Color.WHITE);
                    ColorStateList blackIcons = AppCompatResources.getColorStateList(this, R.color.nav_colors_white_bg);
                    navView.setItemIconTintList(blackIcons);
                    navView.setItemTextColor(blackIcons);
                    if (borderLine != null) {
                        borderLine.setBackgroundTintList(null);
                        borderLine.setBackgroundColor(Color.WHITE);
                        borderLine.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
                        borderLine.setPadding(0, 0, 0, 0);
                        borderLine.setScaleType(ImageView.ScaleType.FIT_XY);
                    }
                }

                if (!isGetStarted) {
                    if (destId == R.id.navigation_home) {
                        navView.getMenu().findItem(destId).setChecked(true);
                        navView.post(() -> animateNavIcons(navView, destId));
                    } else if (destId == R.id.navigation_reward_bank) {
                        navView.getMenu().findItem(R.id.navigation_model).setChecked(true);
                        navView.post(() -> animateNavIcons(navView, R.id.navigation_model));
                    } else if (destId != R.id.navigation_device) {
                        navView.getMenu().setGroupCheckable(0, true, false);
                        for (int i = 0; i < navView.getMenu().size(); i++) navView.getMenu().getItem(i).setChecked(false);
                        navView.getMenu().setGroupCheckable(0, true, true);
                        navView.post(() -> animateNavIcons(navView, -1));
                    }
                }
            }
        });
    }

    private void setupSidebarNav(View sidebar, int viewId, int destId, DrawerLayout drawerLayout, NavController navController) {
        View v = sidebar.findViewById(viewId);
        if (v != null) {
            v.setOnClickListener(view -> {
                drawerLayout.closeDrawer(GravityCompat.START);

                // ── THE FIX: Prevent infinite screen piling ──
                NavOptions options = new NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .build();

                navController.navigate(destId, null, options);
            });
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        android.content.res.Configuration override = new android.content.res.Configuration(newBase.getResources().getConfiguration());
        override.fontScale = 1.0f;
        override.densityDpi = (int) (override.densityDpi * 0.85f);
        super.attachBaseContext(newBase.createConfigurationContext(override));
    }

    private void animateNavIcons(BottomNavigationView navView, int selectedId) {
        if (navView == null) return;
        for (int i = 0; i < navView.getMenu().size(); i++) {
            int itemId = navView.getMenu().getItem(i).getItemId();
            View itemView = navView.findViewById(itemId);
            if (itemView != null) {
                boolean isSelected = (itemId == selectedId);
                float scale = isSelected ? 1.4f : 1.0f;
                float alpha = isSelected ? 1.0f : 0.7f;
                itemView.animate().scaleX(scale).scaleY(scale).alpha(alpha).setDuration(350).start();
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof EditText) {
                Rect outRect = new Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    v.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }
}