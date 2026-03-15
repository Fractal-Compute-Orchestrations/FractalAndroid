package com.example.fractal;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
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

    // SharedPreferences key prefix.
    // We store the LAST email we successfully synced for each hardware ID.
    // ─  ""                   → never synced before (send with "not_registered")
    // ─  "Not Authenticated"  → synced but user not logged in yet
    // ─  "<real email>"       → fully registered sync done
    private static final String PREFS_NAME       = "fractal_device_prefs";
    private static final String KEY_LAST_SYNCED  = "last_synced_email_";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // =========================================================================
        // FIX: FORCE STATUS BAR ICONS TO BE DARK GLOBALLY
        // =========================================================================
        WindowInsetsControllerCompat windowController = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        // 'true' means "the background is light, so use dark icons"
        windowController.setAppearanceLightStatusBars(true);
        // =========================================================================

        // =========================================================================
        // FIREBASE REMOTE CONFIG
        // =========================================================================
        FirebaseRemoteConfig mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600)
                .build();
        mFirebaseRemoteConfig.setConfigSettingsAsync(configSettings);

        Map<String, Object> defaults = new HashMap<>();
        defaults.put("server_protocol", "https");
        defaults.put("server_ip", "fractal-grid.duckdns.org");
        defaults.put("server_port", "");
        mFirebaseRemoteConfig.setDefaultsAsync(defaults);

        mFirebaseRemoteConfig.fetchAndActivate().addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                android.util.Log.i("RemoteConfig", "Cloud Server URLs updated successfully!");
            }
        });
        // =========================================================================

        DrawerLayout drawerLayout        = findViewById(R.id.drawer_layout);
        View customHeader                = findViewById(R.id.custom_header);
        ImageButton headerDrawerButton   = findViewById(R.id.header_drawer);
        BottomNavigationView navView     = findViewById(R.id.nav_view);
        ImageView borderLine             = findViewById(R.id.imageView);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_activity_main);
        if (navHostFragment == null) return;
        NavController navController = navHostFragment.getNavController();

        if (navView != null) {
            navView.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                int currentDest = navController.getCurrentDestination() != null
                        ? navController.getCurrentDestination().getId() : -1;

                // =========================================================================
                // NAV OPTIONS: INSTANT SWAP & BACKSTACK MANAGEMENT
                // =========================================================================
                NavOptions navOptions = new NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .setRestoreState(true)
                        // We leave setEnterAnim/setExitAnim out for instant transitions.
                        // This specific PopUpTo setup is what kills the Home screen flicker.
                        .setPopUpTo(navController.getGraph().getStartDestinationId(), false, true)
                        .build();

                if (itemId == R.id.navigation_home) {
                    if (currentDest != R.id.navigation_home) {
                        navController.navigate(R.id.navigation_home, null, navOptions);
                    }
                } else if (itemId == R.id.navigation_device) {
                    if (currentDest != R.id.navigation_device) {
                        navController.navigate(R.id.navigation_device, null, navOptions);
                    }
                    Bundle result = new Bundle();
                    result.putInt("page", 0);
                    getSupportFragmentManager().setFragmentResult("tab_change", result);
                } else if (itemId == R.id.navigation_model) {
                    if (currentDest != R.id.navigation_reward_bank) {
                        navController.navigate(R.id.navigation_reward_bank, null, navOptions);
                    }
                }

                navView.getMenu().findItem(itemId).setChecked(true);
                navView.post(() -> animateNavIcons(navView, itemId));
                return true;
            });

            // Handle orchestration tab swipe (e.g., from a ViewPager in the Device fragment)
            getSupportFragmentManager().setFragmentResultListener("pager_swiped", this, (requestKey, bundle) -> {
                int page = bundle.getInt("page");
                if (page == 0) {
                    navView.getMenu().findItem(R.id.navigation_device).setChecked(true);
                    navView.post(() -> animateNavIcons(navView, R.id.navigation_device));
                }
            });
        }

        // =========================================================================
        // AUTO-SEND REGISTERED DTO ON STARTUP
        // =========================================================================
        Context appContext = getApplicationContext();

        Thread registeredInfoSender = new Thread(() -> {
            // ── Step 1: Build hardware DTO ────────────────────────────────────
            AppBackend.Network.RegisteredInfo.RegistrationManager regManager =
                    new AppBackend.Network.RegisteredInfo.RegistrationManager(appContext);

            AppBackend.Network.RegisteredInfo.Registered_DTO dto =
                    regManager.generateNewRegistrationData();

            android.util.Log.i("MainActivity", "Hardware DTO → HW: " + dto.getHardwareID()
                    + " | MAC: " + dto.getMacAddress()
                    + " | RAM: " + dto.getTotalRam());

            // ── Step 2: Wait for Firebase Auth to initialise (up to 5 s) ─────
            com.google.firebase.auth.FirebaseAuth auth =
                    com.google.firebase.auth.FirebaseAuth.getInstance();
            int waited = 0;
            while (auth.getCurrentUser() == null && waited < 5) {
                try { Thread.sleep(1000); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                waited++;
            }

            // ── Step 3: Fill auth fields ──────────────────────────────────────
            com.google.firebase.auth.FirebaseUser currentUser = auth.getCurrentUser();
            if (currentUser != null) {
                try { currentUser.reload(); } catch (Exception ignored) {}

                dto.setUsername(currentUser.getDisplayName() != null
                        && !currentUser.getDisplayName().isEmpty()
                        ? currentUser.getDisplayName() : "Authorized User");
                dto.setEmail(currentUser.getEmail() != null
                        ? currentUser.getEmail() : "Unknown Email");
                dto.setJoinedOn(currentUser.getMetadata() != null
                        ? new java.text.SimpleDateFormat("dd MMM, yyyy", java.util.Locale.getDefault())
                        .format(new java.util.Date(currentUser.getMetadata().getCreationTimestamp()))
                        : "N/A");
                dto.setStatus("registered");
                android.util.Log.i("MainActivity", "Auth user found → " + dto.getEmail());
            } else {
                dto.setUsername("Unregistered Device");
                dto.setEmail("Not Authenticated");
                dto.setJoinedOn("N/A");
                dto.setStatus("not_registered");
                android.util.Log.i("MainActivity", "No auth user — hardware-only DTO.");
            }

            // ── Step 4: Check local flag — should we sync? ────────────────────
            SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String syncKey          = KEY_LAST_SYNCED + dto.getHardwareID();
            String lastSyncedEmail  = prefs.getString(syncKey, "");   // "" = never synced

            boolean needsSync;
            if (lastSyncedEmail.isEmpty()) {
                // Fresh install or cache cleared — always sync
                needsSync = true;
                android.util.Log.i("MainActivity", "No previous sync record — will sync.");
            } else if (lastSyncedEmail.equals(dto.getEmail())) {
                // Nothing changed since last sync — skip
                needsSync = false;
                android.util.Log.i("MainActivity", "Email unchanged since last sync (" + dto.getEmail() + ") — skipping.");
            } else {
                // Email changed (e.g. user logged in since last launch) — sync
                needsSync = true;
                android.util.Log.i("MainActivity", "Email changed: '" + lastSyncedEmail
                        + "' → '" + dto.getEmail() + "' — will sync.");
            }

            if (!needsSync) return;

            // ── Step 5: Retry loop until Firestore confirms the write ─────────
            AppBackend.Network.Server_DAO.Server_DAO dao =
                    new AppBackend.Network.Server_DAO.Server_DAO();
            boolean sent = false;

            while (!sent) {
                try {
                    sent = dao.POST_SendRegisteredInfo(dto);

                    if (sent) {
                        // Persist the email we just synced so we don't repeat
                        prefs.edit().putString(syncKey, dto.getEmail()).apply();
                        android.util.Log.i("MainActivity",
                                "Sync confirmed. Cached email: " + dto.getEmail());
                    } else {
                        android.util.Log.w("MainActivity", "Send failed — retrying in 10 s…");
                        Thread.sleep(10_000);
                    }

                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "Unexpected error: " + e.getMessage());
                    try { Thread.sleep(10_000); } catch (InterruptedException ie2) { break; }
                }
            }

            if (sent) android.util.Log.i("MainActivity", "RegisteredDTO loop finished.");
        });

        registeredInfoSender.setDaemon(true);
        registeredInfoSender.start();
        // =========================================================================

        if (headerDrawerButton != null && drawerLayout != null) {
            headerDrawerButton.setOnClickListener(v -> {
                if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
            });
        }

        View customSidebar = findViewById(R.id.custom_sidebar);
        if (customSidebar != null && drawerLayout != null) {
            ImageButton sidebarCloseBtn = customSidebar.findViewById(R.id.sidebar_close_btn);
            if (sidebarCloseBtn != null)
                sidebarCloseBtn.setOnClickListener(v -> drawerLayout.closeDrawer(GravityCompat.START));

            View navSettings = customSidebar.findViewById(R.id.nav_item_settings);
            if (navSettings != null) navSettings.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                navController.navigate(R.id.navigation_settings);
            });

            View navDeviceInsights = customSidebar.findViewById(R.id.nav_item_device_insights);
            if (navDeviceInsights != null) navDeviceInsights.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                navController.navigate(R.id.nav_device_insights);
            });

            View navAbout = customSidebar.findViewById(R.id.nav_item_about);
            if (navAbout != null) navAbout.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                navController.navigate(R.id.navigation_about);
            });

            View navRegDevice = customSidebar.findViewById(R.id.nav_item_reg_device);
            if (navRegDevice != null) navRegDevice.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                navController.navigate(R.id.navigation_device_auth);
            });

            View navUnregDevice = customSidebar.findViewById(R.id.nav_item_unreg_device);
            if (navUnregDevice != null) navUnregDevice.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                navController.navigate(R.id.navigation_device_unregister);
            });

            View navRegInfo = customSidebar.findViewById(R.id.nav_item_reg_info);
            if (navRegInfo != null) navRegInfo.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                navController.navigate(R.id.navigation_registered_info);
            });
        }

        ImageView fractalLogo = customSidebar.findViewById(R.id.fractal_logo_sidebar);
        if (fractalLogo != null) {
            fractalLogo.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                navController.navigate(R.id.navigation_training_logs);
            });
        }

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int destId       = destination.getId();

            // Define destinations that need special header handling
            boolean isGetStarted = (destId == R.id.navigation_get_started);
            boolean isRewardBank = (destId == R.id.navigation_reward_bank);

            // Hide the global header on Get Started AND the Reward Bank (to allow the liquid effect)
            if (isGetStarted || isRewardBank) {
                if (customHeader != null) customHeader.setVisibility(View.GONE);
                if (navView      != null && isGetStarted) navView.setVisibility(View.GONE); // Keep nav bar on bank page
                if (borderLine   != null) borderLine.setVisibility(View.GONE);
            } else {
                if (customHeader != null) customHeader.setVisibility(View.VISIBLE);
                if (navView      != null) navView.setVisibility(View.VISIBLE);
                if (borderLine   != null) borderLine.setVisibility(View.VISIBLE);
            }

            // ADDED THIS: Trigger dark bar for both About and Reward Bank
            boolean useBlackBar = (destId == R.id.navigation_about || isRewardBank);

            if (navView != null) {
                if (useBlackBar) {
                    // ADDED THIS: Use liquid hex #000000 for the Bank, pure black #000000 for About
                    String darkColor = isRewardBank ? "#000000" : "#000000";

                    navView.setBackgroundColor(Color.parseColor(darkColor));
                    ColorStateList whiteIcons = AppCompatResources.getColorStateList(MainActivity.this, R.color.nav_colors_black_bg);
                    navView.setItemIconTintList(whiteIcons);
                    navView.setItemTextColor(whiteIcons);

                    if (borderLine != null) {
                        borderLine.setBackgroundTintList(null);
                        borderLine.setBackgroundColor(Color.parseColor(darkColor));
                        borderLine.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                        int padding = (int) (getResources().getDisplayMetrics().widthPixels * 0.38);
                        borderLine.setPadding(padding, 0, padding, 0);
                        borderLine.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    }
                } else {
                    navView.setBackgroundColor(Color.WHITE);
                    ColorStateList blackIcons = AppCompatResources.getColorStateList(MainActivity.this, R.color.nav_colors_white_bg);
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
                        for (int i = 0; i < navView.getMenu().size(); i++) {
                            navView.getMenu().getItem(i).setChecked(false);
                        }
                        navView.getMenu().setGroupCheckable(0, true, true);
                        navView.post(() -> animateNavIcons(navView, -1));
                    }
                }
            }
        });
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        android.content.res.Configuration override =
                new android.content.res.Configuration(newBase.getResources().getConfiguration());
        override.fontScale = 1.0f;
        override.densityDpi = (int) (override.densityDpi * 0.85f);
        Context context = newBase.createConfigurationContext(override);
        super.attachBaseContext(context);
    }

    private void animateNavIcons(BottomNavigationView navView, int selectedId) {
        if (navView == null) return;
        for (int i = 0; i < navView.getMenu().size(); i++) {
            int itemId   = navView.getMenu().getItem(i).getItemId();
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