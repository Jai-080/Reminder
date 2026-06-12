package com.example.reminder.network;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface ReminderApi {
    @GET("api/reminders")
    Call<List<ReminderResponse>> getReminders();

    @POST("api/reminders")
    Call<ReminderResponse> createReminder(@Body ReminderRequest request);

    @PUT("api/reminders/{id}")
    Call<ReminderResponse> updateReminder(@Path("id") Long id, @Body ReminderRequest request);

    @DELETE("api/reminders/{id}")
    Call<Void> deleteReminder(@Path("id") Long id);
}
