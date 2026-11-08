package com.example.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.widget.FrameLayout

enum class ConnectorSide {
    LEFT,   // Connector arrow on Left edge, pointing left towards puck
    RIGHT,  // Connector arrow on Right edge, pointing right towards puck
    TOP,    // Connector arrow on Top edge, pointing up towards puck
    BOTTOM  // Connector arrow on Bottom edge, pointing down towards puck
}

/**
 * Custom attached layout that draws a seamless, unified dark espresso panel
 * with a detached directional pointer arrow indicating connection to the puck.
 *
 * Guarantees zero content overflow by strictly clipping all internal children
 * to the card's rounded rectangle bounds, and removes any visible scrollbars.
 */
class AttachedMenuLayout(context: Context) : FrameLayout(context) {

    var connectorSide: ConnectorSide = ConnectorSide.RIGHT
        private set
    var cardWidth: Int = 0
        private set
    var cardHeight: Int = 0
        private set
    var arrowLength: Int = 0
        private set
    var connectorOffset: Float = 0f
        private set

    var onOutsideTouch: (() -> Unit)? = null

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private fun dp(v: Int) = (v * density).toInt()

    private val cornerRadius = dp(24f)
    private val arrowBaseWidth = dp(22f)
    private val strokeWidth = dp(1.5f)

    private val unifiedPath = Path()
    private val cardClipPath = Path()
    private val cardRect = RectF()

