package com.example.artablica;

import java.util.List;

public interface SupabaseCallback {
    void onSuccess(List<Car> cars);
    void onFailure(Exception e);
}
