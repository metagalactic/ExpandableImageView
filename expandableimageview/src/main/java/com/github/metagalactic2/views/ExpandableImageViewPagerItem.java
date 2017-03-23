package com.github.metagalactic2.views;

import android.support.annotation.Nullable;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class ExpandableImageViewPagerItem {

    public abstract String imageUrl();

    @Nullable
    public abstract String imageContentDescription();

    public static ExpandableImageViewPagerItem create(String imageUrl) {
        return create(imageUrl, null);
    }

    public static ExpandableImageViewPagerItem create(String imageUrl, @Nullable String description) {
        return new AutoValue_ExpandableImageViewPagerItem(imageUrl, description);
    }
}
