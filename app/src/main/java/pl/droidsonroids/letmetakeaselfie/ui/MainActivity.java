package pl.droidsonroids.letmetakeaselfie.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import pl.droidsonroids.letmetakeaselfie.R;
import pl.droidsonroids.letmetakeaselfie.api.SelfieApiManager;
import pl.droidsonroids.letmetakeaselfie.api.response.GetSelfiesResponse;
import pl.droidsonroids.letmetakeaselfie.api.response.PostSelfieResponse;
import pl.droidsonroids.letmetakeaselfie.model.Selfie;
import pl.droidsonroids.letmetakeaselfie.ui.adapter.SelfieAdapter;
import pl.droidsonroids.letmetakeaselfie.ui.dialog.UserNameDialogFragment;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class MainActivity extends AppCompatActivity implements UserNameDialogFragment.OnUserNameTypedListener {

    @Bind(R.id.refresh_layout)
    SwipeRefreshLayout swipeRefreshLayout;
    @Bind(R.id.recycler_view)
    RecyclerView recyclerView;
    private SelfieAdapter selfieAdapter;
    private static final String KEY_FILEPATH = "filepath";
    private static final int TAKE_PICTURE = 0;
    private boolean flagOk = false;
    private String inState;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            inState = savedInstanceState.getString(KEY_FILEPATH);
        }
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);

        initSelfieAdapter();
        setupSwipeRefreshLayout();
        setupRecyclerView();
        postRefresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (flagOk) {
            new UserNameDialogFragment().show(getSupportFragmentManager(), null);
        }
    }

    private void initSelfieAdapter() {
        selfieAdapter = new SelfieAdapter();
    }

    private void setupSwipeRefreshLayout() {
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refreshSelfies();
            }
        });
    }

    private void setupRecyclerView() {
        recyclerView.setAdapter(selfieAdapter);
        recyclerView.setLayoutManager(new GridLayoutManager(this, getResources().getInteger(R.integer.grid_span_count)));
        recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(final Rect outRect, final View view, final RecyclerView parent, final RecyclerView.State state) {
                int offset = getResources().getDimensionPixelSize(R.dimen.grid_padding);
                outRect.set(offset, offset, offset, offset);
            }
        });
    }

    private void postRefresh() {
        swipeRefreshLayout.post(new Runnable() {
            @Override
            public void run() {
                swipeRefreshLayout.setRefreshing(true);
                refreshSelfies();
            }
        });
    }

    private void refreshSelfies() {
        SelfieApiManager.getInstance().getSelfies(new Callback<GetSelfiesResponse>() {
            @Override
            public void success(final GetSelfiesResponse getSelfiesResponse, final Response response) {
                swipeRefreshLayout.setRefreshing(false);
                selfieAdapter.setSelfies(getSelfiesResponse.getSelfies());
            }

            @Override
            public void failure(final RetrofitError error) {
                swipeRefreshLayout.setRefreshing(false);
                Toast.makeText(MainActivity.this, R.string.selfies_load_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (requestCode == TAKE_PICTURE && resultCode == RESULT_OK) {
            exifRotate();
            flagOk = true;
        }
    }

    private void openCamera() {
        Intent cameraIntent = new Intent("android.media.action.IMAGE_CAPTURE");
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            String filepath = new Date().toString() + ".jpeg";
            inState = filepath;
            File file = new File(Environment.getExternalStorageDirectory(), inState);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(file));
            startActivityForResult(cameraIntent, TAKE_PICTURE);
        } else {
            Toast.makeText(getApplicationContext(), R.string.no_camera, Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressWarnings("unused")
    @OnClick(R.id.button_add)
    public void addSelfie() {
        openCamera();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(KEY_FILEPATH, inState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onUserNameTyped(@NonNull final String userName) {
        Selfie selfie = new Selfie();
        selfie.setUserName(userName);

        selfie.setPhotoFile(new File(Environment.getExternalStorageDirectory(), inState));

        SelfieApiManager.getInstance().postSelfie(selfie, new Callback<PostSelfieResponse>() {
            @Override
            public void success(final PostSelfieResponse postSelfieResponse, final Response response) {
                refreshSelfies();
            }

            @Override
            public void failure(final RetrofitError error) {
                Toast.makeText(MainActivity.this, R.string.selfie_upload_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void exifRotate() {
        ExifInterface exif = null;
        File file = new File(Environment.getExternalStorageDirectory(), inState);
        String path = file.getPath();
        try {
            exif = new ExifInterface(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (exif != null) {
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED);
            Bitmap picture = BitmapFactory.decodeFile(path);
            picture = rotatePicture(picture, orientation);
            try {
                FileOutputStream fileOutputStream = new FileOutputStream(file);
                if (picture != null) {
                    picture.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream);
                }
                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Bitmap rotatePicture(Bitmap picture, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_NORMAL:
                return picture;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90);
                break;
            default:
                return picture;
        }
        try {
            Bitmap pictureRotated = Bitmap.createBitmap(picture, 0, 0, picture.getWidth(), picture.getHeight(), matrix, true);
            picture.recycle();
            return pictureRotated;
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            return null;
        }
    }
}
