package com.example.reminder.network;

import com.example.reminder.auth.TokenManager;
import com.example.reminder.utils.FileLogger;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {
    private final TokenManager tokenManager;

    public AuthInterceptor(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originalRequest = chain.request();
        String token = tokenManager.getAccessToken();

        FileLogger.log("REST_CLIENT", "REST request started: " + originalRequest.method() + " " + originalRequest.url()
                + " | Token Attached: " + (token != null) + " " + FileLogger.formatTokenMeta(token));

        Request requestToSend;
        if (token != null) {
            requestToSend = originalRequest.newBuilder()
                    .header("Authorization", "Bearer " + token)
                    .build();
        } else {
            requestToSend = originalRequest;
        }

        Response response = chain.proceed(requestToSend);

        FileLogger.log("REST_CLIENT", "REST response received: HTTP response code: " + response.code()
                + " for " + originalRequest.method() + " " + originalRequest.url());

        if (response.code() == 401) {
            FileLogger.log("REST_CLIENT", "401 detected: HTTP 401 Unauthorized for " + originalRequest.url());
        }

        return response;
    }
}
