package pl.droidsonroids.letmetakeaselfie.loader;

import android.support.annotation.NonNull;
import android.widget.ImageView;

import com.squareup.picasso.Picasso;

import pl.droidsonroids.letmetakeaselfie.R;

public class PicassoImageLoader implements ImageLoader {
    @Override
    public void loadImage(@NonNull ImageView imageView, @NonNull String imageUrl) {

        Picasso.with(imageView.getContext())
                .load(imageUrl).fit()
                .placeholder(R.drawable.ic_face_black_36dp)
                .centerCrop()
                .into(imageView);

    }
}
