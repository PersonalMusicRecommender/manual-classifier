package com.bardy.classifier.manual;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Error;
import com.spotify.sdk.android.player.Metadata;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerEvent;
import com.spotify.sdk.android.player.Spotify;
import com.spotify.sdk.android.player.SpotifyPlayer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class MainActivity extends AppCompatActivity implements SpotifyPlayer.NotificationCallback, ConnectionStateCallback {

    public static final String TAG = "MainAcitivity";

    private String mClientId;
    private static final String REDIRECT_URI = "personal-music-recommender://callback";
    private static final int REQUEST_CODE = 1337;

    private String mToken;

    private RequestQueue mRequestQueue;

    private Player mPlayer;

    private String mCurrentSpotifyId;
    private String mCurrentTrackName;

    private TextView mTitle;
    private TextView mArtist;
    private ImageView mAlbumCover;
    private ImageButton mPlayButton;
    private ImageButton[] mStars = new ImageButton[5];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            mClientId = loadClientId();

            AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(mClientId,
                    AuthenticationResponse.Type.TOKEN,
                    REDIRECT_URI);
            builder.setScopes(new String[]{"user-read-private", "streaming"});
            AuthenticationRequest request = builder.build();

            AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);

            mTitle = (TextView) findViewById(R.id.track_title);
            mArtist = (TextView) findViewById(R.id.track_artist);
            mAlbumCover = (ImageView) findViewById(R.id.track_album_cover);
            mPlayButton = (ImageButton) findViewById(R.id.play_button);

            mRequestQueue = Volley.newRequestQueue(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        // Check if result comes from the correct activity
        if (requestCode == REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
            if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                mToken = response.getAccessToken();

                Config playerConfig = new Config(this, mToken, mClientId);
                Spotify.getPlayer(playerConfig, this, new SpotifyPlayer.InitializationObserver() {
                    @Override
                    public void onInitialized(SpotifyPlayer spotifyPlayer) {
                        mPlayer = spotifyPlayer;
                        mPlayer.addConnectionStateCallback(MainActivity.this);
                        mPlayer.addNotificationCallback(MainActivity.this);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        Log.e("MainActivity", "Could not initialize player: " + throwable.getMessage());
                    }
                });
            }
        }
    }

    @Override
    protected void onStop () {
        super.onStop();
        if (mRequestQueue != null) {
            mRequestQueue.cancelAll(TAG);
        }
    }

    @Override
    protected void onDestroy() {
        Spotify.destroyPlayer(this);
        super.onDestroy();
    }

    @Override
    public void onLoggedIn() {
        playRandomDeepHouse();

        mPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlay();
            }
        });

        LinearLayout starsContainer = (LinearLayout) findViewById(R.id.stars_container);
        for(int i = 0; i < starsContainer.getChildCount(); i++) {
            ImageButton star = (ImageButton) starsContainer.getChildAt(i);

            final int index = i;
            star.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    rateTrack(mCurrentSpotifyId, mCurrentTrackName, index + 1);

                    for(int j = 0; j <= index; j++)
                        mStars[j].setImageResource(R.drawable.ic_star_white_18dp);

                    playRandomDeepHouse();
                }
            });

            mStars[i] = star;
        }
    }

    @Override
    public void onLoggedOut() {
        Log.d("MainActivity", "User logged out");
    }

    @Override
    public void onLoginFailed(Error error) {
        Log.d("MainActivity", "Login failed");
    }

    @Override
    public void onTemporaryError() {
        Log.d("MainActivity", "Temporary error occurred");
    }

    @Override
    public void onConnectionMessage(String message) {
        Log.d("MainActivity", "Received connection message: " + message);
    }

    @Override
    public void onPlaybackEvent(PlayerEvent playerEvent) {
        Log.d("MainActivity", "Playback event received: " + playerEvent.name());
        switch (playerEvent) {
            case kSpPlaybackNotifyPlay: setPauseButton(); break;
            case kSpPlaybackNotifyPause: setPlayButton(); break;
            case kSpPlaybackNotifyMetadataChanged: setMetadata(); break;
            default:
                break;
        }
    }

    @Override
    public void onPlaybackError(Error error) {
        Log.e("MainActivity", "Playback error received: " + error.name());
        switch (error) {
            default:
                break;
        }
    }

    private void playRandomDeepHouse() {
        String url ="https://api.spotify.com/v1/recommendations?seed_genres=deep-house&limit=1";

        JsonObjectRequest recommendationsRequest = new JsonObjectRequest(Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject json) {
                try {
                    JSONArray tracks = json.getJSONArray("tracks");
                    if(tracks.length() > 0) {
                        JSONObject track = tracks.getJSONObject(0);
                        JsonObjectRequest playRequest =
                                createPlayRequest(track.getString("id"), track.getString("name"), track.getString("uri"));
                        playRequest.setTag(TAG);
                        mRequestQueue.add(playRequest);
                    }
                    else
                        playRandomDeepHouse();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("Recommendations request",error.toString());
            }
        }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String>  params = new HashMap<>();
                params.put("AUTHORIZATION", "Bearer " + mToken);
                return params;
            }
        };
        recommendationsRequest.setTag(TAG);

        mRequestQueue.add(recommendationsRequest);
    }

    private JsonObjectRequest createPlayRequest(final String spotifyId, final String name, final String spotifyUri) {
        String url = "http://35.167.14.171:9000/is-track-rated/" + spotifyId;

        return new JsonObjectRequest(Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject json) {
                try {
                    if(!json.getBoolean("is-track-rated")) {
                        mPlayer.playUri(new Player.OperationCallback() {
                            @Override
                            public void onSuccess() {
                                for(int i = 0; i < mStars.length; i++) {
                                    mCurrentSpotifyId = spotifyId;
                                    mCurrentTrackName = name;
                                    mStars[i].setImageResource(R.drawable.ic_star_border_white_18dp);
                                }
                            }

                            @Override
                            public void onError(Error error) {
                                Log.e("Play error", error.name());
                            }
                        }, spotifyUri, 0, 0);
                    }
                    else
                        playRandomDeepHouse();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("Is rated request",error.toString());
            }
        });
    }

    private void rateTrack(String spotifyId, String name, int stars) {
        String url = "http://35.167.14.171:9000/rate-track";

        try {
            JSONObject data = new JSONObject();
            data.put("spotify-id", spotifyId);
            data.put("name", name);
            data.put("stars", stars);

            JsonObjectRequest rateRequest = new JsonObjectRequest(Request.Method.POST, url, data, new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject json) {}
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Toast.makeText(MainActivity.this, "Track wasn't rated", Toast.LENGTH_SHORT).show();
                    Log.e("Rate request",error.toString());
                }
            });
            rateRequest.setTag(TAG);

            mRequestQueue.add(rateRequest);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void setMetadata() {
        Metadata.Track trackData = mPlayer.getMetadata().currentTrack;

        if(trackData != null) {
            mTitle.setText(trackData.name);
            mArtist.setText(trackData.artistName);
            new DownloadImageTask(mAlbumCover).execute(trackData.albumCoverWebUrl);
        }
        else
            playRandomDeepHouse();
    }

    private void togglePlay() {
        if(mPlayer.getPlaybackState().isPlaying)
            mPlayer.pause(null);
        else
            mPlayer.resume(null);
    }

    private void setPlayButton() {
        mPlayButton.setImageResource(R.drawable.ic_play_circle_outline_white_24dp);
    }

    private void setPauseButton() {
        mPlayButton.setImageResource(R.drawable.ic_pause_circle_outline_white_24dp);
    }

    private String loadClientId() throws IOException {
        Properties properties = new Properties();
        properties.load(getAssets().open("app.properties"));
        return properties.getProperty("client-id");
    }
}

class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {

    private ImageView bmImage;

    DownloadImageTask(ImageView bmImage) {
        this.bmImage = bmImage;
    }

    protected Bitmap doInBackground(String... urls) {
        Bitmap mIcon11 = null;
        try {
            InputStream in = new java.net.URL(urls[0]).openStream();
            mIcon11 = BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            Log.e("Image download", e.getMessage());
            e.printStackTrace();
        }
        return mIcon11;
    }

    protected void onPostExecute(Bitmap image) {
        bmImage.setImageBitmap(image);
    }
}
