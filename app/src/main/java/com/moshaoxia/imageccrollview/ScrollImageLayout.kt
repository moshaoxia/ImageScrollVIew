package com.moshaoxia.imageccrollview

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.IntDef
import androidx.annotation.Nullable


/**
 * Author : moshaoxia
 * Date : 2021/3/19
 * Introduction:背景长图无限滚动控件
 * 功能特点：
 *
 */
class ScrollImageLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    /**
     * 间隔时间内平移距离
     */
    private var mTransDistance = 0f

    /**
     * 间隔时间内平移增距
     */
    private var mIntervalIncreaseDistance: Float

    /**
     * 填满当前view所需bitmap个数
     */
    private var mBitmapCount = 0

    /**
     * 是否开始滚动
     */
    private var mIsScroll: Boolean

    /**
     * 滚动方向：上移/左移，默认上移
     */
    private var mScrollOrientation: Int

    /**
     * 遮罩
     */
    private var mMaskDrawable: Drawable?

    private val mDrawable: Drawable?
    private var mScrollBitmap: Bitmap? = null
    private val mPaint: Paint
    private val mMatrix: Matrix
    private val scrollTime: Int
    private var startTime = 0L
    private var mRCHelper = RoundCornerHelper()

    @IntDef(ORIENTATION_HORIZONTAL, ORIENTATION_VERTICAL)
    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    annotation class Orientation

    companion object {
        private const val TAG = "SrcScrollFrameLayout"

        /**
         * 重绘间隔时间，取60FPS
         */
        private const val DEFAULT_DRAW_INTERVALS_TIME = 16.66F

        const val ORIENTATION_HORIZONTAL = 0
        const val ORIENTATION_VERTICAL = 1
    }

    init {
        val array = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.ScrollImageView,
            defStyleAttr,
            0
        )
        mRCHelper.initAttrs(context, attrs)
        val speed = array.getInteger(R.styleable.ScrollImageView_speed, 5)
        mScrollOrientation = array.getInteger(
            R.styleable.ScrollImageView_scrollOrientation,
            ORIENTATION_HORIZONTAL
        )
        mIntervalIncreaseDistance = speed.toFloat()
        mDrawable = array.getDrawable(R.styleable.ScrollImageView_src)
        mIsScroll = array.getBoolean(R.styleable.ScrollImageView_scroll, true)
        mMaskDrawable = array.getDrawable(R.styleable.ScrollImageView_mask)
        scrollTime = array.getInt(R.styleable.ScrollImageView_scrollTime, -1)
        array.recycle()
        setWillNotDraw(false)
        mPaint = Paint()
        mMatrix = Matrix()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (mDrawable == null || mDrawable !is BitmapDrawable) {
            return
        }
        if (visibility == GONE) {
            return
        }
        if (w == 0 || h == 0) {
            return
        }
        if (mScrollBitmap == null) {
            val bitmap = mDrawable.bitmap
            //调整色彩模式进行质量压缩
            val compressBitmap = bitmap.copy(Bitmap.Config.RGB_565, true)
            //缩放 Bitmap
            mScrollBitmap = resizeBitmapByRatio(compressBitmap)
            calculateIncreaseDistance()
            calculateBitmapCount()
            recycleBitmap(compressBitmap)
        }
        mRCHelper.onSizeChanged(this, w, h)
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.saveLayer(mRCHelper.mLayer, null, Canvas.ALL_SAVE_FLAG)
        super.dispatchDraw(canvas)
        mRCHelper.onClipDraw(canvas)
        canvas.restore()
    }

    private fun calculateIncreaseDistance() {
        if (scrollTime > 0) {
            //有配置完整滚动一张大图所需要的时间
            mIntervalIncreaseDistance = if (scrollOrientationIsVertical()) {
                mScrollBitmap!!.height / (scrollTime * 1000f / DEFAULT_DRAW_INTERVALS_TIME)
            } else {
                mScrollBitmap!!.width / (scrollTime * (1000f / DEFAULT_DRAW_INTERVALS_TIME))
            }
        }
    }

    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.clipPath(mRCHelper.mClipPath)
        super.draw(canvas)
        canvas.restore()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mScrollBitmap == null) {
            return
        }
        updateTransDistance()
        for (i in 0 until mBitmapCount) {
            mMatrix.reset()
            if (scrollOrientationIsVertical()) {
                mMatrix.postTranslate(0f, i * mScrollBitmap!!.height + mTransDistance)
            } else {
                mMatrix.postTranslate(i * mScrollBitmap!!.width + mTransDistance, 0f)
            }
            canvas.drawBitmap(mScrollBitmap!!, mMatrix, mPaint)
        }
        //如果平移的量已经比填充的bitmap宽度加起来 - view的宽度还要多,说明后面就有留白了，此时需要再绘制一张图补上
        mMatrix.reset()
        if (scrollOrientationIsVertical()) {
            if (mScrollBitmap!!.height * mBitmapCount - measuredHeight < Math.abs(mTransDistance)) {
                mMatrix.postTranslate(0f, mBitmapCount * mScrollBitmap!!.height + mTransDistance)
                canvas.drawBitmap(mScrollBitmap!!, mMatrix, mPaint)
            }
        } else {
            if (mScrollBitmap!!.width * mBitmapCount - measuredWidth < Math.abs(mTransDistance)) {
                mMatrix.postTranslate(mBitmapCount * mScrollBitmap!!.width + mTransDistance, 0f)
                canvas.drawBitmap(mScrollBitmap!!, mMatrix, mPaint)
            }
        }
        //绘制遮罩层
        if (mMaskDrawable != null) {
            val bm = drawable2Bitmap(mMaskDrawable)
            if (bm != null) {
                canvas.drawBitmap(bm, 0f, 0f, mPaint)
            }
        }
        //再次重绘，改变位置实现滚动效果
        if (mIsScroll) {
            if (scrollTime != -1) {
                handler.postDelayed({
                    invalidate()
                    //考虑到按时间计算动画是用60PPS，所以手动间隔16ms重绘
                }, 16)
            } else {
                invalidate()
            }
        }
    }

    private fun updateTransDistance() {
        val length =
            if (scrollOrientationIsVertical()) mScrollBitmap?.height else mScrollBitmap?.width
        if (length != null) {
            if (length + mTransDistance <= 0) {
                //第一张已完全滚出屏幕，重置平移距离
                mTransDistance = 0f
            }
        }
        mTransDistance -= mIntervalIncreaseDistance
    }

    /**
     * 开始滚动
     */
    fun startScroll() {
        startTime = System.currentTimeMillis()
        if (mIsScroll) {
            return
        }
        mIsScroll = true
        invalidate()
    }

    /**
     * 停止滚动
     */
    fun stopScroll() {
        if (!mIsScroll) {
            return
        }
        mIsScroll = false
    }

    fun setMaskDrawable(drawable: Drawable?) {
        mMaskDrawable = drawable
        invalidate()
    }

    /**
     * 代码动态设置 bitmap
     *
     */
    fun setScrollBitmap(bitmap: Bitmap) {
        val oldScrollStatus = mIsScroll
        if (oldScrollStatus) {
            stopScroll()
        }
        val compressBitmap: Bitmap
        if (bitmap.config != Bitmap.Config.RGB_565) {
            if (bitmap.isMutable) {
                bitmap.config = Bitmap.Config.RGB_565
                compressBitmap = bitmap
            } else {
                compressBitmap = bitmap.copy(Bitmap.Config.RGB_565, true)
            }
        } else {
            compressBitmap = bitmap
        }
        //按当前View宽度比例缩放 Bitmap
        mScrollBitmap = resizeBitmapByRatio(compressBitmap)
        calculateIncreaseDistance()
        calculateBitmapCount()
        recycleBitmap(bitmap)
        recycleBitmap(compressBitmap)
        if (oldScrollStatus) {
            startScroll()
        }
    }

    /**
     * 计算至少需要几个 bitmap 才能填满当前 view
     */
    private fun calculateBitmapCount() {
        //计算至少需要几个 bitmap 才能填满当前 view
        mBitmapCount =
            if (scrollOrientationIsVertical()) measuredHeight / mScrollBitmap!!.height + 1 else measuredWidth / mScrollBitmap!!.width + 1
    }

    private fun recycleBitmap(bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            bitmap.recycle()
            System.gc()
            Log.i(TAG, "trigger recycleBitmap")
        }
    }

    private fun scrollOrientationIsVertical(): Boolean {
        return mScrollOrientation == 1
    }

    /**
     * 将滑动图片结合方向按比例缩放铺满整个View
     */
    private fun resizeBitmapByRatio(originBitmap: Bitmap): Bitmap {
        val width = originBitmap.width
        val height = originBitmap.height
        val newHeight: Int
        val newWidth: Int
        if (scrollOrientationIsVertical()) {
            newWidth = measuredWidth
            newHeight = newWidth * height / width
        } else {
            newHeight = measuredHeight
            newWidth = newHeight * width / height
        }
        return Bitmap.createScaledBitmap(originBitmap, newWidth, newHeight, true)
    }

    /**
     * Drawable to bitmap.
     *
     * @param drawable The drawable.
     * @return bitmap
     */
    private fun drawable2Bitmap(@Nullable drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable) {
            if (drawable.bitmap != null) {
                return drawable.bitmap
            }
        }
        val bitmap: Bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
            Bitmap.createBitmap(
                measuredWidth, measuredHeight,
                if (drawable.opacity != PixelFormat.OPAQUE) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
            )
        } else {
            Bitmap.createBitmap(
                drawable.intrinsicWidth,
                drawable.intrinsicHeight,
                if (drawable.opacity != PixelFormat.OPAQUE) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
            )
        }
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    fun setScrollOrientation(@Orientation orientation: Int) {
        mScrollOrientation = if (orientation == ORIENTATION_HORIZONTAL) 0 else 1
        mTransDistance = 0f
        if (mScrollBitmap != null) {
            if (mDrawable != null && mDrawable is BitmapDrawable) {
                val bitmap = mDrawable.bitmap
                if (!bitmap.isRecycled) {
                    setScrollBitmap(bitmap)
                    return
                }
            }
            setScrollBitmap(mScrollBitmap!!)
        }
    }

    fun setRadius(radius: Int) {
        for (i in mRCHelper.radii.indices) {
            mRCHelper.radii[i] = radius.toFloat()
        }
        invalidate()
    }

    fun setTopLeftRadius(topLeftRadius: Int) {
        mRCHelper.radii[0] = topLeftRadius.toFloat()
        mRCHelper.radii[1] = topLeftRadius.toFloat()
        invalidate()
    }

    fun setTopRightRadius(topRightRadius: Int) {
        mRCHelper.radii[2] = topRightRadius.toFloat()
        mRCHelper.radii[3] = topRightRadius.toFloat()
        invalidate()
    }

    fun setBottomLeftRadius(bottomLeftRadius: Int) {
        mRCHelper.radii[6] = bottomLeftRadius.toFloat()
        mRCHelper.radii[7] = bottomLeftRadius.toFloat()
        invalidate()
    }

    fun setBottomRightRadius(bottomRightRadius: Int) {
        mRCHelper.radii[4] = bottomRightRadius.toFloat()
        mRCHelper.radii[5] = bottomRightRadius.toFloat()
        invalidate()
    }

}

