package moe.chenxy.huaweipods.utils.miuiStrongToast.data

import android.app.BroadcastOptions
import android.content.Context
import android.content.Intent

/** 跨包窄广播必须显式共享发送方身份，接收端才能用 sentFromPackage fail-closed。 */
internal fun Context.sendIdentitySharingBroadcast(intent: Intent) {
    sendBroadcast(
        intent,
        null,
        BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle(),
    )
}
