package com.github.metagalactic2.adapter;

import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import com.github.metagalactic.views.ScalableImageView;
import com.github.metagalactic2.views.ExpandableImageViewPagerItem;
import com.github.metagalactic2.views.R;

import java.util.List;

import zeta.android.utils.lang.StringUtils;

/**
 * Adapter to be used with an ExpandableImageViewPager. Each item is an ImageView which matches the
 * parent's layout width and height by default. An override for the item width can be specified if
 * desired.
 */
public class ExpandableImageViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String CONTENT_DESCRIPTION_FORMAT = "%s %s of %s";
    private static final String CONTENT_DESCRIPTION_WITH_IMG_DESCRIPTION_FORMAT = "%s %s %s of %s";

    private boolean mIsCollapsed = false;
    private Drawable mHeroImagePlaceholderDrawable;
    private Integer mItemPadding;
    private Integer mViewWidth;
    private List<ExpandableImageViewPagerItem> mData;
    private String mBaseContentDescription;

    private OnImageClickedListener mListener;

    /**
     * Listener interface for receiving updates when an item has been clicked
     */
    public interface OnImageClickedListener {

        void onImageClicked(int position);

        void onClearImageFromYourFavoriteImageLibrary(ScalableImageView scalableImageView);

        void onLoadImageFromYourFavoriteImageLibrary(ScalableImageView scalableImageView,
                                                     String imageUrl,
                                                     @Nullable Drawable placeholder);
    }

    public ExpandableImageViewAdapter(List<ExpandableImageViewPagerItem> imageUrls) {
        mData = imageUrls;
    }

    /**
     * @param drawable a drawable that may be used as a placeholder for the very first image while
     *                 it loads. Use of this drawable allows for transitions involving the first
     *                 image to be much more seamless. Note that this placeholder will be cleared
     *                 immediately after the initial request completes (independent of the success
     *                 of the request).
     */
    public void setHeroImagePlaceholderDrawable(Drawable drawable) {
        mHeroImagePlaceholderDrawable = drawable;
    }

    public void setCollapsed(boolean isCollapsed) {
        mIsCollapsed = isCollapsed;
    }

    public void setViewWidth(int viewWidth) {
        mViewWidth = viewWidth;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (mItemPadding == null) {
            mItemPadding = parent.getContext().getResources()
                    .getDimensionPixelSize(R.dimen.expandable_image_view_pager_item_padding);
        }

        ScalableImageView view = new ScalableImageView(parent.getContext());
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        view.setLayoutParams(params);
        view.setPadding(mItemPadding, 0, mItemPadding, 0); // Just pad the sides
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, final int position) {
        final ScalableImageView imageView = (ScalableImageView) viewHolder.itemView;
        // Update the view width
        if (mViewWidth != null && mViewWidth > 0) {
            // Update the view's width. The new value will get picked up in the next layout pass
            imageView.getLayoutParams().width = mViewWidth;
        }

        // Only allow scaling in expanded state (and, as an extra precaution, only when TalkBack is
        // not currently enabled)
        imageView.setScalable(!mIsCollapsed);

        // Reset any scaled state
        imageView.resetScaling();

        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (imageView.isScaled()) {
                    imageView.resetScaling(true);
                }

                if (mListener != null) {
                    mListener.onImageClicked(position);
                }
            }
        });

        imageView.setContentDescription(getContentDescriptionForPosition(position));

        final ExpandableImageViewPagerItem imageItem = mData.get(position);
        if (imageItem == null) {
            if (mListener != null) {
                mListener.onClearImageFromYourFavoriteImageLibrary(imageView);
            }
            return;
        }

        Drawable placeholder = null;
        if (position == 0) {
            placeholder = mHeroImagePlaceholderDrawable;
        }

        if (mListener != null) {
            mListener.onLoadImageFromYourFavoriteImageLibrary(imageView,
                    imageItem.imageUrl(),
                    placeholder);
        }
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    private String getContentDescriptionForPosition(int position) {
        ExpandableImageViewPagerItem item = mData.get(position);

        if (StringUtils.isNotNullOrEmpty(item.imageContentDescription())) {
            return String.format(CONTENT_DESCRIPTION_WITH_IMG_DESCRIPTION_FORMAT,
                    item.imageContentDescription(), mBaseContentDescription,
                    position + 1, mData.size());
        } else {
            return String.format(CONTENT_DESCRIPTION_FORMAT, mBaseContentDescription, position + 1,
                    mData.size());
        }
    }

    public void setBaseContentDescription(String baseContentDescription) {
        mBaseContentDescription = baseContentDescription;
    }

    public void setOnImageClickedListener(OnImageClickedListener listener) {
        mListener = listener;
    }

    // Just use empty view holder, as all we will need is the root item
    private static class ViewHolder extends RecyclerView.ViewHolder {
        ViewHolder(View itemView) {
            super(itemView);
        }
    }
}