class RoundCornerHelper {
    var radii = FloatArray(8) // top-left, top-right, bottom-right, bottom-left
    var mClipPath = Path()  // 剪裁区域路径
    var mPaint = Paint() // 画笔
    var mAreaRegion = Region()// 内容区域
    var mLayer = RectF() // 画布图层大小

    fun initAttrs(context: Context, attrs: AttributeSet?) {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.RCAttrs)
        val roundCorner = ta.getDimensionPixelSize(R.styleable.RCAttrs_round_corner, 0)
        val roundCornerTopLeft = ta.getDimensionPixelSize(
            R.styleable.RCAttrs_round_corner_top_left, roundCorner
        )
        val roundCornerTopRight = ta.getDimensionPixelSize(
            R.styleable.RCAttrs_round_corner_top_right, roundCorner
        )
        val roundCornerBottomLeft = ta.getDimensionPixelSize(
            R.styleable.RCAttrs_round_corner_bottom_left, roundCorner
        )
        val roundCornerBottomRight = ta.getDimensionPixelSize(
            R.styleable.RCAttrs_round_corner_bottom_right, roundCorner
        )
        ta.recycle()
        radii[0] = roundCornerTopLeft.toFloat()
        radii[1] = roundCornerTopLeft.toFloat()
        radii[2] = roundCornerTopRight.toFloat()
        radii[3] = roundCornerTopRight.toFloat()
        radii[4] = roundCornerBottomRight.toFloat()
        radii[5] = roundCornerBottomRight.toFloat()
        radii[6] = roundCornerBottomLeft.toFloat()
        radii[7] = roundCornerBottomLeft.toFloat()

