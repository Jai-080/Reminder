package com.example.reminder.network;

import android.content.Context;
import android.util.Log;

import com.example.reminder.auth.TokenManager;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static final String TAG = "ApiClient";
    private static Retrofit retrofitWithAuth = null;
    private static Retrofit retrofitNoAuth = null;
    private static String cachedBaseUrl = null;

    private static synchronized void checkAndInitClients(Context context) {
        String currentBaseUrl = TokenManager.getInstance(context).getBaseUrl();
        if (cachedBaseUrl == null || !cachedBaseUrl.equals(currentBaseUrl)) {
            Log.d(TAG, "Configured Base URL changed or initialized: " + currentBaseUrl);
            cachedBaseUrl = currentBaseUrl;
            // Invalidate current cached Retrofit clients to force reconstruction with new Base URL
            retrofitWithAuth = null;
            retrofitNoAuth = null;
        }
    }

    public static synchronized Retrofit getClientWithAuth(Context context) {
        checkAndInitClients(context);
        if (retrofitWithAuth == null) {
            TokenManager tokenManager = TokenManager.getInstance(context);

            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .addInterceptor(new AuthInterceptor(tokenManager))
                    .addInterceptor(logging)
                    .authenticator(new TokenRefreshAuthenticator(context, tokenManager))
                    .build();

            retrofitWithAuth = new Retrofit.Builder()
                    .baseUrl(cachedBaseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                    .build();
        }
        return retrofitWithAuth;
    }

    public static synchronized Retrofit getClientNoAuth(Context context) {
        checkAndInitClients(context);
        if (retrofitNoAuth == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .build();

            retrofitNoAuth = new Retrofit.Builder()
                    .baseUrl(cachedBaseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                    .build();
        }
        return retrofitNoAuth;
    }

    // Expose API interfaces
    public static AuthApi getAuthServiceNoAuth(Context context) {
        return getClientNoAuth(context).create(AuthApi.class);
    }

    public static AuthApi getAuthServiceWithAuth(Context context) {
        return getClientWithAuth(context).create(AuthApi.class);
    }

    public static NoteApi getNoteApi(Context context) {
        return getClientWithAuth(context).create(NoteApi.class);
    }

    public static ReminderApi getReminderApi(Context context) {
        return getClientWithAuth(context).create(ReminderApi.class);
    }

    public static PaymentApi getPaymentApi(Context context) {
        return getClientWithAuth(context).create(PaymentApi.class);
    }
}
