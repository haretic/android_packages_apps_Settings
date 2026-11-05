/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.password;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import com.android.settings.R;

/**
 * The lock screen's combination dial (SystemUI's WheelCombinationDial) for choosing a PIN, so the
 * PIN is entered as it will be unlocked: each digit is as many steps of the wheel one way, 1 to 9
 * and ten for 0, and turning back sets it and starts the next. The side button's press goes on,
 * its hold (back) takes back the digit being turned or the last one set, and a press with nothing
 * turned yet types the PIN instead. By touch, circling the dial turns it and a tap goes on.
 */
public final class WheelPinDial extends View {

    /** What the dial enters, through the PIN pad underneath. */
    public interface Listener {
        void onDigit(int digit);

        void onOk();

        void onDelete();

        /** The PIN is to be typed instead. */
        void onUseKeyboard();

        /** How many digits the PIN has so far. */
        int enteredLength();
    }

    /** The fewest digits a PIN has: fewer the lock doesn't try. */
    private static final int MIN_DIGITS = 4;
    /** Steps for 0, and as many as the dial's notches. */
    private static final int NOTCHES = 10;
    private static final float NOTCH_DEGREES = 360f / NOTCHES;
    /** The bezel's small ticks between its notches. */
    private static final int TICKS_PER_NOTCH = 4;

