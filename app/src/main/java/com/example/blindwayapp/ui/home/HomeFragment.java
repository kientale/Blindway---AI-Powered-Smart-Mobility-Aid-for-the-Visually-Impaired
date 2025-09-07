package com.example.blindwayapp.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.example.blindwayapp.R;
import com.example.blindwayapp.ui.my_locations.MyLocationsFragment;

public class HomeFragment extends Fragment {

    private HomeViewModel homeViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        // Lấy CardView "Vị trí của tôi"
        View cardMyLocation = root.findViewById(R.id.cardMyLocation);

        // Xử lý sự kiện click
        cardMyLocation.setOnClickListener(v -> {
            FragmentTransaction transaction = requireActivity()
                    .getSupportFragmentManager()
                    .beginTransaction();

            transaction.replace(R.id.container, new MyLocationsFragment());
            transaction.addToBackStack(null); // Cho phép quay lại bằng nút Back
            transaction.commit();
        });
        return root;
    }
}
