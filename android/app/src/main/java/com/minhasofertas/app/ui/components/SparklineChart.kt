package com.minhasofertas.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SparklineChart(
    prices: List<Double>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF2E7D32),
    width: Dp = 46.dp,
    height: Dp = 20.dp
) {
    if (prices.size < 2) return

    val minPrice = remember(prices) { prices.minOrNull() ?: 0.0 }
    val maxPrice = remember(prices) { prices.maxOrNull() ?: 1.0 }
    val priceRange = remember(minPrice, maxPrice) {
        val diff = maxPrice - minPrice
        if (diff <= 0.001) 1.0 else diff
    }

    Canvas(modifier = modifier.width(width).height(height)) {
        val w = size.width
        val h = size.height
        val stepX = w / (prices.size - 1)

        val path = Path()
        prices.forEachIndexed { index, price ->
            val normY = ((price - minPrice) / priceRange).toFloat()
            // Invertido: preço menor fica no rodapé (maior Y)
            val y = h - (normY * (h - 6f)) - 3f
            val x = index * stepX

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx())
        )

        // Ponto final no preço mais recente
        val lastNormY = ((prices.last() - minPrice) / priceRange).toFloat()
        val lastY = h - (lastNormY * (h - 6f)) - 3f
        drawCircle(
            color = lineColor,
            radius = 3.dp.toPx(),
            center = Offset(w, lastY)
        )
    }
}
