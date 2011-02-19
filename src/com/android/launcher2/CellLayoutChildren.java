/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher2;

import java.util.ArrayList;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

public class CellLayoutChildren extends ViewGroup {
    static final String TAG = "CellLayoutChildren";

    // These are temporary variables to prevent having to allocate a new object just to
    // return an (x, y) value from helper functions. Do NOT use them to maintain other state.
    private final int[] mTmpCellXY = new int[2];

    private final WallpaperManager mWallpaperManager;

    private int mLeftPadding;
    private int mTopPadding;

    private int mCellWidth;
    private int mCellHeight;

    private int mWidthGap;
    private int mHeightGap;

    public CellLayoutChildren(Context context) {
        super(context);
        mWallpaperManager = WallpaperManager.getInstance(context);
        setLayerType(LAYER_TYPE_HARDWARE, null);

        // Disable multitouch for the workspace
        setMotionEventSplittingEnabled(false);
    }

    public void setCellDimensions(int cellWidth, int cellHeight,
            int leftPadding, int topPadding, int widthGap, int heightGap ) {
        mCellWidth = cellWidth;
        mCellHeight = cellHeight;
        mLeftPadding = leftPadding;
        mTopPadding = topPadding;
        mWidthGap = widthGap;
        mHeightGap = heightGap;
    }

    public View getChildAt(int x, int y) {
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            CellLayout.LayoutParams lp = (CellLayout.LayoutParams) child.getLayoutParams();

            if ((lp.cellX <= x) && (x < lp.cellX + lp.cellHSpan) &&
                    (lp.cellY <= y) && (y < lp.cellY + lp.cellHSpan)) {
                return child;
            }
        }
        return null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int cellWidth = mCellWidth;
        final int cellHeight = mCellHeight;
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            CellLayout.LayoutParams lp = (CellLayout.LayoutParams) child.getLayoutParams();

            lp.setup(cellWidth, cellHeight, mWidthGap, mHeightGap,
                    mLeftPadding, mTopPadding);

            int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
            int childheightMeasureSpec = MeasureSpec.makeMeasureSpec(lp.height,
                    MeasureSpec.EXACTLY);

            child.measure(childWidthMeasureSpec, childheightMeasureSpec);
        }
        int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSpecSize =  MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(widthSpecSize, heightSpecSize);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                CellLayout.LayoutParams lp = (CellLayout.LayoutParams) child.getLayoutParams();

                int childLeft = lp.x;
                int childTop = lp.y;
                child.layout(childLeft, childTop, childLeft + lp.width, childTop + lp.height);

                if (lp.dropped) {
                    lp.dropped = false;

                    final int[] cellXY = mTmpCellXY;
                    getLocationOnScreen(cellXY);
                    mWallpaperManager.sendWallpaperCommand(getWindowToken(),
                            WallpaperManager.COMMAND_DROP,
                            cellXY[0] + childLeft + lp.width / 2,
                            cellXY[1] + childTop + lp.height / 2, 0, null);

                    if (lp.animateDrop) {
                        lp.animateDrop = false;

                        // This call does not result in a requestLayout(), but at one point did.
                        // We need to be cautious about any method calls within the layout pass
                        // to insure we don't leave the view tree in a bad state.
                        ((Workspace) mParent.getParent()).animateViewIntoPosition(child);
                    }
                }
            }
        }
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        if (child != null) {
            Rect r = new Rect();
            child.getDrawingRect(r);
            requestRectangleOnScreen(r);
        }
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();

        // Cancel long press for all children
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            child.cancelLongPress();
        }
    }

    @Override
    protected void setChildrenDrawingCacheEnabled(boolean enabled) {
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View view = getChildAt(i);
            view.setDrawingCacheEnabled(enabled);
            // Update the drawing caches
            if (!view.isHardwareAccelerated()) {
                view.buildDrawingCache(true);
            }
        }
    }

    @Override
    protected void setChildrenDrawnWithCacheEnabled(boolean enabled) {
        super.setChildrenDrawnWithCacheEnabled(enabled);
    }

    private final ArrayList<AppWidgetResizeFrame> mResizeFrames = new  ArrayList<AppWidgetResizeFrame>();
    private AppWidgetResizeFrame mCurrentResizeFrame;
    private int mXDown, mYDown;
    private boolean mIsWidgetBeingResized;

    public void clearAllResizeFrames() {
        for (AppWidgetResizeFrame frame: mResizeFrames) {
            removeView(frame);
        }
        mResizeFrames.clear();
    }

    public boolean isWidgetBeingResized() {
        return mIsWidgetBeingResized;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        Rect hitRect = new Rect();

        int x = (int) ev.getX();
        int y = (int) ev.getY();

        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            for (AppWidgetResizeFrame child: mResizeFrames) {
                child.getHitRect(hitRect);
                if (hitRect.contains(x, y)) {
                    if (child.beginResizeIfPointInRegion(x - child.getLeft(), y - child.getTop())) {
                        mCurrentResizeFrame = child;
                        mIsWidgetBeingResized = true;
                        mXDown = x;
                        mYDown = y;
                        requestDisallowInterceptTouchEvent(true);
                        return true;
                    }
                }
            }
            mCurrentResizeFrame = null;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean handled = false;
        Rect hitRect = new Rect();
        int action = ev.getAction();

        int x = (int) ev.getX();
        int y = (int) ev.getY();

        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            for (AppWidgetResizeFrame child: mResizeFrames) {
                child.getHitRect(hitRect);
                if (hitRect.contains(x, y)) {
                    if (child.beginResizeIfPointInRegion(x - child.getLeft(), y - child.getTop())) {
                        mCurrentResizeFrame = child;
                        mIsWidgetBeingResized = true;
                        mXDown = x;
                        mYDown = y;
                        requestDisallowInterceptTouchEvent(true);
                        return true;
                    }
                }
            }
            mCurrentResizeFrame = null;
        }

        // TODO: Need to handle ACTION_POINTER_UP / multi-touch
        if (mCurrentResizeFrame != null) {
            switch (action) {
                case MotionEvent.ACTION_MOVE:
                    mCurrentResizeFrame.visualizeResizeForDelta(x - mXDown, y - mYDown);
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP: {
                    mCurrentResizeFrame.commitResizeForDelta(x - mXDown, y - mYDown);
                    mIsWidgetBeingResized = false;
                    handled = true;
                }
            }
        }
        return handled;
    }

    public void addResizeFrame(ItemInfo itemInfo, LauncherAppWidgetHostView widget, CellLayout cellLayout) {
        AppWidgetResizeFrame resizeFrame = new AppWidgetResizeFrame(getContext(),
                itemInfo, widget, cellLayout);

        CellLayout.LayoutParams lp = new CellLayout.LayoutParams(-1, -1, -1, -1);
        lp.isLockedToGrid = false;

        addView(resizeFrame, lp);
        mResizeFrames.add(resizeFrame);

        resizeFrame.snapToWidget(false);
    }
}
