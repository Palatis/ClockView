package tw.idv.palatis.clockview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.icu.util.Measure;
import android.os.Build;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.NestedScrollingChild;
import android.support.v4.view.NestedScrollingChildHelper;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import java.util.Calendar;

public class ClockView extends View implements NestedScrollingChild {
    private static final String TAG = "ClockView";

    private static final ImageView.ScaleType[] sScaleTypeArray = {
            ImageView.ScaleType.MATRIX,
            ImageView.ScaleType.FIT_XY,
            ImageView.ScaleType.FIT_START,
            ImageView.ScaleType.FIT_CENTER,
            ImageView.ScaleType.FIT_END,
            ImageView.ScaleType.CENTER,
            ImageView.ScaleType.CENTER_CROP,
            ImageView.ScaleType.CENTER_INSIDE
    };

    private final NestedScrollingChildHelper mNestedChildHelper = new NestedScrollingChildHelper(this);

    private Calendar mCalendar;

    private ImageView.ScaleType mScaleType = ImageView.ScaleType.MATRIX;
    private Matrix mCustomMatrix = new Matrix();
    private final Matrix mMatrix = new Matrix();

    private boolean mIs24hr;
    private boolean mDrawReversed;
    @DrawableRes
    private int mDialDrawableResId;
    private Drawable mDialDrawable;
    private HandOverlay[] mHandOverlays;
    private float[] mTouchPoints;
    private boolean mAdjustViewBounds;

    public static final int HAND_HOUR = 0;
    public static final int HAND_MINUTE = 1;
    public static final int HAND_SECOND = 2;

    private class HandOverlay {
        public float value;
        public long interval;
        public float division;
        public float startAngle;
        public float ratio_cx;
        public float ratio_cy;
        @DrawableRes
        public int drawableResId;
        public Drawable drawable;
        public ValueAnimator animator = null;

        public HandOverlay(@DrawableRes int drawableResId, @Nullable Drawable drawable, float value, float division, float startAngle, float ratio_cx, float ratio_cy, long interval) {
            this.drawableResId = drawableResId;
            this.drawable = drawable;
            this.value = value;
            this.division = division;
            this.startAngle = startAngle;
            this.ratio_cx = ratio_cx;
            this.ratio_cy = ratio_cy;
            this.interval = interval;
        }
    }

    public ClockView(Context context) {
        this(context, null);
    }

