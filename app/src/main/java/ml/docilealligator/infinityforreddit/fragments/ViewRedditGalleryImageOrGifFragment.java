package ml.docilealligator.infinityforreddit.fragments;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.github.piasy.biv.BigImageViewer;
import com.github.piasy.biv.loader.ImageLoader;
import com.github.piasy.biv.loader.glide.GlideImageLoader;
import com.github.piasy.biv.view.BigImageView;
import com.github.piasy.biv.view.GlideImageViewFactory;
import com.google.android.material.bottomappbar.BottomAppBar;

import java.io.File;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import ml.docilealligator.infinityforreddit.BuildConfig;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.SetAsWallpaperCallback;
import ml.docilealligator.infinityforreddit.activities.ViewRedditGalleryActivity;
import ml.docilealligator.infinityforreddit.asynctasks.SaveBitmapImageToFile;
import ml.docilealligator.infinityforreddit.asynctasks.SaveGIFToFile;
import ml.docilealligator.infinityforreddit.bottomsheetfragments.SetAsWallpaperBottomSheetFragment;
import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.services.DownloadMediaService;

public class ViewRedditGalleryImageOrGifFragment extends Fragment {

    public static final String EXTRA_REDDIT_GALLERY_MEDIA = "ERGM";
    public static final String EXTRA_SUBREDDIT_NAME = "ESN";
    public static final String EXTRA_INDEX = "EI";
    public static final String EXTRA_MEDIA_COUNT = "EMC";
    private static final int PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 0;

    @BindView(R.id.progress_bar_view_reddit_gallery_image_or_gif_fragment)
    ProgressBar progressBar;
    @BindView(R.id.image_view_view_reddit_gallery_image_or_gif_fragment)
    BigImageView imageView;
    @BindView(R.id.load_image_error_linear_layout_view_reddit_gallery_image_or_gif_fragment)
    LinearLayout errorLinearLayout;
    @BindView(R.id.bottom_navigation_view_reddit_gallery_image_or_gif_fragment)
    BottomAppBar bottomAppBar;
    @BindView(R.id.title_text_view_view_reddit_gallery_image_or_gif_fragment)
    TextView titleTextView;
    @BindView(R.id.download_image_view_view_reddit_gallery_image_or_gif_fragment)
    ImageView downloadImageView;
    @BindView(R.id.share_image_view_view_reddit_gallery_image_or_gif_fragment)
    ImageView shareImageView;
    @BindView(R.id.wallpaper_image_view_view_reddit_gallery_image_or_gif_fragment)
    ImageView wallpaperImageView;
    @Inject
    Executor mExecutor;

    private ViewRedditGalleryActivity activity;
    private RequestManager glide;
    private Post.Gallery media;
    private String subredditName;
    private boolean isDownloading = false;
    private boolean isActionBarHidden = false;

    public ViewRedditGalleryImageOrGifFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        BigImageViewer.initialize(GlideImageLoader.with(activity));

        View rootView = inflater.inflate(R.layout.fragment_view_reddit_gallery_image_or_gif, container, false);

        ((Infinity) activity.getApplication()).getAppComponent().inject(this);

        ButterKnife.bind(this, rootView);

        setHasOptionsMenu(true);

        media = getArguments().getParcelable(EXTRA_REDDIT_GALLERY_MEDIA);
        subredditName = getArguments().getString(EXTRA_SUBREDDIT_NAME);
        glide = Glide.with(activity);

        imageView.setImageViewFactory(new GlideImageViewFactory());

