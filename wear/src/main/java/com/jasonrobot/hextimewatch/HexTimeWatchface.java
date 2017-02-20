/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.jasonrobot.hextimewatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 */
public class HexTimeWatchface extends CanvasWatchFaceService {

    /*
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = 83;

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<HexTimeWatchface.Engine> mWeakReference;

        public EngineHandler(HexTimeWatchface.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            HexTimeWatchface.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        private static final int SHADOW_RADIUS = 6;
        private final Rect mPeekCardBounds = new Rect();
        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new EngineHandler(this);
        private Calendar cal;
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                cal.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean mRegisteredTimeZoneReceiver = false;
        private float centerX;
        private float centerY;
        private boolean mAmbient;
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;

        private float h1Length;
        private float h2Length;
        private float h3Length;
        private float h4Length;

        private Paint h1Paint;
        private Paint h2Paint;
        private Paint h3Paint;
        private Paint h4Paint;
        private Paint textPaint;

        // for some reason, these powers dont work right....???
        int[] hexPowers = {1, 16, 256, 4096, 65536, 1048576, 16777216};

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            cal = Calendar.getInstance();

            h1Paint = new Paint();
            h1Paint.setARGB(0xFF, 0xDD, 0x20, 0x20);
            h1Paint.setStrokeWidth(15);

            h2Paint = new Paint();
            h2Paint.setARGB(0xFF, 0xDD, 0xDD, 0xDD);
            h2Paint.setStrokeWidth(15);

            h3Paint = new Paint(h1Paint);

            h4Paint = new Paint(h2Paint);

            textPaint = new Paint();
            textPaint.setARGB(0xFF, 0xDD, 0xDD, 0xDD);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(getResources().getDimensionPixelSize(R.dimen.digitTextSizeInAnalog));

            invalidate();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            mAmbient = inAmbientMode;

            updateWatchHandStyle();

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void updateWatchHandStyle() {
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);

            /* Dim display in mute mode. */
//            if (mMuteMode != inMuteMode) {
//            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            /*
             * Find the coordinates of the centerX point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            centerX = width / 2f;
            centerY = height / 2f;

            /*
             * Calculate lengths of different hands based on watch screen size.
             */
            h1Length = (float) (centerX * 0.5);
            h2Length = (float) (centerX * 0.65);
            h3Length = (float) (centerX * 0.8);
            h4Length = (float) (centerX * 0.9);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            //blank the canvas
            canvas.drawColor(Color.BLACK);

            // Update the time
            cal.setTimeInMillis(System.currentTimeMillis());
            cal.setTimeZone(TimeZone.getTimeZone("PST"));

            // Constant to help calculate clock hand rotations
            final float TWO_PI = (float) Math.PI * 2f;
            final float SIXTEENTH = TWO_PI / 16;

            int width = bounds.width();
            int height = bounds.height();

            int h5 = calculateHexTime(5);

            int h4 = calculateHexTime(4);
            float h4rot = SIXTEENTH * (h4 + (h5 / 16f) + 8);
            System.out.println("h4 " + h4 + ", h5 " + h5 + ", and " + (h5 / 16f));
            float h4x = (float) (Math.sin(h4rot) * h4Length) + centerX;
            float h4y = (float) (-Math.cos(h4rot) * h4Length) + centerY;
            canvas.drawLine(centerX, centerY, h4x, h4y, h4Paint);

            int h3 = calculateHexTime(3);
            float h3rot = SIXTEENTH * (h3 + (h4 / 16f) + 8);
            float h3x = (float) (Math.sin(h3rot) * h3Length) + centerX;
            float h3y = (float) (-Math.cos(h3rot) * h3Length) + centerY;
            canvas.drawLine(centerX, centerY, h3x, h3y, h3Paint);

            int h2 = calculateHexTime(2);
            float h2rot = SIXTEENTH * (h2 + (h3 / 16f) + 8);
            float h2x = (float) (Math.sin(h2rot) * h2Length) + centerX;
            float h2y = (float) (-Math.cos(h2rot) * h2Length) + centerY;
            canvas.drawLine(centerX, centerY, h2x, h2y, h2Paint);

            int h1 = calculateHexTime(1);
            float h1rot = SIXTEENTH * (h1 + (h2 / 16f) + 8);
            float h1x = (float) (Math.sin(h1rot) * h1Length) + centerX;
            float h1y = (float) (-Math.cos(h1rot) * h1Length) + centerY;
            canvas.drawLine(centerX, centerY, h1x, h1y, h1Paint);

            canvas.drawText(toHex(h1) + ":" + toHex(h2) + ":" + toHex(h3) + "," + toHex(h4) + "." + toHex(h5),
                    centerX, getResources().getDimensionPixelSize(R.dimen.digitTextSizeInAnalog), textPaint);

            canvas.drawText(cal.get(Calendar.HOUR) + ":" + cal.get(Calendar.MINUTE),
                    centerX, bounds.height() - getResources().getDimensionPixelSize(R.dimen.digitTextSizeInAnalog), textPaint);
        }

        /**
         * Calculate the current hex times
         *
         * @param subdiv which subdivision to calculate. (1 for h1, 2 for h2, etc)
         *
         * @return whole integer of the time
         */
        private int calculateHexTime(int subdiv) {
            long timeInDay = (cal.getTimeInMillis() % 86400000);
            return (int) (timeInDay / (86400000 / Math.pow(16, subdiv)) % 16);
        }

        private char toHex(int val) {
            return Character.toUpperCase(Character.forDigit(val, 16));
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                cal.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        @Override
        public void onPeekCardPositionUpdate(Rect rect) {
            super.onPeekCardPositionUpdate(rect);
            mPeekCardBounds.set(rect);
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            HexTimeWatchface.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            HexTimeWatchface.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts/stops the {@link #mUpdateTimeHandler} timer based on the state of the watch face.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !mAmbient;
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
