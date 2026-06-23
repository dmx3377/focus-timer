package dmx.focus_timer.wearos.presentation

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class TimerTileService : TileService() {
    private val RESOURCES_VERSION = "1"

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(tileLayout(this, requestParams.deviceConfiguration))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .build()
        )
    }

    private fun tileLayout(
        context: Context,
        deviceParameters: DeviceParameters
    ): LayoutElementBuilders.LayoutElement {
        return PrimaryLayout.Builder(deviceParameters)
            .setContent(
                Column.Builder()
                    .addContent(
                        Text.Builder(context, "Quick Timer")
                            .setTypography(Typography.TYPOGRAPHY_TITLE3)
                            .setColor(androidx.wear.protolayout.ColorBuilders.argb(0xFFFFFFFF.toInt()))
                            .build()
                    )
                    .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(12f)).build())
                    .addContent(
                        Row.Builder()
                            .addContent(timerButton(context, "5m", 5, deviceParameters))
                            .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(8f)).build())
                            .addContent(timerButton(context, "10m", 10, deviceParameters))
                            .build()
                    )
                    .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).build())
                    .addContent(
                        Row.Builder()
                            .addContent(timerButton(context, "25m", 25, deviceParameters))
                            .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(8f)).build())
                            .addContent(timerButton(context, "50m", 50, deviceParameters))
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun timerButton(context: Context, label: String, minutes: Int, deviceParameters: DeviceParameters): LayoutElementBuilders.LayoutElement {
        val launchAction = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(context.packageName)
                    .setClassName("dmx.focus_timer.wearos.presentation.WearOSActivity")
                    .addKeyToExtraMapping(
                        "QUICK_START_MINUTES",
                        ActionBuilders.AndroidStringExtra.Builder().setValue(minutes.toString()).build()
                    )
                    .build()
            )
            .build()

        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId("timer_action_$minutes")
            .setOnClick(launchAction)
            .build()

        return CompactChip.Builder(context, label, clickable, deviceParameters)
            .build()
    }
}
