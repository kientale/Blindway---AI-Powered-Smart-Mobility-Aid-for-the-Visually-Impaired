package com.example.blindwayapp.ui.home;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.blindwayapp.R;
import com.example.blindwayapp.ui.my_locations.MyLocationsFragment;
import com.example.blindwayapp.ui.navigation.NavigationFragment;
import com.example.blindwayapp.ui.search_location.SearchLocationFragment;
import com.example.blindwayapp.ui.traffic_location.TrafficLocationFragment;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private static final int LOCATION_PERMISSION_REQUEST = 1001;
    private FusedLocationProviderClient fusedLocationClient;
    private TextView tvCurrentLocation;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        tvCurrentLocation = root.findViewById(R.id.tvCurrentLocation);

        // Lấy CardView
        View cardMyLocation = root.findViewById(R.id.cardMyLocation);
        View cardSearchLocation = root.findViewById(R.id.cardSearch);
        View cardTrafficLocation = root.findViewById(R.id.cardTraffic);
        View cardNavigation = root.findViewById(R.id.cardNavigation);

        // Xử lý sự kiện click
        cardMyLocation.setOnClickListener(v -> openFragment(new MyLocationsFragment()));
        cardSearchLocation.setOnClickListener(v -> openFragment(new SearchLocationFragment()));
        cardTrafficLocation.setOnClickListener(v -> openFragment(new TrafficLocationFragment()));
        cardNavigation.setOnClickListener(v -> openFragment(new NavigationFragment()));

        // Lấy vị trí hiện tại
        getCurrentLocation();

        return root;
    }

    private void openFragment(Fragment fragment) {
        FragmentTransaction transaction = requireActivity()
                .getSupportFragmentManager()
                .beginTransaction();

        transaction.replace(R.id.container, fragment);
        transaction.addToBackStack(null);
        transaction.commit();
    }

    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {

            // Xin quyền nếu chưa có
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(requireActivity(), location -> {
                    if (location != null) {
                        String address = getAddressFromLocation(location);
                        tvCurrentLocation.setText(address);
                    } else {
                        tvCurrentLocation.setText("Không lấy được vị trí");
                    }
                });
    }

    private String getAddressFromLocation(Location location) {
        Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                return addresses.get(0).getAddressLine(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "Vĩ độ: " + location.getLatitude() + ", Kinh độ: " + location.getLongitude();
    }

    // Nhận kết quả xin quyền
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            } else {
                Toast.makeText(requireContext(), "Bạn cần cấp quyền vị trí để sử dụng tính năng này", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
