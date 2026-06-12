package com.example.reminder.network;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface PaymentApi {
    @GET("api/payments")
    Call<List<PaymentResponse>> getPayments();

    @POST("api/payments")
    Call<PaymentResponse> createPayment(@Body PaymentRequest request);

    @PUT("api/payments/{id}")
    Call<PaymentResponse> updatePayment(@Path("id") Long id, @Body PaymentRequest request);

    @DELETE("api/payments/{id}")
    Call<Void> deletePayment(@Path("id") Long id);
}