    var cardLeft = 0f
        private set
    var cardTop = 0f
        private set
    var cardRight = 0f
        private set
    var cardBottom = 0f
        private set

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xF61E1612.toInt() // Dark translucent espresso-brown (96% opacity)
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.strokeWidth = this@AttachedMenuLayout.strokeWidth
        color = 0xFF46352C.toInt() // Thin warm-gray cocoa border
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x40000000 // Soft natural shadow
    }

    init {
        setWillNotDraw(false)
        clipToPadding = false
        clipChildren = true
    }

    fun updateGeometry(
        side: ConnectorSide,
        cardW: Int,
        cardH: Int,
        arrowLen: Int,
        offset: Float
    ) {
        val changed = (connectorSide != side || cardWidth != cardW ||
                cardHeight != cardH || arrowLength != arrowLen ||
                Math.abs(connectorOffset - offset) > 0.5f)

        connectorSide = side
        cardWidth = cardW
        cardHeight = cardH
        arrowLength = arrowLen
        connectorOffset = offset

        if (changed) {
            rebuildPaths()
            requestLayout()
            invalidate()
        }
    }

    private fun rebuildPaths() {
        unifiedPath.reset()
        cardClipPath.reset()
        if (cardWidth <= 0 || cardHeight <= 0) return

        val halfStroke = strokeWidth / 2f
        val aLen = arrowLength.toFloat()

        when (connectorSide) {
            ConnectorSide.TOP -> {
                cardLeft = halfStroke
                cardTop = aLen + halfStroke
                cardRight = cardLeft + cardWidth - strokeWidth
                cardBottom = cardTop + cardHeight - strokeWidth
            }
            ConnectorSide.BOTTOM -> {
                cardLeft = halfStroke
                cardTop = halfStroke
                cardRight = cardLeft + cardWidth - strokeWidth
                cardBottom = cardTop + cardHeight - strokeWidth
            }
            ConnectorSide.LEFT -> {
                cardLeft = aLen + halfStroke
                cardTop = halfStroke
                cardRight = cardLeft + cardWidth - strokeWidth
                cardBottom = cardTop + cardHeight - strokeWidth
            }
            ConnectorSide.RIGHT -> {
                cardLeft = halfStroke
                cardTop = halfStroke
                cardRight = cardLeft + cardWidth - strokeWidth
                cardBottom = cardTop + cardHeight - strokeWidth
            }
        }

        // Clip path for internal content: strictly the interior of the rounded card
        cardRect.set(cardLeft, cardTop, cardRight, cardBottom)
        cardClipPath.addRoundRect(cardRect, cornerRadius, cornerRadius, Path.Direction.CW)

        val r = cornerRadius
        val halfBase = arrowBaseWidth / 2f

        when (connectorSide) {
            ConnectorSide.TOP -> {
                val clampedOffsetX = connectorOffset.coerceIn(
                    cardLeft + r + halfBase,
                    cardRight - r - halfBase
                )
                val leftX = clampedOffsetX - halfBase
                val rightX = clampedOffsetX + halfBase
                val tipX = clampedOffsetX
                val tipY = cardTop - aLen

                unifiedPath.moveTo(cardLeft + r, cardTop)
                // Top edge with upward-pointing arrow
                unifiedPath.lineTo(leftX, cardTop)
                unifiedPath.cubicTo(
                    leftX + arrowBaseWidth * 0.25f, cardTop - aLen * 0.40f,
                    tipX - arrowBaseWidth * 0.15f, tipY + aLen * 0.10f,
                    tipX, tipY
                )
                unifiedPath.cubicTo(
                    tipX + arrowBaseWidth * 0.15f, tipY + aLen * 0.10f,
                    rightX - arrowBaseWidth * 0.25f, cardTop - aLen * 0.40f,
                    rightX, cardTop
                )
                unifiedPath.lineTo(cardRight - r, cardTop)
                unifiedPath.quadTo(cardRight, cardTop, cardRight, cardTop + r)
                unifiedPath.lineTo(cardRight, cardBottom - r)
                unifiedPath.quadTo(cardRight, cardBottom, cardRight - r, cardBottom)
                unifiedPath.lineTo(cardLeft + r, cardBottom)
                unifiedPath.quadTo(cardLeft, cardBottom, cardLeft, cardBottom - r)
                unifiedPath.lineTo(cardLeft, cardTop + r)
                unifiedPath.quadTo(cardLeft, cardTop, cardLeft + r, cardTop)
            }

            ConnectorSide.BOTTOM -> {
                val clampedOffsetX = connectorOffset.coerceIn(
                    cardLeft + r + halfBase,
                    cardRight - r - halfBase
                )
                val leftX = clampedOffsetX - halfBase
                val rightX = clampedOffsetX + halfBase
                val tipX = clampedOffsetX
                val tipY = cardBottom + aLen

                unifiedPath.moveTo(cardLeft + r, cardTop)
                unifiedPath.lineTo(cardRight - r, cardTop)
                unifiedPath.quadTo(cardRight, cardTop, cardRight, cardTop + r)
                unifiedPath.lineTo(cardRight, cardBottom - r)
                unifiedPath.quadTo(cardRight, cardBottom, cardRight - r, cardBottom)
                // Bottom edge with downward-pointing arrow
                unifiedPath.lineTo(rightX, cardBottom)
                unifiedPath.cubicTo(
                    rightX - arrowBaseWidth * 0.25f, cardBottom + aLen * 0.40f,
                    tipX + arrowBaseWidth * 0.15f, tipY - aLen * 0.10f,
                    tipX, tipY
                )
                unifiedPath.cubicTo(
                    tipX - arrowBaseWidth * 0.15f, tipY - aLen * 0.10f,
                    leftX + arrowBaseWidth * 0.25f, cardBottom + aLen * 0.40f,
                    leftX, cardBottom
                )
                unifiedPath.lineTo(cardLeft + r, cardBottom)
                unifiedPath.quadTo(cardLeft, cardBottom, cardLeft, cardBottom - r)
                unifiedPath.lineTo(cardLeft, cardTop + r)
                unifiedPath.quadTo(cardLeft, cardTop, cardLeft + r, cardTop)
            }

            ConnectorSide.RIGHT -> {
                val clampedOffsetY = connectorOffset.coerceIn(
                    cardTop + r + halfBase,
                    cardBottom - r - halfBase
                )
                val topY = clampedOffsetY - halfBase
                val bottomY = clampedOffsetY + halfBase
                val tipX = cardRight + aLen
                val tipY = clampedOffsetY

                unifiedPath.moveTo(cardLeft + r, cardTop)
                unifiedPath.lineTo(cardRight - r, cardTop)
                unifiedPath.quadTo(cardRight, cardTop, cardRight, cardTop + r)
                // Right edge with rightward-pointing arrow
                unifiedPath.lineTo(cardRight, topY)
                unifiedPath.cubicTo(
                    cardRight + aLen * 0.40f, topY + halfBase * 0.25f,
                    tipX - aLen * 0.10f, tipY - halfBase * 0.20f,
                    tipX, tipY
                )
                unifiedPath.cubicTo(
                    tipX - aLen * 0.10f, tipY + halfBase * 0.20f,
                    cardRight + aLen * 0.40f, bottomY - halfBase * 0.25f,
                    cardRight, bottomY
                )
                unifiedPath.lineTo(cardRight, cardBottom - r)
                unifiedPath.quadTo(cardRight, cardBottom, cardRight - r, cardBottom)
                unifiedPath.lineTo(cardLeft + r, cardBottom)
                unifiedPath.quadTo(cardLeft, cardBottom, cardLeft, cardBottom - r)
                unifiedPath.lineTo(cardLeft, cardTop + r)
                unifiedPath.quadTo(cardLeft, cardTop, cardLeft + r, cardTop)
            }

            ConnectorSide.LEFT -> {
                val clampedOffsetY = connectorOffset.coerceIn(
                    cardTop + r + halfBase,
                    cardBottom - r - halfBase
                )
                val topY = clampedOffsetY - halfBase
                val bottomY = clampedOffsetY + halfBase
                val tipX = cardLeft - aLen
                val tipY = clampedOffsetY

                unifiedPath.moveTo(cardLeft + r, cardTop)
                unifiedPath.lineTo(cardRight - r, cardTop)
                unifiedPath.quadTo(cardRight, cardTop, cardRight, cardTop + r)
                unifiedPath.lineTo(cardRight, cardBottom - r)
                unifiedPath.quadTo(cardRight, cardBottom, cardRight - r, cardBottom)
                unifiedPath.lineTo(cardLeft + r, cardBottom)
                unifiedPath.quadTo(cardLeft, cardBottom, cardLeft, cardBottom - r)
                // Left edge with leftward-pointing arrow
                unifiedPath.lineTo(cardLeft, bottomY)
                unifiedPath.cubicTo(
                    cardLeft - aLen * 0.40f, bottomY - halfBase * 0.25f,
                    tipX + aLen * 0.10f, tipY + halfBase * 0.20f,
                    tipX, tipY
                )
                unifiedPath.cubicTo(
                    tipX + aLen * 0.10f, tipY - halfBase * 0.20f,
                    cardLeft - aLen * 0.40f, topY + halfBase * 0.25f,
                    cardLeft, topY
                )
                unifiedPath.lineTo(cardLeft, cardTop + r)
                unifiedPath.quadTo(cardLeft, cardTop, cardLeft + r, cardTop)
            }
        }
        unifiedPath.close()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (childCount > 0) {
            val child = getChildAt(0)
            val childWidth = (cardWidth - strokeWidth.toInt()).coerceAtLeast(0)
            val childHeight = (cardHeight - strokeWidth.toInt()).coerceAtLeast(0)
            child.measure(
                MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)
            )
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (childCount > 0) {
            val child = getChildAt(0)
            child.layout(
                cardLeft.toInt(),
                cardTop.toInt(),
                cardRight.toInt(),
                cardBottom.toInt()
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (unifiedPath.isEmpty) return

        // Soft outer ambient shadow
        canvas.save()
        canvas.translate(0f, dp(2f))
        canvas.drawPath(unifiedPath, shadowPaint)
        canvas.restore()

        // Seamless unified fill (panel + connector arrow)
        canvas.drawPath(unifiedPath, fillPaint)

        // Seamless unified stroke border
        canvas.drawPath(unifiedPath, strokePaint)
    }

    override fun dispatchDraw(canvas: Canvas) {
        // Enforce strict content clipping to the card's rounded boundary.
        // No item, icon, text, or switch can ever overflow or appear outside the container!
        if (!cardClipPath.isEmpty) {
            canvas.save()
            canvas.clipPath(cardClipPath)
            super.dispatchDraw(canvas)
            canvas.restore()
        } else {
            super.dispatchDraw(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_OUTSIDE) {
            onOutsideTouch?.invoke()
            return true
        }
        return super.onTouchEvent(event)
    }
}
