package com.example.mpdemo;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

/**
 * 黑胶唱片自定义视图，实现旋转动画效果
 */
public class RecordView extends View {
    private Bitmap recordBitmap; // 黑胶唱片位图
    private Bitmap centerCapBitmap; // 唱片中心盖位图
    private Bitmap defaultAlbumArt; // 默认专辑封面

    private ValueAnimator animator; // 旋转动画器
    private float rotationAngle = 0; // 旋转角度
    private Paint paint; // 绘制画笔

    public RecordView(Context context) {
        super(context);
        init();
    }

    public RecordView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RecordView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        paint.setDither(true);
    }

    /**
     * 设置专辑封面
     */
    public void setAlbumArt(Bitmap albumArt) {
        this.defaultAlbumArt = albumArt;
        invalidate(); // 重绘视图
    }

    /**
     * 开始旋转动画
     */
    public void startRotation() {
        if (animator != null && animator.isRunning()) {
            return; // 如果动画已在运行，则直接返回
        }

        // 创建旋转动画，从0度到360度无限循环
        animator = ValueAnimator.ofFloat(0f, 360f);
        animator.setDuration(2000); // 一圈2秒，模拟唱片转速
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator()); // 线性插值器，保持匀速

        animator.addUpdateListener(animation -> {
            rotationAngle = (float) animation.getAnimatedValue();
            invalidate(); // 请求重绘
        });

        animator.start();
    }

    /**
     * 停止旋转动画
     */
    public void stopRotation() {
        if (animator != null) {
            animator.cancel();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (getWidth() == 0 || getHeight() == 0) {
            return; // 如果视图大小为0，则直接返回
        }

        int size = Math.min(getWidth(), getHeight());
        float radius = size / 2f * 0.8f; // 唱片半径占视图大小的80%
        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2;

        // 保存画布状态
        canvas.save();

        // 绕中心点旋转
        canvas.rotate(rotationAngle, centerX, centerY);

        // 绘制黑胶唱片
        if (recordBitmap == null || recordBitmap.isRecycled()) {
            // 创建黑胶唱片位图
            recordBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas recordCanvas = new Canvas(recordBitmap);

            // 绘制黑色圆盘
            Paint recordPaint = new Paint();
            recordPaint.setAntiAlias(true);
            recordPaint.setColor(0xFF222222); // 深灰色，模拟黑胶质感
            recordCanvas.drawCircle(size/2, size/2, radius, recordPaint);

            // 绘制唱片沟槽效果
            Paint groovePaint = new Paint();
            groovePaint.setAntiAlias(true);
            groovePaint.setStyle(Paint.Style.STROKE);
            groovePaint.setStrokeWidth(2f);
            groovePaint.setColor(0x33FFFFFF); // 半透明白色

            // 绘制同心圆沟槽
            for (int i = 1; i <= 15; i++) {
                float grooveRadius = radius * (0.9f - 0.05f * i);
                recordCanvas.drawCircle(size/2, size/2, grooveRadius, groovePaint);
            }
        }

        // 将唱片绘制到主画布上
        canvas.drawBitmap(recordBitmap,
                         centerX - recordBitmap.getWidth()/2,
                         centerY - recordBitmap.getHeight()/2,
                         paint);

        // 如果有专辑封面，绘制到唱片上
        if (defaultAlbumArt != null) {
            drawAlbumArtOnRecord(canvas, centerX, centerY);
        }

        // 恢复画布状态
        canvas.restore();

        // 绘制中心盖，不参与旋转
        if (centerCapBitmap == null || centerCapBitmap.isRecycled()) {
            // 创建中心盖位图
            int capSize = size / 8; // 中心盖大小为视图的1/8
            centerCapBitmap = Bitmap.createBitmap(capSize, capSize, Bitmap.Config.ARGB_8888);
            Canvas capCanvas = new Canvas(centerCapBitmap);

            Paint capPaint = new Paint();
            capPaint.setAntiAlias(true);
            capPaint.setColor(0xFFFFFFFF); // 白色中心盖
            capCanvas.drawCircle(capSize/2, capSize/2, capSize/2, capPaint);

            // 绘制中心小孔
            Paint holePaint = new Paint();
            holePaint.setAntiAlias(true);
            holePaint.setColor(0xFF000000); // 黑色小孔
            capCanvas.drawCircle(capSize/2, capSize/2, capSize/8, holePaint);
        }

        // 绘制中心盖到主画布上
        canvas.drawBitmap(centerCapBitmap,
                         centerX - centerCapBitmap.getWidth()/2,
                         centerY - centerCapBitmap.getHeight()/2,
                         paint);
    }

    private void drawAlbumArtOnRecord(Canvas canvas, int centerX, int centerY) {
        if (defaultAlbumArt == null) return;

        int size = Math.min(getWidth(), getHeight());
        float radius = size / 2f * 0.8f; // 专辑封面半径略小于黑胶半径

        // 创建圆形遮罩
        Bitmap circleBitmap = Bitmap.createBitmap((int)(radius*2), (int)(radius*2), Bitmap.Config.ARGB_8888);
        Canvas circleCanvas = new Canvas(circleBitmap);
        Paint circlePaint = new Paint();
        circlePaint.setAntiAlias(true);
        circlePaint.setFilterBitmap(true);
        circlePaint.setDither(true);

        // 绘制裁剪圆形
        circleCanvas.drawCircle(radius, radius, radius, circlePaint);

        // 设置遮罩模式
        circlePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));

        // 缩放专辑封面以适应圆形区域
        Bitmap scaledAlbumArt = Bitmap.createScaledBitmap(defaultAlbumArt, (int)(radius*2), (int)(radius*2), true);
        circleCanvas.drawBitmap(scaledAlbumArt, 0, 0, circlePaint);

        // 绘制到主画布上
        canvas.drawBitmap(circleBitmap,
                         centerX - radius,
                         centerY - radius,
                         paint);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
        }
        releaseBitmaps();
    }

    private void releaseBitmaps() {
        if (recordBitmap != null && !recordBitmap.isRecycled()) {
            recordBitmap.recycle();
            recordBitmap = null;
        }
        if (centerCapBitmap != null && !centerCapBitmap.isRecycled()) {
            centerCapBitmap.recycle();
            centerCapBitmap = null;
        }
        if (defaultAlbumArt != null && !defaultAlbumArt.isRecycled()) {
            defaultAlbumArt.recycle();
            defaultAlbumArt = null;
        }
    }

    /**
     * 检查动画是否正在运行
     */
    public boolean isRotating() {
        return animator != null && animator.isRunning();
    }
}