        mPaint.color = Color.WHITE
        mPaint.isAntiAlias = true
    }

    fun onSizeChanged(view: View, w: Int, h: Int) {
        mLayer[0f, 0f, w.toFloat()] = h.toFloat()
        refreshRegion(view)
    }

    private fun refreshRegion(view: View) {
        val w = mLayer.width().toInt()
        val h = mLayer.height().toInt()
        val areas = RectF()
        areas.left = view.paddingLeft.toFloat()
        areas.top = view.paddingTop.toFloat()
        areas.right = (w - view.paddingRight).toFloat()
        areas.bottom = (h - view.paddingBottom).toFloat()
        mClipPath.reset()
        mClipPath.addRoundRect(areas, radii, Path.Direction.CW)
        val clip = Region(
            areas.left.toInt(), areas.top.toInt(),
            areas.right.toInt(), areas.bottom.toInt()
        )
        mAreaRegion.setPath(mClipPath, clip)
    }

    fun onClipDraw(canvas: Canvas) {
        mPaint.color = Color.WHITE
        mPaint.style = Paint.Style.FILL
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
            mPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            canvas.drawPath(mClipPath, mPaint)
        } else {
            mPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            val path = Path()
            path.addRect(0f, 0f, mLayer.width(), mLayer.height(), Path.Direction.CW)
            path.op(mClipPath, Path.Op.DIFFERENCE)
            canvas.drawPath(path, mPaint)
        }
    }
}