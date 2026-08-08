package moe.chenxy.huaweipods.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AppIcons {
    val Documentation: ImageVector = ImageVector.Builder(
        name = "Documentation",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.85f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(3f, 4.8f)
            curveTo(6.4f, 3.9f, 9.2f, 4.7f, 12f, 6.8f)
            curveTo(14.8f, 4.7f, 17.6f, 3.9f, 21f, 4.8f)
            verticalLineTo(19.5f)
            curveTo(17.6f, 18.6f, 14.8f, 19.4f, 12f, 21f)
            curveTo(9.2f, 19.4f, 6.4f, 18.6f, 3f, 19.5f)
            close()
            moveTo(12f, 6.8f)
            verticalLineTo(21f)
        }
    }.build()

    val Support: ImageVector = ImageVector.Builder(
        name = "Support",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.85f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(12f, 21f)
            curveTo(9.8f, 19.3f, 3.5f, 14.9f, 3.5f, 9.4f)
            curveTo(3.5f, 6.4f, 5.6f, 4.4f, 8.4f, 4.4f)
            curveTo(10f, 4.4f, 11.2f, 5.2f, 12f, 6.4f)
            curveTo(12.8f, 5.2f, 14f, 4.4f, 15.6f, 4.4f)
            curveTo(18.4f, 4.4f, 20.5f, 6.4f, 20.5f, 9.4f)
            curveTo(20.5f, 14.9f, 14.2f, 19.3f, 12f, 21f)
            close()
        }
    }.build()

    val Home: ImageVector = ImageVector.Builder(
        name = "Home",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(4f, 11f)
            lineTo(12f, 4.5f)
            lineTo(20f, 11f)
            moveTo(6.5f, 10f)
            verticalLineTo(19f)
            horizontalLineTo(10f)
            verticalLineTo(14.5f)
            horizontalLineTo(14f)
            verticalLineTo(19f)
            horizontalLineTo(17.5f)
            verticalLineTo(10f)
        }
    }.build()

    val Headphones: ImageVector = ImageVector.Builder(
        name = "Headphones",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(4f, 13f)
            curveTo(4f, 7.5f, 7.8f, 4f, 12f, 4f)
            curveTo(16.2f, 4f, 20f, 7.5f, 20f, 13f)
            moveTo(7.2f, 13f)
            curveTo(5.8f, 13f, 5f, 14f, 5f, 15.4f)
            verticalLineTo(17f)
            curveTo(5f, 18.4f, 5.9f, 19.5f, 7.2f, 19.5f)
            horizontalLineTo(8.6f)
            verticalLineTo(13f)
            horizontalLineTo(7.2f)
            moveTo(16.8f, 13f)
            curveTo(18.2f, 13f, 19f, 14f, 19f, 15.4f)
            verticalLineTo(17f)
            curveTo(19f, 18.4f, 18.1f, 19.5f, 16.8f, 19.5f)
            horizontalLineTo(15.4f)
            verticalLineTo(13f)
            horizontalLineTo(16.8f)
        }
    }.build()

    val Contacts: ImageVector = ImageVector.Builder(
        name = "Contacts",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(9f, 11f)
            curveTo(10.7f, 11f, 12f, 9.7f, 12f, 8f)
            curveTo(12f, 6.3f, 10.7f, 5f, 9f, 5f)
            curveTo(7.3f, 5f, 6f, 6.3f, 6f, 8f)
            curveTo(6f, 9.7f, 7.3f, 11f, 9f, 11f)
            moveTo(4.5f, 18.5f)
            curveTo(5.1f, 15.8f, 7f, 14f, 9f, 14f)
            curveTo(11f, 14f, 12.9f, 15.8f, 13.5f, 18.5f)
            moveTo(15f, 9f)
            horizontalLineTo(20f)
            moveTo(15f, 13f)
            horizontalLineTo(20f)
            moveTo(15f, 17f)
            horizontalLineTo(18f)
        }
    }.build()

    val RemoveContact: ImageVector = ImageVector.Builder(
        name = "RemoveContact",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(9f, 11f)
            curveTo(10.7f, 11f, 12f, 9.7f, 12f, 8f)
            curveTo(12f, 6.3f, 10.7f, 5f, 9f, 5f)
            curveTo(7.3f, 5f, 6f, 6.3f, 6f, 8f)
            curveTo(6f, 9.7f, 7.3f, 11f, 9f, 11f)
            moveTo(4.5f, 18.5f)
            curveTo(5.1f, 15.8f, 7f, 14f, 9f, 14f)
            curveTo(10.5f, 14f, 12f, 15f, 13f, 16.7f)
            moveTo(15f, 15f)
            lineTo(20f, 20f)
            moveTo(20f, 15f)
            lineTo(15f, 20f)
        }
    }.build()
}
