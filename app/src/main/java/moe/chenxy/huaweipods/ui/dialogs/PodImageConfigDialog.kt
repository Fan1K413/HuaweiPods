package moe.chenxy.huaweipods.ui.dialogs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.config.EarphonePref
import moe.chenxy.huaweipods.config.PodImagePrefs
import moe.chenxy.huaweipods.config.PodImageResource
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.smartaudio.OfficialImageCatalogPolicy
import moe.chenxy.huaweipods.smartaudio.OfficialSmartAudioResourceOption
import moe.chenxy.huaweipods.smartaudio.SmartAudioImageCache
import moe.chenxy.huaweipods.smartaudio.SmartAudioResourceIdentity
import moe.chenxy.huaweipods.smartaudio.SmartAudioResourceIdentityPolicy
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun PodImageConfigDialog(
    show: Boolean,
    earphones: List<EarphonePref>,
    currentAddress: String,
    currentName: String,
    deviceRoute: HuaweiDeviceRoute,
    autoOpenOfficialOptions: Boolean,
    onDismissRequest: () -> Unit,
    onSave: (String, String, Map<PodImageResource, Uri?>, Set<PodImageResource>) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember(context) {
        context.getSharedPreferences(ConfigManager.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }
    val target = earphones.firstOrNull { it.address.equals(currentAddress, ignoreCase = true) }
        ?: EarphonePref(address = currentAddress, name = currentName)
    var selectedResource by remember(show) { mutableStateOf(PodImageResource.BOX) }
    var selectedImages by remember(show, target.address) { mutableStateOf<Map<PodImageResource, Uri?>>(emptyMap()) }
    var clearedImages by remember(show, target.address) { mutableStateOf<Set<PodImageResource>>(emptySet()) }
    var officialOptions by remember(show, target.address) {
        mutableStateOf<List<OfficialSmartAudioResourceOption>>(emptyList())
    }
    var showOfficialOptions by remember(show, target.address) { mutableStateOf(false) }
    var officialBusy by remember(show, target.address) { mutableStateOf(false) }
    var officialLoadFailed by remember(show, target.address) { mutableStateOf(false) }
    var requestedOfficialIdentity by remember(show, target.address) {
        mutableStateOf<SmartAudioResourceIdentity?>(null)
    }
    val persistedOfficialIdentity = remember(
        show,
        target.address,
        target.cloudModelId,
        target.cloudSubModelId,
    ) {
        val latest = PodImagePrefs.latestCloudIdentities(prefs).firstOrNull {
            it.address.equals(target.address, ignoreCase = true)
        }
        SmartAudioResourceIdentityPolicy.normalize(
            address = latest?.address,
            modelId = latest?.modelId,
            subModelId = latest?.subModelId,
        ) ?: SmartAudioResourceIdentityPolicy.normalize(
            address = target.address,
            modelId = target.cloudModelId,
            subModelId = target.cloudSubModelId,
        )
    }
    val currentOfficialIdentity = requestedOfficialIdentity ?: persistedOfficialIdentity
    val catalogModelId = currentOfficialIdentity?.modelId
        ?: OfficialImageCatalogPolicy.modelIdForRoute(deviceRoute)
    val requestStartedMessage = stringResource(R.string.official_image_request_started)
    val requestFailedMessage = stringResource(R.string.official_image_request_failed)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImages = selectedImages + (selectedResource to uri)
            clearedImages = clearedImages - selectedResource
        }
    }

    fun loadOfficialOptions() {
        val modelId = catalogModelId ?: return
        if (officialBusy || target.address.isBlank()) return
        officialBusy = true
        officialLoadFailed = false
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { SmartAudioImageCache.loadOfficialOptions(modelId) }
            }
            officialBusy = false
            officialOptions = result.getOrDefault(emptyList())
            officialLoadFailed = result.isFailure || officialOptions.isEmpty()
            showOfficialOptions = !officialLoadFailed
        }
    }

    fun requestOfficialOption(option: OfficialSmartAudioResourceOption) {
        if (officialBusy) return
        val identity = SmartAudioResourceIdentityPolicy.normalize(
            address = target.address,
            modelId = option.modelId,
            subModelId = option.subModelId,
        ) ?: return
        officialBusy = true
        coroutineScope.launch {
            val accepted = withContext(Dispatchers.IO) {
                SmartAudioImageCache.request(context, identity)
            }
            officialBusy = false
            if (accepted) {
                requestedOfficialIdentity = identity
                showOfficialOptions = false
            }
            Toast.makeText(
                context,
                if (accepted) requestStartedMessage else requestFailedMessage,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    LaunchedEffect(show, autoOpenOfficialOptions, target.address, catalogModelId) {
        if (show && autoOpenOfficialOptions) {
            loadOfficialOptions()
        }
    }

    OverlayDialog(
        title = stringResource(R.string.custom_pod_images),
        summary = target.name.ifBlank { target.address },
        show = show,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (autoOpenOfficialOptions && deviceRoute == HuaweiDeviceRoute.HUAWEI_FREEBUDS3) {
                Text(
                    text = stringResource(R.string.official_image_legacy_confirmation_hint),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            OfficialImageResourceRow(
                summary = when {
                    officialBusy -> stringResource(R.string.official_image_loading)
                    officialLoadFailed -> stringResource(R.string.official_image_load_failed)
                    currentOfficialIdentity != null -> stringResource(
                        R.string.official_image_identity,
                        currentOfficialIdentity.modelId,
                        currentOfficialIdentity.subModelId,
                    )
                    catalogModelId != null -> stringResource(R.string.official_image_hint)
                    else -> stringResource(R.string.official_image_unknown_model)
                },
                enabled = catalogModelId != null && target.address.isNotBlank() && !officialBusy,
                loading = officialBusy,
                onClick = ::loadOfficialOptions,
            )
            PodImageResource.entries.forEach { resource ->
                val selectedUri = selectedImages[resource]
                val manualPath = target.imagePath(resource)
                    .takeUnless { resource in clearedImages }
                val cloudPath = target.cloudImagePath(resource)
                PodImageResourceRow(
                    resource = resource,
                    selectedUri = selectedUri,
                    savedPath = manualPath ?: cloudPath,
                    title = stringResource(resource.titleRes()),
                    summary = when {
                        selectedUri != null || manualPath != null ->
                            stringResource(R.string.custom_image_selected)
                        cloudPath != null -> stringResource(R.string.custom_image_official)
                        else -> stringResource(R.string.custom_image_default)
                    },
                    clearable = selectedUri != null || manualPath != null,
                    onClick = {
                        selectedResource = resource
                        launcher.launch("image/*")
                    },
                    onClear = {
                        selectedImages = selectedImages - resource
                        clearedImages = clearedImages + resource
                    },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = stringResource(R.string.cancel),
                onClick = onDismissRequest,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(4.dp))
            TextButton(
                text = stringResource(R.string.save),
                onClick = { onSave(target.address, target.name, selectedImages, clearedImages) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }

    WindowDialog(
        title = stringResource(R.string.official_image_select_color),
        show = show && showOfficialOptions,
        onDismissRequest = { if (!officialBusy) showOfficialOptions = false },
    ) {
        Text(
            text = stringResource(
                if (deviceRoute == HuaweiDeviceRoute.HUAWEI_FREEBUDS3) {
                    R.string.official_image_legacy_confirmation_hint
                } else {
                    R.string.official_image_select_color_hint
                },
            ),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )
        officialOptions.forEach { option ->
            val isCurrent = currentOfficialIdentity?.let {
                it.modelId == option.modelId && it.subModelId == option.subModelId
            } == true
            val currentSuffix = if (isCurrent) {
                stringResource(R.string.official_image_current_suffix)
            } else {
                ""
            }
            TextButton(
                text = "${option.resourceDesc} · ${option.subModelId}$currentSuffix",
                onClick = { requestOfficialOption(option) },
                enabled = !officialBusy,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun OfficialImageResourceRow(
    summary: String,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(24.dp),
            imageVector = MiuixIcons.Refresh,
            contentDescription = null,
            tint = if (enabled) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.official_image_get),
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.headline1,
            )
            Text(
                text = summary,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (loading) {
            Spacer(Modifier.width(12.dp))
            InfiniteProgressIndicator(
                color = MiuixTheme.colorScheme.primary,
                size = 24.dp,
            )
        }
    }
}

@Composable
private fun PodImageResourceRow(
    resource: PodImageResource,
    selectedUri: Uri?,
    savedPath: String?,
    title: String,
    summary: String,
    clearable: Boolean,
    onClick: () -> Unit,
    onClear: () -> Unit,
) {
    val previewPainter = rememberPodImagePreviewPainter(resource, selectedUri, savedPath)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = previewPainter,
            contentDescription = title,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.headline1,
            )
            Text(
                text = summary,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (clearable) {
            Spacer(Modifier.width(12.dp))
            IconButton(
                modifier = Modifier.size(32.dp),
                onClick = onClear,
            ) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = MiuixIcons.Close,
                    contentDescription = stringResource(R.string.custom_image_restore_default),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun rememberPodImagePreviewPainter(
    resource: PodImageResource,
    selectedUri: Uri?,
    savedPath: String?,
): Painter {
    val context = LocalContext.current
    val fallback = painterResource(resource.defaultImageRes())
    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        context,
        selectedUri,
        savedPath,
    ) {
        value = withContext(Dispatchers.IO) {
            selectedUri?.let { uri -> decodePreviewBitmap(context, uri) }
                ?: savedPath?.let(::decodePreviewBitmap)
        }
    }
    return bitmap?.let { preview ->
        remember(preview) { BitmapPainter(preview.asImageBitmap()) }
    } ?: fallback
}

private fun decodePreviewBitmap(context: android.content.Context, uri: Uri): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri).use { input ->
        input ?: return@runCatching null
        BitmapFactory.decodeStream(input, null, bounds)
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = previewSampleSize(bounds.outWidth, bounds.outHeight)
    }
    context.contentResolver.openInputStream(uri).use { input ->
        input?.let { BitmapFactory.decodeStream(it, null, options) }
    }
}.getOrNull()

private fun decodePreviewBitmap(path: String): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply {
            inSampleSize = previewSampleSize(bounds.outWidth, bounds.outHeight)
        },
    )
}.getOrNull()

private fun previewSampleSize(width: Int, height: Int): Int {
    var sample = 1
    while (width / sample > PREVIEW_MAX_PIXELS || height / sample > PREVIEW_MAX_PIXELS) {
        sample *= 2
    }
    return sample
}

private const val PREVIEW_MAX_PIXELS = 256

private fun PodImageResource.titleRes(): Int = when (this) {
    PodImageResource.BOX -> R.string.custom_image_box
    PodImageResource.LEFT -> R.string.custom_image_left
    PodImageResource.RIGHT -> R.string.custom_image_right
}

private fun PodImageResource.defaultImageRes(): Int = when (this) {
    PodImageResource.BOX -> R.drawable.img_box
    PodImageResource.LEFT -> R.drawable.img_left
    PodImageResource.RIGHT -> R.drawable.img_right
}