    private final Paint mBezel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFace = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRun = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mIndex = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDigit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mHint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPin = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mArc = new RectF();
    private final Path mPointer = new Path();
    private final int[] mOnScreen = new int[2];
    private final float mTouchSlop;
    private final Vibrator mVibrator;
    private final VibrationEffect mSet;
    private final VibrationAttributes mAttributes =
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH);
    private final int mPrimary;
    private final int mOnSurfaceVariant;

    private Listener mListener;
    /** The way the digit being turned goes, 1 down (clockwise), -1 up, 0 none yet. */
    private int mRunDirection;
    /** Its steps so far, 1 to NOTCHES. */
    private int mRunSteps;
    /** The dial's turn in notches, animated towards mTargetTurn. */
    private float mTurn;
    private int mTargetTurn;
    private ValueAnimator mTurnAnimator;
    /** 1 just after a digit was set, fading to 0: its arc closes into the index. */
    private float mSetFlash;
    private int mSetSteps;
    private int mSetDirection;
    private ValueAnimator mSetAnimator;
    /** Where the dial is, laid out in onSizeChanged. */
    private float mCx;
    private float mCy;
    private float mRadius;
    private float mHintTop;
    /** Touch: the angle the finger was at, and whether it circles rather than taps. */
    private float mLastAngle;
    private float mAngleTravel;
    private boolean mCircling;
    private float mDownX;
    private float mDownY;
    /** The dial's shake, a wrong PIN's or a short one's, as a lock's handle that won't turn. */
    private ValueAnimator mShakeAnimator;
    /** Whether the last press was on too few digits to try, its hint shown until a turn. */
    private boolean mTooShort;
    /** Back, the side button's hold, takes back a digit while there is one. */
    private final OnBackInvokedCallback mTakeBack = this::takeBack;
    private OnBackInvokedDispatcher mBackDispatcher;
    private boolean mTakingBack;

    public WheelPinDial(Context context) {
        super(context);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mVibrator = context.getSystemService(Vibrator.class);
        mSet = mVibrator != null
                        && mVibrator.areAllPrimitivesSupported(
                                VibrationEffect.Composition.PRIMITIVE_THUD)
                ? VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.8f)
                        .compose()
                : VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK);
        mPrimary = context.getColor(com.android.internal.R.color.materialColorPrimary);
        mOnSurfaceVariant =
                context.getColor(com.android.internal.R.color.materialColorOnSurfaceVariant);
        mBezel.setColor(
                context.getColor(com.android.internal.R.color.materialColorSurfaceContainerHigh));
        mFace.setColor(
                context.getColor(com.android.internal.R.color.materialColorSurfaceContainer));
        mTick.setColor(mOnSurfaceVariant);
        mTick.setStrokeCap(Paint.Cap.ROUND);
        mRun.setColor(mPrimary);
        mRun.setStyle(Paint.Style.STROKE);
        mRun.setStrokeCap(Paint.Cap.ROUND);
        mIndex.setColor(mPrimary);
        mDigit.setTextAlign(Paint.Align.CENTER);
        mDigit.setColor(context.getColor(com.android.internal.R.color.materialColorOnSurface));
        mHint.setTextAlign(Paint.Align.CENTER);
        mHint.setTextSize(sp(15));
        mHint.setColor(mOnSurfaceVariant);
        mPin.setColor(mPrimary);
        setClickable(true);
        setFocusable(true);
        updateDescription();
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    /** Nothing turned, as for a new PIN. */
    public void reset() {
        mTooShort = false;
        mRunDirection = 0;
        mRunSteps = 0;
        mSetFlash = 0;
        updateDescription();
        updateTakeBack();
        invalidate();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_DOWN:
                turn(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                turn(-1);
                return true;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (event.getRepeatCount() == 0) {
                    tryPin();
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                // Windows that still take back as a key
                if (hasTakeBack()) {
                    if (event.getRepeatCount() == 0) {
                        takeBack();
                    }
                    return true;
                }
                return super.onKeyDown(keyCode, event);
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    private boolean hasTakeBack() {
        return mRunSteps > 0 || (mListener != null && mListener.enteredLength() > 0);
    }

    /** Back takes back a digit while there is one, and leaves the page after. */
    public void updateTakeBack() {
        final boolean want = hasTakeBack() && isShown();
        if (want == mTakingBack) {
            return;
        }
        if (want) {
            mBackDispatcher = findOnBackInvokedDispatcher();
            if (mBackDispatcher == null) {
                return;
            }
            mBackDispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_OVERLAY, mTakeBack);
        } else if (mBackDispatcher != null) {
            mBackDispatcher.unregisterOnBackInvokedCallback(mTakeBack);
        }
        mTakingBack = want;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateTakeBack();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mTakingBack && mBackDispatcher != null) {
            mBackDispatcher.unregisterOnBackInvokedCallback(mTakeBack);
        }
        mTakingBack = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateTakeBack();
    }

    private void turn(int direction) {
        mTooShort = false;
        if (mRunDirection != 0 && direction != mRunDirection) {
            // Turning back sets the digit turned so far, and this step is the next one's first
            setDigit();
        }
        mRunDirection = direction;
        // Past ten steps the count goes round again, as the dial does
        mRunSteps = mRunSteps % NOTCHES + 1;
        spin(direction);
        updateDescription();
        updateTakeBack();
        invalidate();
    }

    private void setDigit() {
        if (mRunSteps == 0) {
            return;
        }
        mSetSteps = mRunSteps;
        mSetDirection = mRunDirection;
        final int digit = mRunSteps % NOTCHES;
        mRunDirection = 0;
        mRunSteps = 0;
        if (mVibrator != null) {
            mVibrator.vibrate(mSet, mAttributes);
        }
        if (mListener != null) {
            mListener.onDigit(digit);
        }
        if (mSetAnimator != null) {
            mSetAnimator.cancel();
        }
        mSetAnimator = ValueAnimator.ofFloat(1f, 0f);
        mSetAnimator.setDuration(320);
        mSetAnimator.setInterpolator(new DecelerateInterpolator());
        mSetAnimator.addUpdateListener(a -> {
            mSetFlash = (float) a.getAnimatedValue();
            invalidate();
        });
        mSetAnimator.start();
    }

    private void tryPin() {
        if (mListener == null) {
            return;
        }
        final int entered = mListener.enteredLength();
        if (mRunSteps == 0 && entered == 0) {
            mListener.onUseKeyboard();
            return;
        }
        if (entered + (mRunSteps > 0 ? 1 : 0) < MIN_DIGITS) {
            // Not tried, as the lock would let it go without a word: the dial says so instead
            mTooShort = true;
            shake();
            invalidate();
            return;
        }
        setDigit();
        mListener.onOk();
        updateDescription();
    }

    private void takeBack() {
        if (mRunSteps > 0) {
            // The digit being turned, not yet set: the dial back to where it started
            spin(-mRunDirection * mRunSteps);
            mRunDirection = 0;
            mRunSteps = 0;
        } else if (mListener != null) {
            mListener.onDelete();
        }
        updateDescription();
        updateTakeBack();
        invalidate();
    }

    /** A shake side to side, settling, as a lock's handle tried while still locked. */
    public void shake() {
        if (mShakeAnimator != null) {
            mShakeAnimator.cancel();
        }
        mShakeAnimator = ValueAnimator.ofFloat(0f, 1f);
        mShakeAnimator.setDuration(420);
        mShakeAnimator.addUpdateListener(a -> {
            final float t = (float) a.getAnimatedValue();
            setTranslationX((float) (Math.sin(t * Math.PI * 6) * (1 - t) * dp(10)));
        });
        mShakeAnimator.start();
        if (mVibrator != null) {
            mVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK),
                    mAttributes);
        }
    }

    /** The dial's face round by {@code notches}, smoothly, as a heavy dial follows the hand. */
    private void spin(int notches) {
        mTargetTurn += notches;
        if (mTurnAnimator != null) {
            mTurnAnimator.cancel();
        }
        mTurnAnimator = ValueAnimator.ofFloat(mTurn, mTargetTurn);
        mTurnAnimator.setDuration(Math.abs(notches) > 1 ? 260 : 140);
        mTurnAnimator.setInterpolator(new OvershootInterpolator(1.4f));
        mTurnAnimator.addUpdateListener(a -> {
            mTurn = (float) a.getAnimatedValue();
            invalidate();
        });
        mTurnAnimator.start();
    }

    @Override
    public boolean performClick() {
        super.performClick();
        tryPin();
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final float x = event.getX();
        final float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = x;
                mDownY = y;
                mLastAngle = angle(x, y);
                mAngleTravel = 0;
                mCircling = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (!mCircling && Math.hypot(x - mDownX, y - mDownY) > mTouchSlop) {
                    mCircling = true;
                }
                if (!mCircling) {
                    return true;
                }
                final float now = angle(x, y);
                float delta = now - mLastAngle;
                if (delta > 180) {
                    delta -= 360;
                } else if (delta < -180) {
                    delta += 360;
                }
                mLastAngle = now;
                mAngleTravel += delta;
                // Clockwise is down the wheel, a notch a step
                while (mAngleTravel >= NOTCH_DEGREES) {
                    mAngleTravel -= NOTCH_DEGREES;
                    turn(1);
                    performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                }
                while (mAngleTravel <= -NOTCH_DEGREES) {
                    mAngleTravel += NOTCH_DEGREES;
                    turn(-1);
                    performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (!mCircling) {
                    if (y >= mHintTop && mListener != null) {
                        // The hints: typing the PIN instead
                        mListener.onUseKeyboard();
                    } else {
                        performClick();
                    }
                }
                return true;
            default:
                return true;
        }
    }

    /** The angle of a point round the dial's centre, clockwise from the top, in degrees. */
    private float angle(float x, float y) {
        return (float) Math.toDegrees(Math.atan2(x - mCx, mCy - y));
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(
                getDefaultSize(getSuggestedMinimumWidth(), widthSpec),
                resolveSize((int) Math.min(dp(330),
                        getResources().getDisplayMetrics().heightPixels * 0.5f), heightSpec));
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        // The dial as large as fits over its two lines of hints, smaller on a short screen
        final float hints = dp(56);
        mRadius = Math.max(dp(56), Math.min(width / 2f - dp(24), (height - hints) / 2f - dp(8)));
        mCx = width / 2f;
        mCy = dp(12) + mRadius;
        mHintTop = mCy + mRadius + dp(6);
        mRun.setStrokeWidth(dp(7));
        mTick.setStrokeWidth(dp(2));
        mDigit.setTextSize(Math.min(sp(56), mRadius * 0.7f));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final float bezel = mRadius;
        final float face = mRadius * 0.74f;
        canvas.drawCircle(mCx, mCy, bezel, mBezel);
        canvas.drawCircle(mCx, mCy, face, mFace);

        // The bezel's notches turn with the dial; every notch a step, the long ones every five
        canvas.save();
        canvas.rotate(mTurn * NOTCH_DEGREES, mCx, mCy);
        final int ticks = NOTCHES * TICKS_PER_NOTCH;
        for (int i = 0; i < ticks; i++) {
            final boolean notch = i % TICKS_PER_NOTCH == 0;
            final boolean major = notch && (i / TICKS_PER_NOTCH) % 5 == 0;
            final float outer = bezel - dp(7);
            final float inner = outer - (major ? dp(14) : notch ? dp(10) : dp(4));
            mTick.setAlpha(notch ? 255 : 110);
            mTick.setStrokeWidth(major ? dp(3) : dp(2));
            canvas.drawLine(mCx, mCy - outer, mCx, mCy - inner, mTick);
            canvas.rotate(360f / ticks, mCx, mCy);
        }
        canvas.restore();

        // The digit being turned: an arc from the index, a notch a step, the way it turns
        final float arcRadius = (bezel + face) / 2;
        mArc.set(mCx - arcRadius, mCy - arcRadius, mCx + arcRadius, mCy + arcRadius);
        if (mRunSteps > 0) {
            mRun.setAlpha(255);
            canvas.drawArc(mArc, -90, mRunDirection * mRunSteps * NOTCH_DEGREES, false, mRun);
        } else if (mSetFlash > 0) {
            // The one just set closes back into the index
            mRun.setAlpha((int) (255 * mSetFlash));
            canvas.drawArc(mArc, -90, mSetDirection * mSetSteps * NOTCH_DEGREES * mSetFlash,
                    false, mRun);
        }

        // The index, a safe's mark over the top of the dial
        mPointer.reset();
        final float tip = mCy - bezel + dp(2);
        mPointer.moveTo(mCx, tip + dp(1));
        mPointer.lineTo(mCx - dp(8), tip - dp(11));
        mPointer.lineTo(mCx + dp(8), tip - dp(11));
        mPointer.close();
        canvas.drawPath(mPointer, mIndex);

        // In the middle the digit being turned; under it the digits set, as a safe's pins
        final int entered = mListener == null ? 0 : mListener.enteredLength();
        if (mRunSteps > 0) {
            mDigit.setColor(mPrimary);
            canvas.drawText(Integer.toString(mRunSteps % NOTCHES), mCx,
                    mCy - (mDigit.ascent() + mDigit.descent()) / 2, mDigit);
        } else {
            mDigit.setColor(mOnSurfaceVariant);
            final float size = mDigit.getTextSize();
            mDigit.setTextSize(sp(17));
            canvas.drawText(getContext().getString(R.string.wheel_pin_dial_idle), mCx,
                    mCy - (mDigit.ascent() + mDigit.descent()) / 2, mDigit);
            mDigit.setTextSize(size);
        }
        drawPins(canvas, entered, face);

        final float line = mHintTop + dp(18);
        canvas.drawText(getContext().getString(firstHint(entered)), mCx, line, mHint);
        canvas.drawText(getContext().getString(secondHint(entered)), mCx, line + dp(22), mHint);
    }

    /**
     * The digits set so far as pins standing up low in the dial's face, a row of up to eight: the
     * PIN's own dots above, which the dial takes the place of.
     */
    private void drawPins(Canvas canvas, int entered, float face) {
        final int shown = Math.min(entered, 8);
        if (shown == 0) {
            return;
        }
        final float gap = Math.min(dp(14), face * 1.2f / 8);
        final float width = dp(5);
        final float height = Math.min(dp(16), face * 0.16f);
        final float middle = mCy + face * 0.6f;
        final float left = mCx - gap * (shown - 1) / 2;
        for (int i = 0; i < shown; i++) {
            final float x = left + gap * i;
            // The newest pin springs up as it is set
            final float lift = i == shown - 1 ? mSetFlash * dp(5) : 0;
            canvas.drawRoundRect(x - width / 2, middle - height / 2 - lift, x + width / 2,
                    middle + height / 2 - lift, width / 2, width / 2, mPin);
        }
    }

    private int firstHint(int entered) {
        if (mTooShort) {
            return R.string.wheel_pin_dial_hint_short;
        }
        if (mRunSteps > 0) {
            return R.string.wheel_pin_dial_hint_back;
        }
        return entered == 0 ? R.string.wheel_pin_dial_hint_start : R.string.wheel_pin_dial_hint_next;
    }

    private int secondHint(int entered) {
        return mRunSteps > 0 || entered > 0
                ? R.string.wheel_pin_dial_hint_press
                : R.string.wheel_pin_dial_hint_keyboard;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        // The wheel's ring goes round the dial itself, not its hints
        getLocationOnScreen(mOnScreen);
        info.setBoundsInScreen(new Rect(
                mOnScreen[0] + (int) (mCx - mRadius),
                mOnScreen[1] + (int) (mCy - mRadius),
                mOnScreen[0] + (int) (mCx + mRadius),
                mOnScreen[1] + (int) (mCy + mRadius)));
    }

    private void updateDescription() {
        setContentDescription(getContext().getString(R.string.wheel_pin_dial_description));
        setStateDescription(mRunSteps > 0
                ? Integer.toString(mRunSteps % NOTCHES)
                : getContext().getString(R.string.wheel_pin_dial_state_idle));
    }

    private float dp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private float sp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }
}
