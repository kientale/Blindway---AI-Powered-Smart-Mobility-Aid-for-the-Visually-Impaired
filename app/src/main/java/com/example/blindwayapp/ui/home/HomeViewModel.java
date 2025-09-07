package com.example.blindwayapp.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Arrays;
import java.util.List;

public class HomeViewModel extends ViewModel {

    private final MutableLiveData<List<String>> locations = new MutableLiveData<>();

    public HomeViewModel() {
        // Khởi tạo dữ liệu
        locations.setValue(Arrays.asList("Nhà", "Trường học", "Bệnh viện"));
    }

    public LiveData<List<String>> getLocations() {
        return locations;
    }

    public void addLocation(String location) {
        List<String> current = locations.getValue();
        if (current != null) {
            current.add(location);
            locations.setValue(current);
        }
    }
}
