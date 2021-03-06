package tw.idv.palatis.clockview;

import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
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
        public float horizontal_bias;
        public float vertical_bias;
        @DrawableRes
        public int drawableResId;
        public Drawable drawable;
        public ValueAnimator animator = null;

        public HandOverlay(@DrawableRes int drawableResId, @Nullable Drawable drawable, float value, float division, float startAngle, float horizontal_bias, float vertical_bias, long interval) {
            this.drawableResId = drawableResId;
            this.drawable = drawable;
            this.value = value;
            this.division = division;
            this.startAngle = startAngle;
            this.horizontal_bias = horizontal_bias;
            this.vertical_bias = vertical_bias;
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

            mTouchPoints = new float[6];

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
            if (mAdjustViewBounds)
                requestLayout();
            else
                postInvalidate();
        }
    }

    private void setScaleTypeInternal(ImageView.ScaleType scaleType) {
        mScaleType = scaleType;
        final Matrix matrix = new Matrix();
        if (mScaleType.equals(ImageView.ScaleType.MATRIX)) {
            matrix.set(mCustomMatrix);
        } else {
            final RectF contentBound = new RectF(0, 0, getContentWidth(), getContentHeight());
            final RectF dialBound = new RectF(0, 0, getDialWidth(), getDialHeight());
            if (mScaleType.equals(ImageView.ScaleType.FIT_XY)) {
                matrix.setRectToRect(dialBound, contentBound, Matrix.ScaleToFit.FILL);
            } else if (mScaleType.equals(ImageView.ScaleType.FIT_START)) {
                matrix.setRectToRect(dialBound, contentBound, Matrix.ScaleToFit.START);
            } else if (mScaleType.equals(ImageView.ScaleType.FIT_CENTER)) {
                matrix.setRectToRect(dialBound, contentBound, Matrix.ScaleToFit.CENTER);
            } else if (mScaleType.equals(ImageView.ScaleType.FIT_END)) {
                matrix.setRectToRect(dialBound, contentBound, Matrix.ScaleToFit.END);
            } else if (mScaleType.equals(ImageView.ScaleType.CENTER)) {
                matrix.postTranslate(contentBound.width() / 2.0f, contentBound.height() / 2.0f);
                matrix.postTranslate(-dialBound.width() / 2.0f, -dialBound.height() / 2.0f);
            } else if (mScaleType.equals(ImageView.ScaleType.CENTER_CROP)) {
                final float scale = Math.max(
                        dialBound.width() != 0 ? contentBound.width() / dialBound.width() : 1,
                        dialBound.height() != 0 ? contentBound.height() / dialBound.height() : 1
                );
                matrix.postScale(scale, scale);
                matrix.postTranslate(
                        (contentBound.width() - dialBound.width() * scale) / 2,
                        (contentBound.height() - dialBound.height() * scale) / 2
                );
            } else if (mScaleType.equals(ImageView.ScaleType.CENTER_INSIDE)) {
                final float scale = Math.min(
                        contentBound.width() < dialBound.width() ? contentBound.width() / dialBound.width() : 1,
                        contentBound.height() < dialBound.height() ? contentBound.height() / dialBound.height() : 1
                );
                matrix.postScale(scale, scale);
                matrix.postTranslate(contentBound.width() / 2, contentBound.height() / 2);
                matrix.postTranslate(-dialBound.width() / 2, -dialBound.width() / 2);
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
        if (mTouchPoints == null || mTouchPoints.length != (n << 1))
            mTouchPoints = new float[n << 1];
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
        final Drawable oldDrawable = mDialDrawable;
        if (oldDrawable != null)
            oldDrawable.setCallback(null);
        if (mDialDrawable != drawable) {
            setScaleTypeInternal(mScaleType);
            mDialDrawable = drawable;
            if (mDialDrawable != null)
                mDialDrawable.setCallback(this);
            if (oldDrawable != null && mDialDrawable != null) {
                if (oldDrawable.getIntrinsicWidth() != mDialDrawable.getIntrinsicWidth() || oldDrawable.getIntrinsicHeight() != mDialDrawable.getIntrinsicHeight()) {
                    requestLayout();
                    return;
                }
            }
            postInvalidate();
        }
    }

    public void setHandDrawable(int index, @DrawableRes int drawable) {
        final HandOverlay hand = mHandOverlays[index];
        if (hand.drawableResId != drawable) {
            hand.drawableResId = drawable;
            setHandDrawableInternal(hand, ResourcesCompat.getDrawable(getResources(), drawable, getContext().getTheme()), 0.5f, 0.5f);
        }
    }

    public void setHandDrawable(int index, @Nullable Drawable drawable) {
        final HandOverlay hand = mHandOverlays[index];
        if (hand.drawable != drawable) {
            hand.drawableResId = -1;
            setHandDrawableInternal(hand, drawable, 0.5f, 0.5f);
        }
    }

    public void setHandDrawable(int index, @DrawableRes int drawable, float horizontal_bias, float vertical_bias) {
        final HandOverlay hand = mHandOverlays[index];
        if (hand.drawableResId != drawable) {
            hand.drawableResId = drawable;
            setHandDrawableInternal(hand, ResourcesCompat.getDrawable(getResources(), drawable, getContext().getTheme()), horizontal_bias, vertical_bias);
        }
    }

    public void setHandDrawable(int index, @Nullable Drawable drawable, float horizontal_bias, float vertical_bias) {
        final HandOverlay hand = mHandOverlays[index];
        if (hand.drawable != drawable) {
            hand.drawableResId = -1;
            setHandDrawableInternal(hand, drawable, horizontal_bias, vertical_bias);
        }
    }

    protected void setHandDrawableInternal(HandOverlay hand, @Nullable Drawable drawable, float horizontal_bias, float vertical_bias) {
        if (hand.drawable != null)
            hand.drawable.setCallback(null);
        hand.drawable = drawable;
        if (hand.drawable != null)
            hand.drawable.setCallback(this);
        hand.horizontal_bias = horizontal_bias;
        hand.vertical_bias = vertical_bias;
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

        if (mDrawReversed) {
            for (int i = mHandOverlays.length - 1; i >= 0; --i)
                drawHand(canvas, mDialDrawable, mHandOverlays[i]);
        } else {
            for (final HandOverlay hand : mHandOverlays)
                drawHand(canvas, mDialDrawable, hand);
        }

        canvas.restore();
    }

    private void drawHand(Canvas canvas, Drawable dial, HandOverlay hand) {
        if (hand.drawable == null)
            return;

        canvas.save();
        canvas.translate(dial.getIntrinsicWidth() * hand.horizontal_bias, dial.getIntrinsicHeight() * hand.vertical_bias);
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
            setScaleTypeInternal(mScaleType);
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

    private final float[] mDragHandCenter = new float[2];

    private void handleHandDrag(int index, float oldX, float oldY, float newX, float newY) {
        final HandOverlay hand = mHandOverlays[index];

        final int dialWidth = getDialWidth();
        final int dialHeight = getDialHeight();
        if (dialWidth == 0 || dialHeight == 0)
            return;

        final float angleOld = (float) Math.atan2(oldY - mDragHandCenter[1], oldX - mDragHandCenter[0]);
        final float angleNew = (float) Math.atan2(newY - mDragHandCenter[1], newX - mDragHandCenter[0]);

        final float oldValue = hand.value;
        hand.value += Math.toDegrees(angleNew - angleOld) / hand.division;
        if (mOnHandChangedListener != null)
            mOnHandChangedListener.onHandChanged(this, index, hand.value, oldValue);
        postInvalidateOnAnimation();
    }

    private int getHandByLocation(float x, float y) {
        // dial center
        final float dialWidth = getDialWidth();
        final float dialHeight = getDialHeight();
        if (dialWidth == 0 || dialHeight == 0)
            return -1;

        // collect tip position
        for (int i = 0; i < mHandOverlays.length; ++i) {
            final HandOverlay hand = mHandOverlays[i];
            if (hand.drawable == null)
                continue;
            final float handSize = hand.drawable.getIntrinsicHeight() / 2.0f;
            final float handAngle = (float) Math.toRadians(hand.value * hand.division - hand.startAngle);
            mTouchPoints[i << 1] = (float) (dialWidth * hand.horizontal_bias + Math.cos(handAngle) * handSize);
            mTouchPoints[(i << 1) + 1] = (float) (dialHeight * hand.vertical_bias + Math.sin(handAngle) * handSize);
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
            float distance = (float) Math.hypot(x - mTouchPoints[i << 1], y - mTouchPoints[(i << 1) + 1]);
            // Log.d(TAG, "getHandByLocation(): hand " + i + " - distance = " + distance);
            if (minDistance > distance) {
                minDistance = distance;
                index = i;
            }
        }

        mDragHandCenter[0] = dialWidth * mHandOverlays[index].horizontal_bias;
        mDragHandCenter[1] = dialHeight * mHandOverlays[index].vertical_bias;
        mMatrix.mapPoints(mDragHandCenter);
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

    public void setHandValue(int index, float toValue, boolean animate) {
        final HandOverlay hand = mHandOverlays[index];
        if (!animate) {
            if (hand.value != toValue) {
                hand.value = toValue;
                postInvalidateOnAnimation();
            }
        } else {
            if (hand.value != toValue) {
                if (hand.animator == null) {
                    hand.animator = new ValueAnimator();
                    hand.animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator animation) {
                            hand.value = (float) animation.getAnimatedValue();
                            postInvalidateOnAnimation();
                        }
                    });
                }
                hand.animator.cancel();
                final float fromValue1 = hand.value;
                final float fromValue2 = hand.value - 360.0f / hand.division;
                // Log.d(TAG, "setHandValue(): from " + fromValue1 + " (or " + fromValue2 + ") to " + toValue + ", chosen " + (Math.abs(fromValue1 - toValue) < Math.abs(fromValue2 - toValue) ? fromValue1 : fromValue2));
                hand.animator.setFloatValues(
                        Math.abs(fromValue1 - toValue) < Math.abs(fromValue2 - toValue) ?
                                fromValue1 :
                                fromValue2,
                        toValue
                );
                hand.animator.start();
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
