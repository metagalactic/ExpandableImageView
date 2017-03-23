package com.github.sample;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.github.metagalactic.views.ScalableImageView;
import com.github.metagalactic2.views.ExpandableImageView;
import com.github.metagalactic2.views.ExpandableImageViewPagerItem;
import com.github.sample.appconfig.GlideConfigModule;

import java.util.ArrayList;
import java.util.List;

public class ExpandableImageViewDemoActivity extends AppCompatActivity {

    private static final int PRODUCT_IMAGES_PER_PAGE = 1;
    private static final float EXPANDED_VIEW_WIDTH = 1f;
    private static final float COLLAPSED_VIEW_WIDTH = 0.6f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expandable_image_view_demo);

        final Resources resources = getResources();
        ExpandableImageView expandableImageView = (ExpandableImageView) findViewById(R.id.expandable_image_view);

        expandableImageView.setPagerHeightMin(resources.getDimensionPixelSize(R.dimen.image_pager_default_height));
        expandableImageView.setPagingHeightMax(resources.getDimensionPixelSize(R.dimen.image_pager_default_height_expanded));

        expandableImageView.setEndPagesCentered(false);
        expandableImageView.setCollapsedViewWidth(COLLAPSED_VIEW_WIDTH);
        expandableImageView.setImageUrls(getImageStrings());
        expandableImageView.setCollapsedNumberOfItemsPerPage(PRODUCT_IMAGES_PER_PAGE);
        expandableImageView.setListener(new ExpandableImageView.OnImageEventListener() {
            @Override
            public void onImageClicked(int position) {

            }

            @Override
            public void onImagesSelected(int firstPosition, int numberOfItems) {

            }

            @Override
            public void onClearImageFromYourFavoriteImageLibrary(final ScalableImageView scalableImageView) {
                Glide.clear(scalableImageView);
            }

            @Override
            public void onLoadImageFromYourFavoriteImageLibrary(final ScalableImageView imageView,
                                                                final String imageUrl,
                                                                @Nullable final Drawable placeholder) {

                if (imageUrl.equals(imageView.getTag())) {
                    // We already have the image, no need to load it again.
                    return;
                }

                //If we don't clear the tag, Glide will crash as Glide do not want us to have tag on
                //image view
                // Clear the current tag and load the new image, The new tag will be set on success.
                imageView.setTag(null);

                Glide.with(imageView.getContext())
                        .load(imageUrl)
                        .thumbnail(GlideConfigModule.SIZE_MULTIPLIER)
                        .placeholder(placeholder)
                        .dontAnimate()
                        .listener(new RequestListener<String, GlideDrawable>() {
                            @Override
                            public boolean onException(Exception e,
                                                       String model,
                                                       Target<GlideDrawable> target,
                                                       boolean isFirstResource) {
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(GlideDrawable resource,
                                                           String model,
                                                           Target<GlideDrawable> target,
                                                           boolean isFromMemoryCache,
                                                           boolean isFirstResource) {
                                // Set the image URL as a tag so we know that the image with this URL has
                                // successfully loaded
                                imageView.setTag(imageUrl);
                                return false;
                            }
                        })
                        .into(imageView);
            }
        });
        expandableImageView.refresh();
    }

    private List<ExpandableImageViewPagerItem> getImageStrings() {
        List<ExpandableImageViewPagerItem> images = new ArrayList<>();
        images.add(ExpandableImageViewPagerItem.create("http://assets.myntassets.com/w_720,q_90/v1/images/style/properties/Ira-Soleil-Women-Black-Printed-Kurti_c86a54f87d73cf9b6516ea35a2f0c98c_images.jpg"));
        images.add(ExpandableImageViewPagerItem.create("http://assets.myntassets.com/w_720,q_90/v1/images/style/properties/Ira-Soleil-Women-Black-Printed-Kurti_90553860893fd3b40a4f6781f50b3aaa_images.jpg"));
        images.add(ExpandableImageViewPagerItem.create("http://assets.myntassets.com/w_720,q_90/v1/images/style/properties/Ira-Soleil-Women-Black-Printed-Kurti_8009a64be1e7e4b4c7b67019552d57e6_images.jpg"));
        images.add(ExpandableImageViewPagerItem.create("http://assets.myntassets.com/w_720,q_90/v1/images/style/properties/Ira-Soleil-Women-Black-Printed-Kurti_c86a54f87d73cf9b6516ea35a2f0c98c_images.jpg"));
        return images;
    }

}
