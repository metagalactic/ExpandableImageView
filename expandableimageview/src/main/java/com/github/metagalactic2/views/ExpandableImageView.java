package com.github.metagalactic2.views;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.github.metagalactic.views.ScalableImageView;
import com.github.metagalactic2.adapter.ExpandableImageViewAdapter;
import com.github.metagalactic2.layout_manager.CustomLinearLayoutManager;

import java.util.ArrayList;
import java.util.List;

/**
 * A RecyclerView implementation which acts similarly to a ViewPager, but which allows for multiple
 * items per page to be specified dynamically and which features a behavior that allows for items
 * to go into a fullscreen-type paging mode, with a smooth animation between the expanded and
 * collapsed states.
 */
public class ExpandableImageView extends RecyclerView implements
        ExpandableImageViewAdapter.OnImageClickedListener {

    private static final String TAG = ExpandableImageView.class.getSimpleName();

    // Time to animate a height change (in milliseconds)
    private static final int HEIGHT_ANIMATION_TIME = 300;

    // Time to animate snapping-to-place paging behavior (in milliseconds)
    private static final int PAGING_ANIMATION_TIME = 500;

    // Time to wait after smoother scroller stops before notifying listener (in milliseconds)
    private static final int PAGING_LISTENER_DELAY = 100;

    // Number of "screens" worth of additional pages to eagerly load.
    private static final int NUM_EAGERLY_LOADED_SCREENS = 1;

    private boolean mIsAnimatingExpandedState = false;
    private boolean mIsAnimatingLeftEdge = false;
    private boolean mIsCollapsed = true;
    private boolean mAreEndPagesCentered = false;
    private boolean mExpandDisabled = false;

    private int mDisplayWidth;
    private int mHeightMax;
    private int mHeightMin;

    // The number of items per page when in the collapsed state. Defaults to 1.
    private int mItemsPerPage = 1;

    // The current number of items per page. This can vary between 1 for the expanded state and
    // the user-supplied value for the number of items in the collapsed state.
    private int mItemsPerPageCurrent = mItemsPerPage;

    // The fraction of the total width that each item will take up in the collapsed state. Defaults
    // to half the screen width
    private float mViewWidthFractionMin = 0.5f;

    // The fraction of the total width each item will take up in the expanded state. This is fixed
    // at 1.
    private float mViewWidthFractionMax = 1f;

    private float mViewWidthFractionCurrent = mViewWidthFractionMin;

    /**
     * Used during the expand/collapse animation as an indication of where the anchor view should
     * be positioned at that point in the animation.
     */
    private Integer mDesiredAnchorViewLeftValue;

    /**
     * Used during the expand/collapse animation as an indication of what the final left position of
     * the anchor view should be when the animation completes.
     */
    private Integer mFinalAnchorViewLeftValue;

    /**
     * Hold the position of the "first selected item" that was saved in the saved state bundle. This
     * is used to communicate the previous "selected" position to the view's listener if
     * notifyListener is called before the new views have been successfully attached. Note that this
     * value  should always be null if new views are already attached.
     */
    private Integer mFirstSelectedItemSaved;

    /**
     * Runnable for refreshing the current state of this view and its adapter. Any listeners of the
     * current page will be notified of the new state.
     */
    private Runnable mRefreshCurrentStateRunnable = new Runnable() {
        @Override
        public void run() {
            // Update adapter state
            refresh();

            // Trigger listener
            notifyListener();
        }
    };

    /**
     * The underlying configuration to use when loading the images as bitmaps
     */
    private Bitmap.Config mBitmapConfig = Bitmap.Config.ARGB_8888;

    /**
     * Used during the expand/collapse animation to determine the current scroll state. This is
     * necessary for deciding how much scrolling needs to be performed to place the views in their
     * desired locations.
     */
    private View mAnchorView;

    private ExpandableImageViewAdapter mAdapter;
    private GestureDetector mFlingDetector;
    private OnImageEventListener mListener;
    private CustomLinearLayoutManager mLayoutManager;
    private List<ExpandableImageViewPagerItem> mData = new ArrayList<>();
    private String mBaseContentDescription;

    /**
     * Listener interface to knowing when an image has been clicked or when one or more images
     * have been "selected", which refers to the fact that images can be grouped into pages that
     * can be selected in bulk.
     */
    public interface OnImageEventListener {

        /**
         * Callback for when an image is clicked
         *
         * @param position the absolute position of the image in the dataset
         */
        void onImageClicked(final int position);

        /**
         * Callback for when an image or set of images has been "selected", which refers to when the
         * pager settles in on a "page", which is a set of items grouped by the current number of
         * items per page.
         *
         * @param firstPosition the position of the first item in the selected set of pages
         * @param numberOfItems the number of items in the selected set of pages (may be 1)
         */
        void onImagesSelected(final int firstPosition, final int numberOfItems);

        void onClearImageFromYourFavoriteImageLibrary(final ScalableImageView scalableImageView);

        void onLoadImageFromYourFavoriteImageLibrary(final ScalableImageView scalableImageView,
                                                     final String imageUrl,
                                                     @Nullable final Drawable placeholder);
    }

    public ExpandableImageView(Context context) {
        super(context);
        init();
    }

    public ExpandableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ExpandableImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    @Override
    public void onImageClicked(final int position) {
        if (mListener != null) {
            mListener.onImageClicked(position);
        }

        if (mExpandDisabled) {
            // Don't toggle state
            return;
        }

        toggleExpandedState(position);
    }

    @Override
    public void onClearImageFromYourFavoriteImageLibrary(ScalableImageView scalableImageView) {
        if (mListener != null) {
            mListener.onClearImageFromYourFavoriteImageLibrary(scalableImageView);
        }
    }

    @Override
    public void onLoadImageFromYourFavoriteImageLibrary(ScalableImageView scalableImageView,
                                                        String imageUrl,
                                                        @Nullable Drawable placeholder) {
        if (mListener != null) {
            mListener.onLoadImageFromYourFavoriteImageLibrary(scalableImageView, imageUrl,
                    placeholder);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        // If we're currently settling due to an animation, just return true to block new dragging
        // events from happening until its over.
        return (getScrollState() == SCROLL_STATE_SETTLING) || super.onInterceptTouchEvent(e);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        if (mAnchorView == null || mDesiredAnchorViewLeftValue == null ||
                mFinalAnchorViewLeftValue == null || mLayoutManager == null) {
            // No repositioning needs to occur
            return;
        }

        // As part of the expand/collapse animation we need to reposition the views by performing
        // a scroll operation. We'll manually offset them by using the layout manager to avoid calls
        // to "fill" (which would happen if we used scrollBy) because that would trigger
        // "onBindViewHolder" calls in the adapter, which let's be honest, nobody wants right now.
        int scrollBy = mAnchorView.getLeft() - mDesiredAnchorViewLeftValue;
        mLayoutManager.offsetChildrenHorizontal(-scrollBy);

        // Check to see if the animation should be considered over. If so, refresh the views state
        if (!mIsAnimatingLeftEdge) {
            mIsAnimatingExpandedState = false;
            mFinalAnchorViewLeftValue = null;
            mDesiredAnchorViewLeftValue = null;
            mAnchorView = null;

            mIsCollapsed = !mIsCollapsed;

            // Turn the eager loading back on
            mLayoutManager.setNumberOfScreensToEagerLoad(NUM_EAGERLY_LOADED_SCREENS);

            // We need to call refresh() here and notify our listener of the new view state. This
            // will not work properly if called here in onLayout() but we want to do it soon after
            // this method completes, so we will simply post a runnable to handle it.
            post(mRefreshCurrentStateRunnable);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Intercept fling events if necessary
        if (mFlingDetector.onTouchEvent(event)) {
            // Don't consume the event, but don't pass it along either
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_CANCEL:
                // Fall through
            case MotionEvent.ACTION_UP:
                if (getScrollState() == SCROLL_STATE_DRAGGING) {
                    // Try to snap for an up/cancel only if we were previously dragging.
                    snapToPosition(null, false);
                }
                break;
        }

        return super.onTouchEvent(event);
    }

    /**
     * This can be used to clear any adapter data and begin a fresh view state upon setting new
     * data. The primary use for this is to clear things like current scroll position and selected
     * states.
     */
    public void clearData() {
        mData.clear();
        // Reset the adapter
        setAdapter(mAdapter);
    }

    /**
     * When set to true, clicking on the pager items will not result in the expansion of the pager.
     *
     * @param disable true if expansion should be disabled
     */
    public void disableViewExpansion(boolean disable) {
        mExpandDisabled = disable;
    }

    /**
     * @param position the position of the item to check
     * @return the position of the right edge of the view with the given position (in absolute terms
     * of the un-scrolled RecyclerView)
     */
    private int getAbsoluteRightEdgeForPosition(int position) {
        return (position + 1) * getViewWidth();
    }

    private int getExtraLeftPaddingForPosition(int position) {
        return getExtraLeftPaddingForPosition(position, mIsCollapsed);
    }

    private int getExtraLeftOffsetForPosition(int position, boolean isForCollapsedState) {
        // Special case: we need to make sure we don't translate the last item too far to the left
        // (because we are translating with offsetChildrenHorizontal and not scrollBy). In this case
        // we should return the amount needed to right-align this item. Also, we need to make sure
        // the right edge of the item is not actually on the first page, otherwise we'll have
        // problems at that end instead.
        if (isForCollapsedState && !mAreEndPagesCentered && position == getLastPosition()
                && getAbsoluteRightEdgeForPosition(position) > mDisplayWidth) {
            return mDisplayWidth - getViewWidthMin();
        }

        // Just use the extra padding
        return getExtraLeftPaddingForPosition(position, isForCollapsedState);
    }

    private int getExtraLeftPaddingForPosition(int position, boolean isForCollapsedState) {
        return isPaddingVisibleForItem(position, isForCollapsedState)
                ? getPaddingToCenterFirstItem() : 0;
    }

    private int getFirstPagePagingOffset() {
        return mAreEndPagesCentered ? 1 : 0;
    }

    /**
     * @return the position of the first selected item (may be one of several in a "page")
     */
    public int getFirstSelectedItem() {
        if (mLayoutManager.getChildCount() == 0) {
            if (mFirstSelectedItemSaved != null && mData.size() > mFirstSelectedItemSaved) {
                // We have a saved position that is compatible with out data, use that until it is
                // cleared
                return mFirstSelectedItemSaved;
            }

            // Children have not been added yet or we are currently in a layout pass, just return
            // first possible position
            return 0;
        }

        // Clear any previously saved positions
        mFirstSelectedItemSaved = null;
        return mLayoutManager.findFirstVisibleItemPosition();
    }

    private int getLastPosition() {
        return mAdapter.getItemCount() - 1;
    }

    private Animator getLayoutChangeAnimator(final int initialHeight, final int finalHeight,
                                             final int initialPadding, final int finalPadding) {
        final boolean isCollapsed = mIsCollapsed;
        // Animate the height and widths of the view and its children
        ValueAnimator animator = ValueAnimator.ofInt(initialHeight, finalHeight);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                // Update the total view height
                int currentHeight = (Integer) animation.getAnimatedValue();
                ViewGroup.LayoutParams params = ExpandableImageView.this.getLayoutParams();
                params.height = currentHeight;

                // Update children widths
                float rawFraction = animation.getAnimatedFraction();
                float fraction = rawFraction;
                if (!isCollapsed) {
                    // Flip the fraction when collapsing
                    fraction = 1f - fraction;
                }
                setViewWidthFractionCurrent(mViewWidthFractionMin +
                        fraction * (mViewWidthFractionMax - mViewWidthFractionMin), isCollapsed);

                // Rather than calling notifyDataSetChanged, we will just change the width of the
                // available views to avoid any unnecessary view creations
                for (int i = 0; i < mLayoutManager.getChildCount(); i++) {
                    mLayoutManager.getChildAt(i).getLayoutParams().width = getViewWidth();
                }

                // We need to trigger a layout pass here. If we are updating the padding then that
                // will happen automatically, otherwise we will force the pass manually.
                if (mAreEndPagesCentered && (initialPadding != finalPadding)) {
                    int currentPadding = Math.round(initialPadding + rawFraction * (finalPadding -
                            initialPadding));
                    setPadding(currentPadding, 0, currentPadding, 0);
                } else {
                    requestLayout();
                }
            }
        });

        return animator;
    }

    private Animator getLeftEdgeAnimator(View targetView, int startValue, int endValue) {
        mAnchorView = targetView;
        mDesiredAnchorViewLeftValue = startValue;
        mFinalAnchorViewLeftValue = endValue;
        ValueAnimator animator = ValueAnimator.ofInt(startValue, endValue);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                // This is the left edge value we should be at at this point in the animation
                mDesiredAnchorViewLeftValue = (int) animation.getAnimatedValue();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                mIsAnimatingLeftEdge = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mIsAnimatingLeftEdge = false;
            }
        });
        return animator;
    }

    private int getLeftEndValueForPosition(int position) {
        int extraOffset;
        int relativePosition = getRelativePagingPosition(position, true);
        if (position == 0 || position == getLastPosition() || relativePosition != 0) {
            // If the first position is 0 or if we are not collapsing back to the beginning of the
            // page, we should check for extra padding
            extraOffset = getExtraLeftOffsetForPosition(position, true);
        } else {
            // We are going to the beginning of a new page that is not the first...we don't need to
            // take padding into account
            extraOffset = 0;
        }

        return Math.round(extraOffset + relativePosition * mDisplayWidth * mViewWidthFractionMin);
    }

    private int getNewTargetPosition(int firstVisiblePosition, int currentLeftOffset,
                                     Float velocityX, boolean isFling) {
        int relativePagingPosition = getRelativePagingPosition(firstVisiblePosition);

        int itemsPerPage = mItemsPerPageCurrent;
        if (firstVisiblePosition == 0 && mAreEndPagesCentered) {
            // If we are at the first position and it is centered, the relevant items per page here
            // is just a single item.
            itemsPerPage = 1;
        }

        // Given the first visible position and the relative position, decide what the current,
        // next, and previous "pages" should be. Note that for small scrolling movements toward the
        // left of the screen, the "current" position will be based on the views seen before the
        // scroll, while for scrolling movements toward the right of the screen the "current"
        // position will actually be based on those sets of views that were previously off to the
        // left. This change is due to how the first visible position changes when scrolling.
        int currentPagePosition = firstVisiblePosition - relativePagingPosition;
        int nextPagePosition = currentPagePosition + itemsPerPage;
        int previousPagePosition = currentPagePosition - itemsPerPage;

        // If flinging, always snap to the next page/set of pages in that direction. Otherwise, base
        // the snapping behavior on the crossing of a threshold.
        int targetPosition;
        if (isFling) {
            // We define positive values for "sign" as those that will increase the page position
            float sign = -Math.signum(velocityX);
            if (sign > 0) {
                // Flinging toward the left, so show more items on the next page to the right
                targetPosition = nextPagePosition;
            } else {
                // Flinging toward the right, so the "current" page here is actually what is
                // currently on the left, so show that.
                targetPosition = currentPagePosition;
            }
        } else {
            // The threshold for snapping while scrolling will be based on half the current individual
            // view size.
            int snapThreshold = getViewWidth() / 2;
            if (currentLeftOffset < 0) {
                // Item is (partially) off the screen to the left. This is almost always the case.
                // Note that when scrolling the the right, the first visible item is actually from
                // a previous set of pages, and while scrolling to the left it is the "current" set
                if (Math.abs(currentLeftOffset) > snapThreshold) {
                    targetPosition = nextPagePosition;
                } else {
                    targetPosition = currentPagePosition;
                }
            } else {
                // Item is on the screen off to the right
                if (Math.abs(currentLeftOffset) > snapThreshold) {
                    targetPosition = previousPagePosition;
                } else {
                    targetPosition = currentPagePosition;
                }
            }
        }

        // If the target position lies outside of the bounds of the current set of items, fix it to
        // a valid endpoint
        if (targetPosition < 0) {
            targetPosition = 0;
        } else if (targetPosition > getLastPosition()) {
            targetPosition = getLastPosition();
        }

        if (!isValidPagingPosition(targetPosition)) {
            // Note that these warnings should not be seen with proper usage
            Log.e(TAG, "Target position set to invalid value : " + targetPosition);
            targetPosition = getValidPagingPosition(targetPosition);
            Log.e(TAG, "Recalculated target position : " + targetPosition);
        }

        return targetPosition;
    }

    /**
     * @return the amount of padding needed to shift the center of the first item to the center of
     * the screen
     */
    private int getPaddingToCenterFirstItem() {
        float halfViewWidth = (getViewWidthMin() / 2f);
        float numHalfViewWidthsToCenter = (mDisplayWidth / halfViewWidth) / 2;
        return Math.round((numHalfViewWidthsToCenter - 1) * halfViewWidth);
    }

    /**
     * Given an absolute position in the data set, return the relative position within the item's
     * "page" for the current expanded/collapsed state.
     *
     * @param position the absolute position of an item
     * @return the item's relative position in its "page" of data.
     */
    private int getRelativePagingPosition(int position) {
        return getRelativePagingPosition(position, mIsCollapsed);
    }

    /**
     * Given an absolute position in the data set, return the relative position within the item's
     * "page" for the given expanded/collapsed state.
     *
     * @param position              the absolute position of an item
     * @param findForCollapsedState true if looking for the paging position in the collapsed state
     * @return the item's relative position in its "page" of data.
     */
    private int getRelativePagingPosition(int position, boolean findForCollapsedState) {
        if (findForCollapsedState) {
            if (position == 0) {
                // First item is always the first item of its page
                return 0;
            }

            // Get position within a "page". For example, if paging in groups of two, any even position
            // will have a relative position of 0, while any odd position will have a relative position
            // of 1 (if the paging offset is zero).
            return (position - getFirstPagePagingOffset()) % mItemsPerPage;
        } else {
            // Always one item per page when in expanded mode
            return 0;
        }
    }

    /**
     * Given a position, returns the number of items that should be consider to be selected as part
     * of its "page". The position must be a valid paging position, otherwise the next best position
     * will be used.
     *
     * @param position the position of the item in question
     * @return the number of items that belong to that item's "page"
     */
    public int getSelectableItemsPerPageForPosition(int position) {
        if (!isValidPagingPosition(position)) {
            Log.e(TAG, "Attempted to get selectable items for invalid paging position : "
                    + position);
            // Just include the invalid page
            return 1;
        }

        if (position == 0 && mIsCollapsed && mAreEndPagesCentered) {
            // The first page is a page of 1 item here
            return 1;
        } else if (position + mItemsPerPageCurrent > mAdapter.getItemCount()) {
            // We are at a position where the "page" isn't full, so the number of selectable items
            // should be reduced.
            return mAdapter.getItemCount() - position;
        } else {
            // We are safe to return the full number of items;
            return mItemsPerPageCurrent;
        }
    }

    /**
     * Rounds down to the closest paging position.
     *
     * @param position the starting position to round from
     * @return a valid target position less than or equal to the given position
     */
    private int getValidPagingPosition(int position) {
        return position - getRelativePagingPosition(position);
    }

    /**
     * @return the current width (in pixels) of each child views of the pager
     */
    public int getViewWidth() {
        return Math.round(mDisplayWidth * mViewWidthFractionCurrent);
    }

    /**
     * @return the width (in pixels) of each child views of the pager in the collapsed state
     */
    public int getViewWidthMin() {
        return Math.round(mDisplayWidth * mViewWidthFractionMin);
    }

    private void init() {
        // Save display width
        final Resources resources = getResources();
        final Context context = getContext();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        mDisplayWidth = metrics.widthPixels;

        // Setup adapter and layout manager
        mAdapter = new ExpandableImageViewAdapter(mData);
        mAdapter.setOnImageClickedListener(this);
        mLayoutManager = new CustomLinearLayoutManager(context, LinearLayoutManager.HORIZONTAL,
                false, NUM_EAGERLY_LOADED_SCREENS);
        setAdapter(mAdapter);
        setLayoutManager(mLayoutManager);

        // Initialize adapter state
        updateAdapterState();

        // Disable clip to padding in case the first image needs to be centered
        setClipToPadding(false);

        // Get default base content description
        mBaseContentDescription = resources
                .getString(R.string.app_name);

        // Setup fling detector
        mFlingDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent event1, MotionEvent event2,
                                   float velocityX, float velocityY) {
                snapToPosition(velocityX, true);
                return true;
            }
        });
    }

    private boolean isPaddingVisibleForItem(int position) {
        return isPaddingVisibleForItem(position, mIsCollapsed);
    }

    private boolean isPaddingVisibleForItem(int position, boolean isForCollapsedState) {
        if (!isForCollapsedState || !mAreEndPagesCentered) {
            // There is no padding so this is always false
            return false;
        }

        if (position == getLastPosition()) {
            // Last position is always centered
            return true;
        }

        // The padding is visible for any item on screen when the first item is centered. That
        // includes the first item and the other views on screen with it (even if only partially).
        int halfViewWidth = (getViewWidthMin() / 2);
        int numHalfViewWidthsToCenter = (mDisplayWidth / halfViewWidth) / 2;
        long numViewsToRightOfCenterItem = Math.round(Math.ceil(numHalfViewWidthsToCenter / 2f));
        return (position <= numViewsToRightOfCenterItem);
    }

    private boolean isValidPagingPosition(int position) {
        if (position == 0) {
            // The first position is always valid
            return true;
        }

        return ((position - getFirstPagePagingOffset()) % mItemsPerPageCurrent == 0);
    }

    // Convenience method for notifying the adapter of data set changes
    private void notifyDataSetChanged() {
        mAdapter.notifyDataSetChanged();
    }

    /**
     * Triggers a call to the views listener. This can be called manually to get updates on the
     * current page information.
     */
    public void notifyListener() {
        notifyListenerForPosition(getFirstSelectedItem());
    }

    private void notifyListenerForPosition(int position) {
        int numberOfItems = getSelectableItemsPerPageForPosition(position);
        if (mListener != null) {
            mListener.onImagesSelected(position, numberOfItems);
        }
    }

    /**
     * This method must be called anytime a substantive change is made to the configuration of the
     * pager, which includes changes in:
     * <p>
     * - number of items per page
     * - width of views
     * - pager minimum/maximum height values
     * - disabling of view expansion
     * - whether or not the first item should be centered
     * - the current expanded state
     * <p>
     * Failure to call this method when needed may result in unusual behavior.
     */
    public void refresh() {
        updateViewWidthFractionCurrent();
        updateCurrentPagingViewNumber();
        updateAdapterState();
        updateContentDescription();
        updatePadding();
        updateViewHeight();
        notifyDataSetChanged();
    }

    /**
     * Set the base content description. The full content description will then be read as:
     * base description + current item number + "of" + total item count
     * Ex: "Image 5 of 30"
     *
     * @param baseContentDescription the base description for each item for accessibility purposes
     */
    public void setBaseContentDescription(String baseContentDescription) {
        mBaseContentDescription = baseContentDescription;
        updateContentDescription();
    }

    /**
     * Set the configuration for loading the underlying bitmap for each image. This may be used, for
     * example, to sacrifice image quality to reduce memory usage. The default is ARGB_8888.
     *
     * @param bitmapConfig the configuration to use
     */
    public void setBitmapConfig(@NonNull Bitmap.Config bitmapConfig) {
        mBitmapConfig = bitmapConfig;
    }

    /**
     * Allows the current expanded state of the view to be set manually. By default the view begins
     * in the collapsed state.
     *
     * @param collapsed true if the view should be set to collapsed state, false if it should be set
     *                  to the expanded state
     */
    public void setCollapsed(boolean collapsed) {
        mIsCollapsed = collapsed;
    }

    /**
     * Sets the number of views per "page" when in the collapsed state. This number determines which
     * views are logically grouped together when snapping between pages. Note that number of items
     * per page is always set to 1 when in the expanded state.
     *
     * @param itemsPerPage the number of items per page when in the collapsed state
     */
    public void setCollapsedNumberOfItemsPerPage(int itemsPerPage) {
        mItemsPerPage = itemsPerPage;
    }

    /**
     * Sets the individual item view width when in the collapsed state (as a fraction of the total
     * screen width). Note that views always take the maximum width in the expanded state.
     *
     * @param fraction the fraction of the screen width
     */
    public void setCollapsedViewWidth(float fraction) {
        mViewWidthFractionMin = fraction;
    }

    /**
     * @param drawable a drawable that may be used as a placeholder for the very first image while
     *                 it loads. Use of this drawable allows for transitions involving the first
     *                 image to be much more seamless. Note that this placeholder will be cleared
     *                 immediately after the initial request completes (independent of the success
     *                 of the request).
     */
    public void setHeroImagePlaceholderDrawable(Drawable drawable) {
        mAdapter.setHeroImagePlaceholderDrawable(drawable);
    }

    /**
     * Sets the data used for this image pager.
     *
     * @param imageUrls the list of images to display
     */
    public void setImageUrls(List<ExpandableImageViewPagerItem> imageUrls) {
        mData.clear();
        mData.addAll(imageUrls);
    }

    /**
     * If set to true, the first item will be centered and part of its own page. If the number of
     * items per page is currently greater than 1, pages will be grouped beginning with the second
     * item.
     *
     * @param endPagesCentered true if the first and last pages should be centered on their own
     *                         pages, false otherwise
     */
    public void setEndPagesCentered(boolean endPagesCentered) {
        mAreEndPagesCentered = endPagesCentered;
    }

    public void setListener(OnImageEventListener listener) {
        mListener = listener;
    }

    /**
     * @param heightMin the height of the pager in the collapsed state
     */
    public void setPagerHeightMin(int heightMin) {
        mHeightMin = heightMin;
    }

    /**
     * @param heightMax the height of the pager in the expanded state
     */
    public void setPagingHeightMax(int heightMax) {
        mHeightMax = heightMax;
    }

    private void setViewWidthFractionCurrent(float fraction) {
        setViewWidthFractionCurrent(fraction, mIsCollapsed);
    }

    private void setViewWidthFractionCurrent(float fraction, boolean isCollapsed) {
        mViewWidthFractionCurrent = fraction;
        updateCurrentPagingViewNumber(isCollapsed);
        updateAdapterState(isCollapsed);
    }

    /**
     * Snaps the view to an appropriate position given the current motion. If the current motion is
     * a fling, the velocity in the x direction must be supplied.
     *
     * @param velocityX the velocity in the x direction. Only required for a fling motion.
     * @param isFling   must be set to true if being called as part of a fling
     */
    private void snapToPosition(Float velocityX, boolean isFling) {
        // Cancel any current scrolling
        stopScroll();

        if (isFling && velocityX == null) {
            Log.e(TAG, "A fling velocity was not specified when snapping in a fling gesture." +
                    " Aborting animation.");
            return;
        }

        // Ensure the current paging view number is current
        updateCurrentPagingViewNumber();

        // Get actual position of the first visible view, and retrieve the view itself
        int firstVisiblePosition = mLayoutManager.findFirstVisibleItemPosition();
        View firstVisibleView = mLayoutManager.findViewByPosition(firstVisiblePosition);
        if (firstVisibleView == null) {
            // This should never be the case, but this serves just as a precaution
            Log.e(TAG, "First visible is null");
            return;
        }

        int currentViewOffset = firstVisibleView.getLeft();
        final int targetPosition = getNewTargetPosition(firstVisiblePosition, currentViewOffset,
                velocityX, isFling);

        // The desired scroll amount is the amount needed to bring the first visible position to
        // be left aligned, plus the desired change in position. Note that the first/last positions
        // may not need this full scrolling amount due to possible padding, so this needs to be
        // corrected for as well.
        int extraPadding = 0;
        if (targetPosition == 0) {
            extraPadding = getExtraLeftPaddingForPosition(0);
        } else if (targetPosition == getLastPosition()) {
            extraPadding = getExtraLeftPaddingForPosition(getLastPosition());
        }
        int positionDiff = targetPosition - firstVisiblePosition;
        int scrollChangeFromPositionChange = positionDiff * getViewWidth();
        final int desiredScrollAmount = currentViewOffset + scrollChangeFromPositionChange - extraPadding;

        if (desiredScrollAmount == 0) {
            // Don't need to perform any scrolling
            return;
        }

        // Use a custom smooth scroller to perform the scroll operation
        SmoothScroller scroller = new SmoothScroller() {
            @Override
            protected void onStart() {
                // Don't need to do anything here
            }

            @Override
            protected void onStop() {
                // This call to onStop actually happens while the view is still scrolling, so we'll
                // wait a little bit before notifying the listener. We could wait until the scroll
                // state is SCROLL_STATE_IDLE, but that tends to be a bit too late. This is a
                // compromise.
                postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        notifyListenerForPosition(targetPosition);
                    }
                }, PAGING_LISTENER_DELAY);
            }

            @Override
            protected void onSeekTargetStep(int dx, int dy, State state, Action action) {
                // Don't need to do anything here
            }

            @Override
            protected void onTargetFound(View targetView, State state, Action action) {
                // Update the action with our desired values
                action.setDx(desiredScrollAmount);
                action.setDuration(PAGING_ANIMATION_TIME);
            }
        };
        scroller.setTargetPosition(targetPosition);
        mLayoutManager.startSmoothScroll(scroller);
    }

    private void toggleExpandedState(final int position) {
        if (mIsAnimatingExpandedState) {
            // Do nothing
            return;
        }

        // List for keeping track of available animators
        List<Animator> animators = new ArrayList<>();

        // Get a reference to the selected view
        final View targetView = mLayoutManager.findViewByPosition(position);

        // Animate the height and widths of the view and its children and the start/end padding (if
        // necessary)
        final int initialHeight, finalHeight, initialPadding, finalPadding;
        if (mIsCollapsed) {
            initialHeight = mHeightMin;
            finalHeight = mHeightMax;
            initialPadding = getPaddingToCenterFirstItem();
            finalPadding = 0;
        } else {
            initialHeight = mHeightMax;
            finalHeight = mHeightMin;
            initialPadding = 0;
            finalPadding = getPaddingToCenterFirstItem();
        }
        animators.add(getLayoutChangeAnimator(initialHeight, finalHeight, initialPadding,
                finalPadding));

        // Animate scrolling of the items to smoothly transition into/out of full-width mode
        int leftStartValue, leftEndValue;
        if (mIsCollapsed) {
            leftStartValue = targetView.getLeft();

            // Always end full screen
            leftEndValue = 0;
        } else {
            // Always start out full screen
            leftStartValue = 0;

            // Animate back to the appropriate position within a page block
            leftEndValue = getLeftEndValueForPosition(position);
        }
        animators.add(getLeftEdgeAnimator(targetView, leftStartValue, leftEndValue));

        AnimatorSet set = new AnimatorSet();
        set.playTogether(animators);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                mIsAnimatingExpandedState = true;

                // Disable eager loading for now. This probably shouldn't matter much, as we really
                // shouldn't be binding views during this animation but this is nice to have just
                // in case.
                mLayoutManager.setNumberOfScreensToEagerLoad(0);
            }


            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);

                // We will perform any final work (such as refreshing the current view state) in
                // onLayout; if that has not already happened, explicitly request a layout pass to
                // ensure it is triggered.
                if (mIsAnimatingExpandedState) {
                    requestLayout();
                }
            }
        });
        set.setDuration(HEIGHT_ANIMATION_TIME);
        set.start();
    }

    private void updateAdapterState() {
        updateAdapterState(mIsCollapsed);
    }

    private void updateAdapterState(boolean isCollapsed) {
        mAdapter.setViewWidth(getViewWidth());
        mAdapter.setCollapsed(isCollapsed);
    }

    private void updateContentDescription() {
        mAdapter.setBaseContentDescription(mBaseContentDescription);
    }

    private void updateCurrentPagingViewNumber() {
        updateCurrentPagingViewNumber(mIsCollapsed);
    }

    private void updateCurrentPagingViewNumber(boolean isCollapsed) {
        if (isCollapsed) {
            mItemsPerPageCurrent = mItemsPerPage;
        } else {
            mItemsPerPageCurrent = 1;
        }
    }

    private void updatePadding() {
        if (mIsCollapsed && mAreEndPagesCentered) {
            setPadding(getPaddingToCenterFirstItem(), 0, getPaddingToCenterFirstItem(), 0);
        } else {
            // No padding
            setPadding(0, 0, 0, 0);
        }
    }

    private void updateViewHeight() {
        ViewGroup.LayoutParams params = getLayoutParams();
        if (mIsCollapsed) {
            params.height = mHeightMin;
        } else {
            params.height = mHeightMax;
        }
        setLayoutParams(params);
    }

    private void updateViewWidthFractionCurrent() {
        if (mIsCollapsed) {
            mViewWidthFractionCurrent = mViewWidthFractionMin;
        } else {
            mViewWidthFractionCurrent = mViewWidthFractionMax;
        }
    }

    //----- Methods for state saving -----//

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());
        restoreFromSavedState(savedState);
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState savedState = new SavedState(superState);
        saveToSavedState(savedState);
        return savedState;
    }

    private void restoreFromSavedState(SavedState state) {
        mAreEndPagesCentered = state.areEndPagesCentered;
        mExpandDisabled = state.expandDisabled;
        mIsCollapsed = state.isCollapsed;
        mViewWidthFractionCurrent = state.viewWidthFractionCurrent;
        mViewWidthFractionMin = state.viewWidthFractionMin;
        mFirstSelectedItemSaved = state.firstSelectedItem;
        mHeightMax = state.heightMax;
        mHeightMin = state.heightMin;
        mItemsPerPage = state.itemsPerPage;
        mItemsPerPageCurrent = state.itemsPerPageCurrent;
        mBitmapConfig = state.bitmapConfig;
        mBaseContentDescription = state.baseContentDescription;
        refresh();
    }

    private void saveToSavedState(SavedState state) {
        state.areEndPagesCentered = mAreEndPagesCentered;
        state.expandDisabled = mExpandDisabled;
        state.isCollapsed = mIsCollapsed;
        state.viewWidthFractionCurrent = mViewWidthFractionCurrent;
        state.viewWidthFractionMin = mViewWidthFractionMin;
        state.firstSelectedItem = getFirstSelectedItem();
        state.heightMax = mHeightMax;
        state.heightMin = mHeightMin;
        state.itemsPerPage = mItemsPerPage;
        state.itemsPerPageCurrent = mItemsPerPageCurrent;
        state.bitmapConfig = mBitmapConfig;
        state.baseContentDescription = mBaseContentDescription;
    }

    /**
     * Represents the saved state of this view. This will NOT extend BaseSavedState, as there is an
     * issue in which subclasses of RecyclerView can not always retrieve its class loader properly,
     * so we will do it manually here.
     */
    private static class SavedState implements Parcelable {

        Parcelable superState;
        boolean areEndPagesCentered;
        boolean expandDisabled;
        boolean isCollapsed;
        float viewWidthFractionCurrent;
        float viewWidthFractionMin;
        int firstSelectedItem;
        int heightMax;
        int heightMin;
        int itemsPerPage;
        int itemsPerPageCurrent;
        Bitmap.Config bitmapConfig;
        String baseContentDescription;

        SavedState(Parcelable superState) {
            this.superState = superState;
        }

        private SavedState(Parcel in) {
            // We need to force the use of the RecyclerView's class loader
            this.superState = in.readParcelable(RecyclerView.class.getClassLoader());

            boolean[] booleans = new boolean[3];
            in.readBooleanArray(booleans);
            areEndPagesCentered = booleans[0];
            expandDisabled = booleans[1];
            isCollapsed = booleans[2];

            viewWidthFractionCurrent = in.readFloat();
            viewWidthFractionMin = in.readFloat();

            firstSelectedItem = in.readInt();
            heightMax = in.readInt();
            heightMin = in.readInt();
            itemsPerPage = in.readInt();
            itemsPerPageCurrent = in.readInt();

            int tmpBitmapConfig = in.readInt();
            bitmapConfig = tmpBitmapConfig == -1 ? Bitmap.Config.ARGB_8888 :
                    Bitmap.Config.values()[tmpBitmapConfig];

            baseContentDescription = in.readString();
        }

        Parcelable getSuperState() {
            return superState;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel out, int flags) {
            out.writeParcelable(superState, flags);
            out.writeBooleanArray(new boolean[]{areEndPagesCentered, expandDisabled, isCollapsed});
            out.writeFloat(viewWidthFractionCurrent);
            out.writeFloat(viewWidthFractionMin);
            out.writeInt(firstSelectedItem);
            out.writeInt(heightMax);
            out.writeInt(heightMin);
            out.writeInt(itemsPerPage);
            out.writeInt(itemsPerPageCurrent);
            out.writeInt(bitmapConfig == null ? -1 : bitmapConfig.ordinal());
            out.writeString(baseContentDescription);
        }

        public static final Parcelable.Creator<SavedState> CREATOR
                = new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}