        imageView.setImageLoaderCallback(new ImageLoader.Callback() {
            @Override
            public void onCacheHit(int imageType, File image) {

            }

            @Override
            public void onCacheMiss(int imageType, File image) {

            }

            @Override
            public void onStart() {

            }

            @Override
            public void onProgress(int progress) {

            }

            @Override
            public void onFinish() {

            }

            @Override
            public void onSuccess(File image) {
                progressBar.setVisibility(View.GONE);

                final SubsamplingScaleImageView view = imageView.getSSIV();

                if (view != null) {
                    view.setOnImageEventListener(new SubsamplingScaleImageView.DefaultOnImageEventListener() {
                        @Override
                        public void onImageLoaded() {
                            view.setMinimumDpi(80);
                            view.setDoubleTapZoomDpi(240);
                            view.setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_FIXED);
                            view.setQuickScaleEnabled(true);
                            view.resetScaleAndCenter();
                        }
                    });
                }
            }

            @Override
            public void onFail(Exception error) {
                progressBar.setVisibility(View.GONE);
                errorLinearLayout.setVisibility(View.VISIBLE);
            }
        });

        loadImage();

        imageView.setOnClickListener(view -> {
            if (isActionBarHidden) {
                activity.getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
                isActionBarHidden = false;
                if (activity.isUseBottomAppBar()) {
                    bottomAppBar.setVisibility(View.VISIBLE);
                }
            } else {
                activity.getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_IMMERSIVE);
                isActionBarHidden = true;
                if (activity.isUseBottomAppBar()) {
                    bottomAppBar.setVisibility(View.GONE);
                }
            }
        });

        errorLinearLayout.setOnClickListener(view -> {
            progressBar.setVisibility(View.VISIBLE);
            errorLinearLayout.setVisibility(View.GONE);
            loadImage();
        });

        if (activity.isUseBottomAppBar()) {
            bottomAppBar.setVisibility(View.VISIBLE);
            if (media.mediaType == Post.Gallery.TYPE_GIF) {
                titleTextView.setText(getString(R.string.view_reddit_gallery_activity_gif_label,
                        getArguments().getInt(EXTRA_INDEX) + 1, getArguments().getInt(EXTRA_MEDIA_COUNT)));
            } else {
                titleTextView.setText(getString(R.string.view_reddit_gallery_activity_image_label,
                        getArguments().getInt(EXTRA_INDEX) + 1, getArguments().getInt(EXTRA_MEDIA_COUNT)));
            }
            downloadImageView.setOnClickListener(view -> {
                if (isDownloading) {
                    return;
                }
                isDownloading = true;
                requestPermissionAndDownload();
            });
            shareImageView.setOnClickListener(view -> {
                if (media.mediaType == Post.Gallery.TYPE_GIF) {
                    shareGif();
                } else {
                    shareImage();
                }
            });
            wallpaperImageView.setOnClickListener(view -> {
                setWallpaper();
            });
        }

        return rootView;
    }

    private void loadImage() {
        imageView.showImage(Uri.parse(media.url));
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.view_reddit_gallery_image_or_gif_fragment, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_download_view_reddit_gallery_image_or_gif_fragment) {
            if (isDownloading) {
                return false;
            }
            isDownloading = true;
            requestPermissionAndDownload();
            return true;
        } else if (itemId == R.id.action_share_view_reddit_gallery_image_or_gif_fragment) {
            if (media.mediaType == Post.Gallery.TYPE_GIF) {
                shareGif();
            } else {
                shareImage();
            }
            return true;
        } else if (itemId == R.id.action_set_wallpaper_view_reddit_gallery_image_or_gif_fragment) {
            setWallpaper();
            return true;
        }

        return false;
    }

    private void requestPermissionAndDownload() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(activity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                // Permission is not granted
                // No explanation needed; request the permission
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE);
            } else {
                // Permission has already been granted
                download();
            }
        } else {
            download();
        }
    }

    private void download() {
        isDownloading = false;

        Intent intent = new Intent(activity, DownloadMediaService.class);
        intent.putExtra(DownloadMediaService.EXTRA_URL, media.url);
        intent.putExtra(DownloadMediaService.EXTRA_MEDIA_TYPE, media.mediaType == Post.Gallery.TYPE_GIF ? DownloadMediaService.EXTRA_MEDIA_TYPE_GIF: DownloadMediaService.EXTRA_MEDIA_TYPE_IMAGE);
        intent.putExtra(DownloadMediaService.EXTRA_FILE_NAME, media.fileName);
        intent.putExtra(DownloadMediaService.EXTRA_SUBREDDIT_NAME, subredditName);
        ContextCompat.startForegroundService(activity, intent);
        Toast.makeText(activity, R.string.download_started, Toast.LENGTH_SHORT).show();
    }

    private void shareImage() {
        glide.asBitmap().load(media.url).into(new CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                if (activity.getExternalCacheDir() != null) {
                    Toast.makeText(activity, R.string.save_image_first, Toast.LENGTH_SHORT).show();
                    SaveBitmapImageToFile.SaveBitmapImageToFile(mExecutor, new Handler(), resource, activity.getExternalCacheDir().getPath(),
                            media.fileName,
                            new SaveBitmapImageToFile.SaveBitmapImageToFileListener() {
                                @Override
                                public void saveSuccess(File imageFile) {
                                    Uri uri = FileProvider.getUriForFile(activity,
                                            BuildConfig.APPLICATION_ID + ".provider", imageFile);
                                    Intent shareIntent = new Intent();
                                    shareIntent.setAction(Intent.ACTION_SEND);
                                    shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                                    shareIntent.setType("image/*");
                                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                    startActivity(Intent.createChooser(shareIntent, getString(R.string.share)));
                                }

                                @Override
                                public void saveFailed() {
                                    Toast.makeText(activity,
                                            R.string.cannot_save_image, Toast.LENGTH_SHORT).show();
                                }
                            });
                } else {
                    Toast.makeText(activity,
                            R.string.cannot_get_storage, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {

            }
        });
    }

    private void shareGif() {
        Toast.makeText(activity, R.string.save_gif_first, Toast.LENGTH_SHORT).show();
        glide.asGif().load(media.url).listener(new RequestListener<GifDrawable>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<GifDrawable> target, boolean isFirstResource) {
                return false;
            }

            @Override
            public boolean onResourceReady(GifDrawable resource, Object model, Target<GifDrawable> target, DataSource dataSource, boolean isFirstResource) {
                if (activity.getExternalCacheDir() != null) {
                    SaveGIFToFile.saveGifToFile(mExecutor, new Handler(), resource, activity.getExternalCacheDir().getPath(), media.fileName,
                            new SaveGIFToFile.SaveGIFToFileAsyncTaskListener() {
                                @Override
                                public void saveSuccess(File imageFile) {
                                    Uri uri = FileProvider.getUriForFile(activity,
                                            BuildConfig.APPLICATION_ID + ".provider", imageFile);
                                    Intent shareIntent = new Intent();
                                    shareIntent.setAction(Intent.ACTION_SEND);
                                    shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                                    shareIntent.setType("image/*");
                                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                    startActivity(Intent.createChooser(shareIntent, getString(R.string.share)));
                                }

                                @Override
                                public void saveFailed() {
                                    Toast.makeText(activity,
                                            R.string.cannot_save_gif, Toast.LENGTH_SHORT).show();
                                }
                            });
                } else {
                    Toast.makeText(activity,
                            R.string.cannot_get_storage, Toast.LENGTH_SHORT).show();
                }
                return false;
            }
        }).submit();
    }

    private void setWallpaper() {
        if (media.mediaType != Post.Gallery.TYPE_GIF) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                SetAsWallpaperBottomSheetFragment setAsWallpaperBottomSheetFragment = new SetAsWallpaperBottomSheetFragment();
                Bundle bundle = new Bundle();
                bundle.putInt(SetAsWallpaperBottomSheetFragment.EXTRA_VIEW_PAGER_POSITION, activity.getCurrentPagePosition());
                setAsWallpaperBottomSheetFragment.setArguments(bundle);
                setAsWallpaperBottomSheetFragment.show(activity.getSupportFragmentManager(), setAsWallpaperBottomSheetFragment.getTag());
            } else {
                ((SetAsWallpaperCallback) activity).setToBoth(activity.getCurrentPagePosition());
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(activity, R.string.no_storage_permission, Toast.LENGTH_SHORT).show();
                isDownloading = false;
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED && isDownloading) {
                download();
            }
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activity = (ViewRedditGalleryActivity) context;
    }

    @Override
    public void onResume() {
        super.onResume();
        SubsamplingScaleImageView ssiv = imageView.getSSIV();
        if (ssiv == null || !ssiv.hasImage()) {
            imageView.showImage(Uri.parse(media.url));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        imageView.cancel();
        SubsamplingScaleImageView subsamplingScaleImageView = imageView.getSSIV();
        if (subsamplingScaleImageView != null) {
            subsamplingScaleImageView.recycle();
        }
    }
}