    public ClockView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ClockView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, 0);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ClockView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr, defStyleRes);
    }

    private void init(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        final Calendar now = Calendar.getInstance();

        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ClockView, defStyleAttr, defStyleRes);
        try {
            final Resources.Theme theme = context.getTheme();
            final Resources res = getResources();

            mNestedChildHelper.setNestedScrollingEnabled(a.getBoolean(R.styleable.ClockView_android_nestedScrollingEnabled, true));

            mIs24hr = a.getBoolean(R.styleable.ClockView_is24hr, false);
            mDrawReversed = a.getBoolean(R.styleable.ClockView_drawReversed, true);
            mDialDrawableResId = a.getResourceId(R.styleable.ClockView_dial, -1);
            mDialDrawable = a.getDrawable(R.styleable.ClockView_dial);
            if (mDialDrawable == null) {
                mDialDrawableResId = R.drawable.cv_default_dial;
                mDialDrawable = ResourcesCompat.getDrawable(res, R.drawable.cv_default_dial, theme);
            }
            if (mDialDrawable != null)
                mDialDrawable.setCallback(this);

            final int numHands = a.getInt(R.styleable.ClockView_numHands, 3);
            mHandOverlays = new HandOverlay[numHands];

            mHandOverlays[HAND_HOUR] = new HandOverlay(
                    a.getResourceId(R.styleable.ClockView_hand_hour, -1),
                    a.getDrawable(R.styleable.ClockView_hand_hour),
                    a.getInt(R.styleable.ClockView_hour, now.get(mIs24hr ? Calendar.HOUR_OF_DAY : Calendar.HOUR)),
                    a.getFloat(R.styleable.ClockView_hand_hour_div, 360.0f / (mIs24hr ? 24.0f : 12.0f)), // 1/12 or 1/24 per hour
                    a.getFloat(R.styleable.ClockView_hand_hour_startAngle, 90.0f),
                    a.getFloat(R.styleable.ClockView_hand_hour_cx, 0.5f),
                    a.getFloat(R.styleable.ClockView_hand_hour_cy, 0.5f),
                    a.getInt(R.styleable.ClockView_hand_hour_interval, 3600 * 1000) // updates every 3600 sec
            );
            if (mHandOverlays[HAND_HOUR].drawable == null) {
                mHandOverlays[HAND_HOUR].drawableResId = R.drawable.cv_default_hand_hour;
                mHandOverlays[HAND_HOUR].drawable = ResourcesCompat.getDrawable(res, R.drawable.cv_default_hand_hour, theme);
            }
            if (mHandOverlays[HAND_HOUR].drawable != null)
                mHandOverlays[HAND_HOUR].drawable.setCallback(this);

            mHandOverlays[HAND_MINUTE] = new HandOverlay(
                    a.getResourceId(R.styleable.ClockView_hand_minute, -1),
                    a.getDrawable(R.styleable.ClockView_hand_minute),
                    a.getInt(R.styleable.ClockView_minute, now.get(Calendar.MINUTE)),
                    a.getFloat(R.styleable.ClockView_hand_minute_div, 360.0f / 60.0f), // 1/60 per minute
                    a.getFloat(R.styleable.ClockView_hand_hour_startAngle, 90.0f),
                    a.getFloat(R.styleable.ClockView_hand_minute_cx, 0.5f),
                    a.getFloat(R.styleable.ClockView_hand_minute_cy, 0.5f),
                    a.getInt(R.styleable.ClockView_hand_minute_interval, 60 * 1000) // updates every 60 sec
            );
            if (mHandOverlays[HAND_MINUTE].drawable == null) {
                mHandOverlays[HAND_MINUTE].drawableResId = R.drawable.cv_default_hand_minute;
                mHandOverlays[HAND_MINUTE].drawable = ResourcesCompat.getDrawable(res, R.drawable.cv_default_hand_minute, theme);
            }
            if (mHandOverlays[HAND_MINUTE].drawable != null)
                mHandOverlays[HAND_MINUTE].drawable.setCallback(this);

            mHandOverlays[HAND_SECOND] = new HandOverlay(
                    a.getResourceId(R.styleable.ClockView_hand_second, -1),
                    a.getDrawable(R.styleable.ClockView_hand_second),
                    a.getInt(R.styleable.ClockView_second, now.get(Calendar.SECOND)),
                    a.getFloat(R.styleable.ClockView_hand_second_div, 360.0f / 60.0f), // 1/60 per second
                    a.getFloat(R.styleable.ClockView_hand_hour_startAngle, 90.0f),
                    a.getFloat(R.styleable.ClockView_hand_second_cx, 0.5f),
                    a.getFloat(R.styleable.ClockView_hand_second_cy, 0.5f),
                    a.getInt(R.styleable.ClockView_hand_second_interval, 1000) // updates every 1 sec
            );
            if (mHandOverlays[HAND_SECOND].drawable != null)
                mHandOverlays[HAND_SECOND].drawable.setCallback(this);

            mTouchPoints = new float[8];

            setScaleTypeInternal(sScaleTypeArray[a.getInteger(R.styleable.ClockView_android_scaleType, 0 /* matrix */)]);
            setAdjustViewBounds(a.getBoolean(R.styleable.ClockView_android_adjustViewBounds, false));
        } finally {
            a.recycle();
        }
    }

    public void setAdjustViewBounds(boolean adjust) {
        if (mAdjustViewBounds == adjust)
            return;

        mAdjustViewBounds = adjust;
        if (mAdjustViewBounds) {
            setScaleTypeInternal(ImageView.ScaleType.FIT_CENTER);
            requestLayout();
        }
    }

    public boolean getAdjustViewBounds() {
        return mAdjustViewBounds;
    }

    public void setScaleType(ImageView.ScaleType scaleType) {
        if (!mScaleType.equals(scaleType)) {
            setScaleTypeInternal(scaleType);
            postInvalidate();
        }
    }

    private void setScaleTypeInternal(ImageView.ScaleType scaleType) {
        mScaleType = scaleType;
        final Matrix matrix = new Matrix();
        if (mScaleType.equals(ImageView.ScaleType.MATRIX)) {
            matrix.set(mCustomMatrix);
        } else {
            final float width = getContentWidth();
            final float height = getContentHeight();
            final float dialWidth = getDialWidth();
            final float dialHeight = getDialHeight();
            if (mScaleType.equals(ImageView.ScaleType.FIT_XY)) {
                matrix.postScale(width / dialWidth, height / dialHeight);
            } else if (mScaleType.equals(ImageView.ScaleType.FIT_START)) {
                final float scale = Math.min(dialWidth != 0 ? width / dialWidth : 1, dialHeight != 0 ? height / dialHeight : 1);
                matrix.postScale(scale, scale);
            } else if (mScaleType.equals(ImageView.ScaleType.FIT_CENTER)) {
                final float scale = Math.min(dialWidth != 0 ? width / dialWidth : 1, dialHeight != 0 ? height / dialHeight : 1);
                matrix.postScale(scale, scale);
                matrix.postTranslate((width - dialWidth * scale) / 2, (height - dialHeight * scale) / 2);
            } else if (mScaleType.equals(ImageView.ScaleType.FIT_END)) {
                final float scale = Math.min(dialWidth != 0 ? width / dialWidth : 1, dialHeight != 0 ? height / dialHeight : 1);
                matrix.postScale(scale, scale);
                matrix.postTranslate(width - dialWidth * scale, height - dialHeight * scale);
            } else if (mScaleType.equals(ImageView.ScaleType.CENTER)) {
                matrix.postTranslate(width / 2, height / 2);
                matrix.postTranslate(-dialWidth / 2, -dialHeight / 2);
            } else if (mScaleType.equals(ImageView.ScaleType.CENTER_CROP)) {
                final float scale = Math.max(
                        dialWidth != 0 ? width / dialWidth : 1,
                        dialHeight != 0 ? height / dialHeight : 1
                );
                matrix.postScale(scale, scale);
                matrix.postTranslate((width - dialWidth * scale) / 2, (height - dialHeight * scale) / 2);
            } else if (mScaleType.equals(ImageView.ScaleType.CENTER_INSIDE)) {
                final float scale = Math.min(
                        width < dialWidth ? width / dialWidth : 1,
                        height < dialHeight ? height / dialHeight : 1
                );
                matrix.postScale(scale, scale);
                matrix.postTranslate(width / 2, height / 2);
                matrix.postTranslate(-dialWidth / 2, -dialHeight / 2);
            }
        }
        matrix.postTranslate(getPaddingLeft(), getPaddingTop());
        setMatrixInternal(matrix);
    }

    private void setMatrixInternal(Matrix matrix) {
        if (!mMatrix.equals(matrix))
            mMatrix.set(matrix);
    }

    public ImageView.ScaleType getScaleType() {
        return mScaleType;
    }

    public void setImageMatrix(Matrix matrix) {
        if (matrix == null)
            matrix = new Matrix();

        if (!mCustomMatrix.equals(matrix)) {
            mCustomMatrix = matrix;
            if (mScaleType.equals(ImageView.ScaleType.MATRIX)) {
                setScaleTypeInternal(mScaleType);
                postInvalidate();
            }
        }
    }

    public Matrix getImageMatrix() {
        return mCustomMatrix;
    }

    public void setNumHands(int n) {
        if (mTouchPoints == null || mTouchPoints.length != (2 + n << 1))
            mTouchPoints = new float[2 + n << 1];
        final HandOverlay[] newHands = new HandOverlay[n];
        final int count = Math.min(n, mHandOverlays.length);
        System.arraycopy(mHandOverlays, 0, newHands, 0, count);
        for (int i = count; i < n; ++i)
            newHands[i] = new HandOverlay(-1, null, 0, 0.0f, 0.0f, 0.0f, 0.0f, 0);
        mHandOverlays = newHands;
    }

    public void setDialDrawable(@DrawableRes int drawable) {
        if (mDialDrawableResId != drawable) {
            mDialDrawableResId = drawable;
            setDialDrawableInternal(ResourcesCompat.getDrawable(getResources(), drawable, getContext().getTheme()));
        }
    }

    public void setDialDrawable(@Nullable Drawable drawable) {
        if (mDialDrawable != drawable) {
            mDialDrawableResId = -1;
            setDialDrawableInternal(drawable);
        }
    }

    protected void setDialDrawableInternal(@Nullable Drawable drawable) {
        if (mDialDrawable != null)
            mDialDrawable.setCallback(null);
        if (mDialDrawable != drawable)
            setScaleTypeInternal(mScaleType);
        mDialDrawable = drawable;
        if (mDialDrawable != null)
            mDialDrawable.setCallback(this);
        postInvalidate();
    }

    public void setHandDrawable(int index, @DrawableRes int drawable) {
        final HandOverlay hand = mHandOverlays[index];
        if (hand.drawableResId != drawable) {
            hand.drawableResId = drawable;
            setHandDrawableInternal(hand, ResourcesCompat.getDrawable(getResources(), drawable, getContext().getTheme()));
        }
    }

    public void setHandDrawable(int index, @Nullable Drawable drawable) {
        final HandOverlay hand = mHandOverlays[index];
        if (hand.drawable != drawable) {
            hand.drawableResId = -1;
            setHandDrawableInternal(hand, drawable);
        }
    }

    protected void setHandDrawableInternal(HandOverlay hand, @Nullable Drawable drawable) {
        if (hand.drawable != null)
            hand.drawable.setCallback(null);
        hand.drawable = drawable;
        if (hand.drawable != null)
            hand.drawable.setCallback(this);
        postInvalidate();
    }

    public void setTime(long time) {
        mCalendar.setTimeInMillis(time);
        postInvalidate();
    }

    public void setTime(Calendar calendar) {
        mCalendar = calendar;
        postInvalidate();
    }

    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw || h != oldh) {
            setScaleTypeInternal(mScaleType);
            postInvalidate();
        }
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mDialDrawable == null)
            return;

        canvas.save();
        canvas.concat(mMatrix);

        mDialDrawable.setBounds(0, 0, mDialDrawable.getIntrinsicWidth(), mDialDrawable.getIntrinsicHeight());
        mDialDrawable.draw(canvas);

        canvas.translate(mDialDrawable.getIntrinsicWidth() / 2.0f, mDialDrawable.getIntrinsicHeight() / 2.0f);
        if (mDrawReversed) {
            for (int i = mHandOverlays.length - 1; i >= 0; --i)
                drawHand(canvas, mHandOverlays[i]);
        } else {
            for (final HandOverlay hand : mHandOverlays)
                drawHand(canvas, hand);
        }

        canvas.restore();
    }

    private void drawHand(Canvas canvas, HandOverlay hand) {
        if (hand.drawable == null)
            return;

        canvas.save();
        canvas.rotate(hand.value * hand.division);
        canvas.translate(-hand.drawable.getIntrinsicWidth() / 2.0f, -hand.drawable.getIntrinsicHeight() / 2.0f);
        hand.drawable.setBounds(0, 0, hand.drawable.getIntrinsicWidth(), hand.drawable.getIntrinsicHeight());
        hand.drawable.draw(canvas);
        canvas.restore();
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        final int minWidth = getSuggestedMinimumWidth();
        final int minHeight = getSuggestedMinimumHeight();

        int width;
        int height;

        //Measure Width
        if (widthMode == MeasureSpec.EXACTLY) {
            //Must be this size
            width = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            //Can't be bigger than...
            width = Math.min(minWidth, widthSize);
        } else {
            //Be whatever you want
            width = minWidth;
        }

        //Measure Height
        if (heightMode == MeasureSpec.EXACTLY) {
            //Must be this size
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            //Can't be bigger than...
            height = Math.min(minHeight, heightSize);
        } else {
            //Be whatever you want
            height = minHeight;
        }

        if (mAdjustViewBounds) {
            if (widthMode == MeasureSpec.EXACTLY && heightMode == MeasureSpec.AT_MOST) {
                height = Math.min(heightSize, minHeight * width / minWidth);
            } else if (widthMode == MeasureSpec.AT_MOST && heightMode == MeasureSpec.EXACTLY) {
                width = Math.min(widthSize, minWidth * height / minHeight);
            }
        }


        setMeasuredDimension(width, height);
    }

    private int getContentHeight() {
        return getHeight() - getPaddingTop() - getPaddingBottom();
    }

    private int getContentWidth() {
        return getWidth() - getPaddingLeft() - getPaddingRight();
    }

    private int getDialWidth() {
        return mDialDrawable != null ? mDialDrawable.getIntrinsicWidth() : 0;
    }

    private int getDialHeight() {
        return mDialDrawable != null ? mDialDrawable.getIntrinsicHeight() : 0;
    }

    protected int getSuggestedMinimumWidth() {
        return Math.max(super.getSuggestedMinimumWidth(), getDialWidth() + getPaddingLeft() + getPaddingRight());
    }

    protected int getSuggestedMinimumHeight() {
        return Math.max(super.getSuggestedMinimumHeight(), getDialHeight() + getPaddingTop() + getPaddingBottom());
    }

    private int mHandIndex = -1;
    private float mLastTouchX = 0;
    private float mLastTouchY = 0;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean superResult = super.onTouchEvent(event);

        final float eventX = event.getX();
        final float eventY = event.getY();

        final int action = MotionEventCompat.getActionMasked(event);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                mLastTouchX = eventX;
                mLastTouchY = eventY;
                mHandIndex = getHandByLocation(eventX, eventY);
                if (mHandIndex != -1) {
                    boolean shouldHandle = false;
                    if (mOnHandChangedListener != null) {
                        mNestedChildHelper.startNestedScroll(ViewCompat.SCROLL_AXIS_HORIZONTAL | ViewCompat.SCROLL_AXIS_VERTICAL);
                        shouldHandle = mOnHandChangedListener.onHandChangeBegin(this, mHandIndex);
                    }
                    if (shouldHandle) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                        Log.d(TAG, "onTouchEvent(): starting with index = " + mHandIndex);
                        return true;
                    } else {
                        mHandIndex = -1;
                        return superResult;
                    }
                } else {
                    return superResult;
                }
            case MotionEvent.ACTION_MOVE:
                if (mHandIndex == -1)
                    return superResult;

                if (mNestedChildHelper.isNestedScrollingEnabled())
                    handleHandDrag(mHandIndex, mLastTouchX, mLastTouchY, eventX, eventY);
                mLastTouchX = eventX;
                mLastTouchY = eventY;
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mOnHandChangedListener != null && mNestedChildHelper.isNestedScrollingEnabled()) {
                    mOnHandChangedListener.onHandChangeEnd(this, mHandIndex);
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                mHandIndex = -1;
                return true;
        }
        return superResult;
    }

    private void handleHandDrag(int index, float oldX, float oldY, float newX, float newY) {
        final HandOverlay hand = mHandOverlays[index];

        final int dialWidth = getDialWidth();
        final int dialHeight = getDialHeight();
        if (dialWidth == 0 || dialHeight == 0)
            return;

        final int contentCenterX = (getRight() - getLeft()) / 2 + getPaddingLeft();
        final int contentCenterY = (getBottom() - getTop()) / 2 + getPaddingTop();

        final float handCenterX = contentCenterX + dialWidth * (hand.ratio_cx - 0.5f);
        final float handCenterY = contentCenterY + dialHeight * (hand.ratio_cy - 0.5f);

        final float angleOld = (float) Math.atan2(oldY - handCenterY, oldX - handCenterX);
        final float angleNew = (float) Math.atan2(newY - handCenterY, newX - handCenterX);

        final float oldValue = hand.value;
        hand.value += Math.toDegrees(angleNew - angleOld) / hand.division;
        if (mOnHandChangedListener != null)
            mOnHandChangedListener.onHandChanged(this, index, hand.value, oldValue);
        postInvalidateOnAnimation();
    }

    private int getHandByLocation(float x, float y) {
        // dial center
        mTouchPoints[0] = getDialWidth() / 2.0f;
        mTouchPoints[1] = getDialHeight() / 2.0f;
        if (mTouchPoints[0] == 0 || mTouchPoints[1] == 0)
            return -1;

        // collect tip position
        for (int i = 0; i < mHandOverlays.length; ++i) {
            final HandOverlay hand = mHandOverlays[i];
            if (hand.drawable == null)
                continue;
            final float handSize = hand.drawable.getIntrinsicHeight() / 2.0f;
            final float handAngle = (float) Math.toRadians(hand.value * hand.division - hand.startAngle);
            mTouchPoints[2 + (i << 1)] = (float) (mTouchPoints[0] + Math.cos(handAngle) * handSize);
            mTouchPoints[3 + (i << 1)] = (float) (mTouchPoints[1] + Math.sin(handAngle) * handSize);
        }

        // Log.d(TAG, "getHandByLocation(): touch = (" + x + ", " + y + "), size = (" + getWidth() + ", " + getHeight() + ")");
        // Log.d(TAG, "getHandByLocation(): points before = " + Arrays.toString(mTouchPoints));
        mMatrix.mapPoints(mTouchPoints);
        // Log.d(TAG, "getHandByLocation():  points after = " + Arrays.toString(mTouchPoints));

        // find hand by distance
        int index = -1;
        float minDistance = Float.POSITIVE_INFINITY;
        for (int i = 0; i < mHandOverlays.length; ++i) {
            final HandOverlay hand = mHandOverlays[i];
            if (hand.drawable == null)
                continue;
            float distance = (float) Math.hypot(x - mTouchPoints[2 + (i << 1)], y - mTouchPoints[3 + (i << 1)]);
            // Log.d(TAG, "getHandByLocation(): hand " + i + " - distance = " + distance);
            if (minDistance > distance) {
                minDistance = distance;
                index = i;
            }
        }
        return index;
    }

    public float getHandValue(int index) {
        return mHandOverlays[index].value;
    }

    public void setHour(float value) {
        setHandValue(HAND_HOUR, value, true);
    }

    public void setMinute(float value) {
        setHandValue(HAND_MINUTE, value, true);
    }

    public void setSecond(float value) {
        setHandValue(HAND_SECOND, value, true);
    }

    public void setHandValue(int index, float value, boolean animate) {
        final HandOverlay hand = mHandOverlays[index];
        if (!animate) {
            if (hand.value != value) {
                hand.value = value;
                postInvalidateOnAnimation();
            }
        } else {
            if (hand.value != value) {
                final float value2 = 360 / hand.division - value;
                final ValueAnimator animator = hand.animator = ValueAnimator.ofFloat(hand.value, Math.abs(value - hand.value) < Math.abs(value2 - hand.value) ? value : value2);
                animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        hand.value = (float) animation.getAnimatedValue();
                        postInvalidateOnAnimation();
                    }
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    float oldValue = hand.value;

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        hand.value = oldValue;
                        hand.animator = null;
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        hand.animator = null;
                    }
                });
                animator.start();
            }
        }
    }

    @Override
    public void setNestedScrollingEnabled(boolean enabled) {
        mNestedChildHelper.setNestedScrollingEnabled(enabled);
    }

    @Override
    public boolean isNestedScrollingEnabled() {
        return mNestedChildHelper.isNestedScrollingEnabled();
    }

    @Override
    public boolean startNestedScroll(int axes) {
        return mNestedChildHelper.startNestedScroll(axes);
    }

    @Override
    public void stopNestedScroll() {
        mNestedChildHelper.stopNestedScroll();
    }

    @Override
    public boolean hasNestedScrollingParent() {
        return mNestedChildHelper.hasNestedScrollingParent();
    }

    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int[] offsetInWindow) {
        return mNestedChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow) {
        return mNestedChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow);
    }

    @Override
    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        return mNestedChildHelper.dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        return mNestedChildHelper.dispatchNestedPreFling(velocityX, velocityY);
    }

    public interface OnHandChangedListener {
        boolean onHandChangeBegin(ClockView view, int handIndex);

        void onHandChanged(ClockView view, int handIndex, float value, float oldValue);

        void onHandChangeEnd(ClockView view, int handIndex);
    }

    private OnHandChangedListener mOnHandChangedListener = null;

    public void setOnHandChangedListener(OnHandChangedListener listener) {
        mOnHandChangedListener = listener;
    }
}
