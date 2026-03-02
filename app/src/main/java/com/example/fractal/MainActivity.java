package com.example.fractal;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.fractal.databinding.ActivityMainBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        // 1. Inflate Layout
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 2. Safe View Lookups
        DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        View customHeader = findViewById(R.id.custom_header);
        ImageButton headerDrawerButton = findViewById(R.id.header_drawer);
        BottomNavigationView navView = findViewById(R.id.nav_view);
        ImageView borderLine = findViewById(R.id.imageView);

        // 3. Safe NavController Initialization
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_activity_main);

        if (navHostFragment == null) return;
        NavController navController = navHostFragment.getNavController();

        // =========================================================================
        // NEW: FIRST LAUNCH LOGIC
        // =========================================================================
        SharedPreferences prefs = getSharedPreferences("fractal_prefs", MODE_PRIVATE);
        boolean isFirstLaunch = prefs.getBoolean("is_first_launch", true);

        if (!isFirstLaunch) {
            // If it's NOT the first launch, clear the backstack and jump straight to Home
            NavOptions navOptions = new NavOptions.Builder()
                    .setPopUpTo(R.id.navigation_get_started, true)
                    .build();
            navController.navigate(R.id.navigation_home, null, navOptions);
        } else {
            // If it IS the first launch, save false so we never see it again
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("is_first_launch", false);
            editor.apply();
        }
        // =========================================================================

        // 4. Custom Bottom Nav Setup
        if (navView != null) {
            navView.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                int currentDest = navController.getCurrentDestination() != null ? navController.getCurrentDestination().getId() : -1;

                if (itemId == R.id.navigation_home) {
                    if (currentDest != R.id.navigation_home) {
                        navController.navigate(R.id.navigation_home);
                    }
                } else if (itemId == R.id.navigation_device || itemId == R.id.navigation_model) {
                    int targetPage = (itemId == R.id.navigation_device) ? 0 : 2;

                    // Navigate to the Pager host if we aren't already there
                    if (currentDest != R.id.navigation_device) {
                        navController.navigate(R.id.navigation_device);
                    }

                    // FIX: Always send the fragment result.
                    Bundle result = new Bundle();
                    result.putInt("page", targetPage);
                    getSupportFragmentManager().setFragmentResult("tab_change", result);
                }

                navView.getMenu().findItem(itemId).setChecked(true);
                navView.post(() -> animateNavIcons(navView, itemId));
                return true;
            });

            // Listen for user swiping the Pager
            getSupportFragmentManager().setFragmentResultListener("pager_swiped", this, (requestKey, bundle) -> {
                int page = bundle.getInt("page");

                if (page == 1) {
                    // --- MIDDLE SCREEN: Deselect all tabs ---
                    navView.getMenu().setGroupCheckable(0, true, false);
                    for (int i = 0; i < navView.getMenu().size(); i++) {
                        navView.getMenu().getItem(i).setChecked(false);
                    }
                    navView.getMenu().setGroupCheckable(0, true, true);

                    // Passing -1 scales all icons back to their normal, unselected size
                    navView.post(() -> animateNavIcons(navView, -1));
                } else {
                    // --- LEFT OR RIGHT SCREEN: Select Device or Model ---
                    int targetId = (page == 0) ? R.id.navigation_device : R.id.navigation_model;
                    navView.getMenu().findItem(targetId).setChecked(true);
                    navView.post(() -> animateNavIcons(navView, targetId));
                }
            });
        }

        // 5. Sidebar Drawer Listener
        if (headerDrawerButton != null && drawerLayout != null) {
            headerDrawerButton.setOnClickListener(v -> {
                if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
            });
        }

        // 6. Sidebar Navigation Logic
        View customSidebar = findViewById(R.id.custom_sidebar);
        if (customSidebar != null && drawerLayout != null) {
            ImageButton sidebarCloseBtn = customSidebar.findViewById(R.id.sidebar_close_btn);
            if (sidebarCloseBtn != null) sidebarCloseBtn.setOnClickListener(v -> drawerLayout.closeDrawer(GravityCompat.START));

            View navSettings = customSidebar.findViewById(R.id.nav_item_settings);
            if (navSettings != null) navSettings.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                navController.navigate(R.id.navigation_settings);
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

        // 7. Centralized Logic: Visibility & Colors
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int destId = destination.getId();
            boolean isGetStarted = (destId == R.id.navigation_get_started);

            // A. Handle Visibility
            if (isGetStarted) {
                if (customHeader != null) customHeader.setVisibility(View.GONE);
                if (navView != null) navView.setVisibility(View.GONE);
                if (borderLine != null) borderLine.setVisibility(View.GONE);
            } else {
                if (customHeader != null) customHeader.setVisibility(View.VISIBLE);
                if (navView != null) navView.setVisibility(View.VISIBLE);
                if (borderLine != null) borderLine.setVisibility(View.VISIBLE);
            }

            // B. Handle Dynamic Colors
            boolean useBlackBar = (destId == R.id.navigation_about);

            if (navView != null) {
                if (useBlackBar) {
                    navView.setBackgroundColor(Color.parseColor("#000000"));
                    ColorStateList whiteIcons = AppCompatResources.getColorStateList(MainActivity.this, R.color.nav_colors_black_bg);
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

                // C. Sync Initial/Default Selection State
                if (!isGetStarted) {
                    if (destId == R.id.navigation_home) {
                        navView.getMenu().findItem(destId).setChecked(true);
                        navView.post(() -> animateNavIcons(navView, destId));
                    } else if (destId == R.id.navigation_device) {
                        // Handled manually
                    } else {
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
                // If the user tapped OUTSIDE the active EditText
                if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    // Force the EditText to lose focus
                    v.clearFocus();
                    // Hide the soft keyboard automatically
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

}