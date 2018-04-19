package com.rainbow_umbrella.wopogo_medals;

import com.google.android.gms.vision.text.TextBlock;
import android.graphics.Rect;
import java.lang.Comparable;

public class TextBlockComparator implements Comparable<TextBlockComparator> {

    Rect mBoundingBox;
    int mIndex;
    TextBlockComparator(TextBlock textBlock, int index) {
        mBoundingBox = new Rect(textBlock.getBoundingBox());
        mIndex = index;
    }

    @Override
    public int compareTo(TextBlockComparator other) {
        if (mBoundingBox.top == other.mBoundingBox.top) {
            if (mBoundingBox.left == other.mBoundingBox.left) {
                return 0;
            } else {
                return mBoundingBox.left < mBoundingBox.left ? -1 : 1;
            }
        } else {
            return mBoundingBox.top < other.mBoundingBox.top ? -1 : 1;
        }
    }
}
