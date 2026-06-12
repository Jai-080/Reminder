package com.example.reminder.network;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface NoteApi {
    @GET("api/notes")
    Call<List<NoteResponse>> getNotes();

    @POST("api/notes")
    Call<NoteResponse> createNote(@Body NoteRequest request);

    @PUT("api/notes/{id}")
    Call<NoteResponse> updateNote(@Path("id") Long id, @Body NoteRequest request);

    @DELETE("api/notes/{id}")
    Call<Void> deleteNote(@Path("id") Long id);
}
