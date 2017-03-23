package com.github.metagalactic2.layout_manager;

import android.content.Context;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;

/**
 * A LinearLayoutManager that overrides getExtraLayoutSpace to ensure that a fair amount of extra
 * layout space is provided for pre-loading data. The intended usage of this manager is for
 * ViewPager-like RecyclerView implementations that want to mimic a ViewPager's off-screen page
 * loading capabilities.
 */
public class CustomLinearLayoutManager extends LinearLayoutManager {

    /**
     * Provides the default value for the amount of "screens" (either full vertical or horizontal
     * screen sizes, depending on orientation) to eager load
     */
    private static final int DEFAULT_SCREENS_TO_EAGER_LOAD = 2;

    private static final String INVALID_SCREENS = "The number of screens to eagerly load must be" +
            " greater than or equal to 0.";

    private int mScreensToEagerLoad = DEFAULT_SCREENS_TO_EAGER_LOAD;
    private int mScreenHeight;
    private int mScreenWidth;

    public CustomLinearLayoutManager(Context context) {
        this(context, LinearLayoutManager.HORIZONTAL, false, DEFAULT_SCREENS_TO_EAGER_LOAD);
    }

    public CustomLinearLayoutManager(Context context, int orientation, boolean reverseLayout) {
        this(context, orientation, reverseLayout, DEFAULT_SCREENS_TO_EAGER_LOAD);
    }

    public CustomLinearLayoutManager(Context context, int orientation, boolean reverseLayout,
                                     int numberOfScreensToEagerLoad) {
        super(context, orientation, reverseLayout);
        init(context, numberOfScreensToEagerLoad);
    }

    @Override
    protected int getExtraLayoutSpace(RecyclerView.State state) {
        if (getOrientation() == LinearLayoutManager.VERTICAL) {
            return mScreensToEagerLoad * mScreenHeight;
        } else {
            return mScreensToEagerLoad * mScreenWidth;
        }
    }

    private void init(Context context, int numberOfScreensToEagerLoad) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        mScreenHeight = metrics.heightPixels;
        mScreenWidth = metrics.widthPixels;
        setNumberOfScreensToEagerLoad(numberOfScreensToEagerLoad);
    }

    public void setNumberOfScreensToEagerLoad(int screens) {
        if (screens < 0) {
            throw new IllegalArgumentException(INVALID_SCREENS);
        }
        mScreensToEagerLoad = screens;
    }
}
