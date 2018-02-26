package org.wordpress.android.ui.photopicker;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SearchView;

import com.android.volley.VolleyError;
import com.wordpress.rest.RestRequest;

import org.json.JSONArray;
import org.json.JSONObject;
import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.fluxc.model.MediaModel;
import org.wordpress.android.fluxc.model.SiteModel;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.DisplayUtils;
import org.wordpress.android.util.PhotonUtils;
import org.wordpress.android.util.StringUtils;
import org.wordpress.android.util.UrlUtils;
import org.wordpress.android.widgets.WPNetworkImageView;

import java.util.ArrayList;
import java.util.List;

public class StockPhotoPickerActivity extends AppCompatActivity {

    private SiteModel mSite;

    private RecyclerView mRecycler;
    private StockPhotoAdapter mAdapter;

    private int mThumbWidth;
    private int mThumbHeight;

    private SearchView mSearchView;
    private String mSearchQuery;
    private final Handler mHandler = new Handler();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //((WordPress) getApplication()).component().inject(this);
        setContentView(R.layout.stock_photo_picker_activity);

        int displayWidth = DisplayUtils.getDisplayPixelWidth(this);
        mThumbWidth = displayWidth / getColumnCount();
        mThumbHeight = (int) (mThumbWidth * 0.75f);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_close_white_24dp);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowTitleEnabled(true);
        }

        mRecycler = findViewById(R.id.recycler);
        mRecycler.setLayoutManager(new GridLayoutManager(this, getColumnCount()));

        mAdapter = new StockPhotoAdapter(this);
        mRecycler.setAdapter(mAdapter);

        mSearchView = findViewById(R.id.search_view);
        mSearchView.setEnabled(false);
        mSearchView.setIconifiedByDefault(false);

        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                submitSearch(query, false);
                return true;
            }
            @Override
            public boolean onQueryTextChange(String query) {
                submitSearch(query, true);
                return true;
            }
        });

        if (savedInstanceState == null) {
            mSite = (SiteModel) getIntent().getSerializableExtra(WordPress.SITE);
        } else {
            mSite = (SiteModel) savedInstanceState.getSerializable(WordPress.SITE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        //mDispatcher.register(this);
    }

    @Override
    protected void onStop() {
        //Dispatcher.unregister(this);
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mSite != null) {
            outState.putSerializable(WordPress.SITE, mSite);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            setResult(RESULT_CANCELED);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    void showProgress(boolean show) {
        if (!isFinishing()) {
            findViewById(R.id.progress).setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    void submitSearch(@Nullable final String query, boolean delayed) {
        mSearchQuery = query;

        if (TextUtils.isEmpty(query)) {
            // TODO: clear search results
            return;
        }

        if (delayed) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (StringUtils.equals(query, mSearchQuery)) {
                        submitSearch(query, false);
                    }
                }
            }, 500);
        } else {
            requestStockPhotos(query);
        }
    }

    private void requestStockPhotos(@Nullable String query) {
        RestRequest.Listener listener = new RestRequest.Listener() {
            @Override
            public void onResponse(JSONObject jsonObject) {
                showProgress(false);
                parseResponse(jsonObject);
            }
        };
        RestRequest.ErrorListener errorListener = new RestRequest.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                AppLog.e(AppLog.T.READER, volleyError);
                showProgress(false);

            }
        };

        showProgress(true);
        String path = "/meta/external-media/pexels?number=20&search=" + UrlUtils.urlEncode(query);
        WordPress.getRestClientUtilsV1_1().get(path, null, null, listener, errorListener);
    }

    private void parseResponse(@NonNull JSONObject jsonObject) {
        List<MediaModel> mediaList = new ArrayList<>();

        JSONArray jsonMediaList = jsonObject.optJSONArray("media");
        if (jsonMediaList != null) {
            for (int i = 0; i < jsonMediaList.length(); i++) {
                JSONObject jsonMedia = jsonMediaList.optJSONObject(i);
                if (jsonMedia != null) {
                    MediaModel media = new MediaModel();
                    media.setMediaId(jsonMedia.optLong("ID"));
                    media.setUrl(jsonMedia.optString("URL"));
                    media.setFileExtension(jsonMedia.optString("extension"));
                    media.setTitle(jsonMedia.optString("title"));
                    media.setFileName(jsonMedia.optString("file"));
                    media.setHeight(jsonMedia.optInt("height"));
                    media.setWidth(jsonMedia.optInt("width"));
                    media.setGuid(jsonMedia.optString("guid"));
                    JSONObject jsonThumbnail = jsonMedia.optJSONObject("thumbnails");
                    if (jsonThumbnail != null) {
                        media.setThumbnailUrl(jsonThumbnail.optString("thumbnail"));
                    }
                    mediaList.add(media);
                }
            }
        }


        mAdapter.setMediaList(mediaList);
    }

    private class StockPhotoAdapter extends RecyclerView.Adapter<StockViewHolder> {
        private final List<MediaModel> mItems = new ArrayList<>();
        private final LayoutInflater mLayoutInflater;

        StockPhotoAdapter(Context context) {
            mLayoutInflater = LayoutInflater.from(context);
            setHasStableIds(false);
        }

        public void setMediaList(@NonNull List<MediaModel> mediaList) {
            mItems.clear();
            mItems.addAll(mediaList);
            notifyDataSetChanged();
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        @Override
        public StockViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = mLayoutInflater.inflate(R.layout.stock_photo_picker_thumbnail, parent, false);
            return new StockViewHolder(view);
        }

        @Override
        public void onBindViewHolder(StockViewHolder holder, int position) {
            MediaModel media = mItems.get(position);
            String imageUrl = getBestImageUrl(media);
            holder.imageView.setImageUrl(imageUrl, WPNetworkImageView.ImageType.PHOTO);
        }

        private String getBestImageUrl(@NonNull MediaModel media) {
            return PhotonUtils.getPhotonImageUrl(media.getThumbnailUrl(), mThumbWidth, mThumbHeight);
        }
    }

    class StockViewHolder extends RecyclerView.ViewHolder {
        private final WPNetworkImageView imageView;

        public StockViewHolder(View view) {
            super(view);
            imageView = view.findViewById(R.id.image_thumbnail);
            imageView.getLayoutParams().width = mThumbWidth;
            imageView.getLayoutParams().height = mThumbHeight;
        }
    }

    public int getColumnCount() {
        return DisplayUtils.isLandscape(this) ? 4 : 3;
    }

}
