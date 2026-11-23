package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AccentColorPreset
import com.example.model.AiProcessingMode
import com.example.model.AspectRatioMode
import com.example.model.CameraMode
import com.example.model.GridType
import com.example.model.LivePhotoDuration
import com.example.model.PhotoFormat
import com.example.model.PhotoResolutionTier
import com.example.model.StabilizationMode
import com.example.model.VideoBitrateTier
import com.example.model.VideoCodecOption
import com.example.model.VideoResolutionPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.hungCamDataStore: DataStore<Preferences> by preferencesDataStore(name = "hung_cam_settings")

data class HungCamSettings(
    // Camera settings
    val defaultLensFacingBack: Boolean = true,
    val defaultCameraMode: CameraMode = CameraMode.PHOTO,
    val photoResolutionTier: PhotoResolutionTier = PhotoResolutionTier.STANDARD,
    val photoFormat: PhotoFormat = PhotoFormat.JPEG,
    val hdrEnabled: Boolean = true,
    val videoResolution: VideoResolutionPreset = VideoResolutionPreset.RES_1080P,
    val videoFps: Int = 30,
    val videoBitrateTier: VideoBitrateTier = VideoBitrateTier.HIGH,
    val videoCodec: VideoCodecOption = VideoCodecOption.H264,
    val stabilizationMode: StabilizationMode = StabilizationMode.HYBRID,
    val aspectRatio: AspectRatioMode = AspectRatioMode.RATIO_4_3,
    val gridType: GridType = GridType.RULE_OF_THIRDS,
    val electronicLevelEnabled: Boolean = true,
    val volumeShutterEnabled: Boolean = true,
    val naturalColorBoostEnabled: Boolean = true,
    val cameraWatermarkEnabled: Boolean = false,
    // Live Photo settings
    val livePhotoDefaultDuration: LivePhotoDuration = LivePhotoDuration.THREE_SEC,
    val livePhotoSoundEnabled: Boolean = true,
    val livePhotoAutoLoopViewer: Boolean = true,
    val livePhotoHighBitrate: Boolean = true,
    // AI settings
    val aiDefaultMode: AiProcessingMode = AiProcessingMode.BALANCED,
    val aiAutoDenoise: Boolean = true,
    val aiAutoSharpen: Boolean = true,
    val aiAutoStabilize: Boolean = true,
    val aiLocalProcessingOnly: Boolean = true,
    // Interface settings
    val darkModeEnabled: Boolean = true,
    val accentColorPreset: AccentColorPreset = AccentColorPreset.LEICA_AMBER,
    val hapticFeedbackEnabled: Boolean = true,
    val animationsEnabled: Boolean = true,
    // Privacy settings
    val locationMetadataEnabled: Boolean = false
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val DEFAULT_BACK = booleanPreferencesKey("default_back")
        val DEFAULT_MODE = stringPreferencesKey("default_mode")
        val PHOTO_RES_TIER = stringPreferencesKey("photo_res_tier")
        val PHOTO_FORMAT = stringPreferencesKey("photo_format")
        val HDR_ENABLED = booleanPreferencesKey("hdr_enabled")
        val VIDEO_RES = stringPreferencesKey("video_res")
        val VIDEO_FPS = stringPreferencesKey("video_fps")
        val VIDEO_BITRATE = stringPreferencesKey("video_bitrate")
        val VIDEO_CODEC = stringPreferencesKey("video_codec")
        val STAB_MODE = stringPreferencesKey("stab_mode")
        val ASPECT_RATIO = stringPreferencesKey("aspect_ratio")
        val GRID_TYPE = stringPreferencesKey("grid_type")
        val LEVEL_ENABLED = booleanPreferencesKey("level_enabled")
        val VOLUME_SHUTTER = booleanPreferencesKey("volume_shutter")
        val NATURAL_COLOR_BOOST = booleanPreferencesKey("natural_color_boost")
        val CAMERA_WATERMARK = booleanPreferencesKey("camera_watermark")
        val LIVE_DURATION = stringPreferencesKey("live_duration")
        val LIVE_SOUND = booleanPreferencesKey("live_sound")
        val LIVE_LOOP = booleanPreferencesKey("live_loop")
        val LIVE_HIGH_BITRATE = booleanPreferencesKey("live_high_bitrate")
        val AI_MODE = stringPreferencesKey("ai_mode")
        val AI_DENOISE = booleanPreferencesKey("ai_denoise")
        val AI_SHARPEN = booleanPreferencesKey("ai_sharpen")
        val AI_STABILIZE = booleanPreferencesKey("ai_stabilize")
        val AI_LOCAL_ONLY = booleanPreferencesKey("ai_local_only")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val ACCENT_PRESET = stringPreferencesKey("accent_preset")
        val HAPTICS = booleanPreferencesKey("haptics")
        val ANIMATIONS = booleanPreferencesKey("animations")
        val LOCATION_META = booleanPreferencesKey("location_meta")
    }

    val settingsFlow: Flow<HungCamSettings> = context.hungCamDataStore.data.map { prefs ->
        HungCamSettings(
            defaultLensFacingBack = prefs[Keys.DEFAULT_BACK] ?: true,
            defaultCameraMode = enumValueOrDefault(prefs[Keys.DEFAULT_MODE], CameraMode.PHOTO),
            photoResolutionTier = enumValueOrDefault(prefs[Keys.PHOTO_RES_TIER], PhotoResolutionTier.STANDARD),
            photoFormat = enumValueOrDefault(prefs[Keys.PHOTO_FORMAT], PhotoFormat.JPEG),
            hdrEnabled = prefs[Keys.HDR_ENABLED] ?: true,
            videoResolution = enumValueOrDefault(prefs[Keys.VIDEO_RES], VideoResolutionPreset.RES_1080P),
            videoFps = prefs[Keys.VIDEO_FPS]?.toIntOrNull() ?: 30,
            videoBitrateTier = enumValueOrDefault(prefs[Keys.VIDEO_BITRATE], VideoBitrateTier.HIGH),
            videoCodec = enumValueOrDefault(prefs[Keys.VIDEO_CODEC], VideoCodecOption.H264),
            stabilizationMode = enumValueOrDefault(prefs[Keys.STAB_MODE], StabilizationMode.HYBRID),
            aspectRatio = enumValueOrDefault(prefs[Keys.ASPECT_RATIO], AspectRatioMode.RATIO_4_3),
            gridType = enumValueOrDefault(prefs[Keys.GRID_TYPE], GridType.RULE_OF_THIRDS),
            electronicLevelEnabled = prefs[Keys.LEVEL_ENABLED] ?: true,
            volumeShutterEnabled = prefs[Keys.VOLUME_SHUTTER] ?: true,
            naturalColorBoostEnabled = prefs[Keys.NATURAL_COLOR_BOOST] ?: true,
            cameraWatermarkEnabled = prefs[Keys.CAMERA_WATERMARK] ?: false,
            livePhotoDefaultDuration = enumValueOrDefault(prefs[Keys.LIVE_DURATION], LivePhotoDuration.THREE_SEC),
            livePhotoSoundEnabled = prefs[Keys.LIVE_SOUND] ?: true,
            livePhotoAutoLoopViewer = prefs[Keys.LIVE_LOOP] ?: true,
            livePhotoHighBitrate = prefs[Keys.LIVE_HIGH_BITRATE] ?: true,
            aiDefaultMode = enumValueOrDefault(prefs[Keys.AI_MODE], AiProcessingMode.BALANCED),
            aiAutoDenoise = prefs[Keys.AI_DENOISE] ?: true,
            aiAutoSharpen = prefs[Keys.AI_SHARPEN] ?: true,
            aiAutoStabilize = prefs[Keys.AI_STABILIZE] ?: true,
            aiLocalProcessingOnly = prefs[Keys.AI_LOCAL_ONLY] ?: true,
            darkModeEnabled = prefs[Keys.DARK_MODE] ?: true,
            accentColorPreset = enumValueOrDefault(prefs[Keys.ACCENT_PRESET], AccentColorPreset.LEICA_AMBER),
            hapticFeedbackEnabled = prefs[Keys.HAPTICS] ?: true,
            animationsEnabled = prefs[Keys.ANIMATIONS] ?: true,
            locationMetadataEnabled = prefs[Keys.LOCATION_META] ?: false
        )
    }

    suspend fun update(transform: (HungCamSettings) -> HungCamSettings) {
        context.hungCamDataStore.edit { prefs ->
            val current = HungCamSettings(
                defaultLensFacingBack = prefs[Keys.DEFAULT_BACK] ?: true,
                defaultCameraMode = enumValueOrDefault(prefs[Keys.DEFAULT_MODE], CameraMode.PHOTO),
                photoResolutionTier = enumValueOrDefault(prefs[Keys.PHOTO_RES_TIER], PhotoResolutionTier.STANDARD),
                photoFormat = enumValueOrDefault(prefs[Keys.PHOTO_FORMAT], PhotoFormat.JPEG),
                hdrEnabled = prefs[Keys.HDR_ENABLED] ?: true,
                videoResolution = enumValueOrDefault(prefs[Keys.VIDEO_RES], VideoResolutionPreset.RES_1080P),
                videoFps = prefs[Keys.VIDEO_FPS]?.toIntOrNull() ?: 30,
                videoBitrateTier = enumValueOrDefault(prefs[Keys.VIDEO_BITRATE], VideoBitrateTier.HIGH),
                videoCodec = enumValueOrDefault(prefs[Keys.VIDEO_CODEC], VideoCodecOption.H264),
                stabilizationMode = enumValueOrDefault(prefs[Keys.STAB_MODE], StabilizationMode.HYBRID),
                aspectRatio = enumValueOrDefault(prefs[Keys.ASPECT_RATIO], AspectRatioMode.RATIO_4_3),
                gridType = enumValueOrDefault(prefs[Keys.GRID_TYPE], GridType.RULE_OF_THIRDS),
                electronicLevelEnabled = prefs[Keys.LEVEL_ENABLED] ?: true,
                volumeShutterEnabled = prefs[Keys.VOLUME_SHUTTER] ?: true,
                naturalColorBoostEnabled = prefs[Keys.NATURAL_COLOR_BOOST] ?: true,
                cameraWatermarkEnabled = prefs[Keys.CAMERA_WATERMARK] ?: false,
                livePhotoDefaultDuration = enumValueOrDefault(prefs[Keys.LIVE_DURATION], LivePhotoDuration.THREE_SEC),
                livePhotoSoundEnabled = prefs[Keys.LIVE_SOUND] ?: true,
                livePhotoAutoLoopViewer = prefs[Keys.LIVE_LOOP] ?: true,
                livePhotoHighBitrate = prefs[Keys.LIVE_HIGH_BITRATE] ?: true,
                aiDefaultMode = enumValueOrDefault(prefs[Keys.AI_MODE], AiProcessingMode.BALANCED),
                aiAutoDenoise = prefs[Keys.AI_DENOISE] ?: true,
                aiAutoSharpen = prefs[Keys.AI_SHARPEN] ?: true,
                aiAutoStabilize = prefs[Keys.AI_STABILIZE] ?: true,
                aiLocalProcessingOnly = prefs[Keys.AI_LOCAL_ONLY] ?: true,
                darkModeEnabled = prefs[Keys.DARK_MODE] ?: true,
                accentColorPreset = enumValueOrDefault(prefs[Keys.ACCENT_PRESET], AccentColorPreset.LEICA_AMBER),
                hapticFeedbackEnabled = prefs[Keys.HAPTICS] ?: true,
                animationsEnabled = prefs[Keys.ANIMATIONS] ?: true,
                locationMetadataEnabled = prefs[Keys.LOCATION_META] ?: false
            )
            val next = transform(current)
            prefs[Keys.DEFAULT_BACK] = next.defaultLensFacingBack
            prefs[Keys.DEFAULT_MODE] = next.defaultCameraMode.name
            prefs[Keys.PHOTO_RES_TIER] = next.photoResolutionTier.name
            prefs[Keys.PHOTO_FORMAT] = next.photoFormat.name
            prefs[Keys.HDR_ENABLED] = next.hdrEnabled
            prefs[Keys.VIDEO_RES] = next.videoResolution.name
            prefs[Keys.VIDEO_FPS] = next.videoFps.toString()
            prefs[Keys.VIDEO_BITRATE] = next.videoBitrateTier.name
            prefs[Keys.VIDEO_CODEC] = next.videoCodec.name
            prefs[Keys.STAB_MODE] = next.stabilizationMode.name
            prefs[Keys.ASPECT_RATIO] = next.aspectRatio.name
            prefs[Keys.GRID_TYPE] = next.gridType.name
            prefs[Keys.LEVEL_ENABLED] = next.electronicLevelEnabled
            prefs[Keys.VOLUME_SHUTTER] = next.volumeShutterEnabled
            prefs[Keys.NATURAL_COLOR_BOOST] = next.naturalColorBoostEnabled
            prefs[Keys.CAMERA_WATERMARK] = next.cameraWatermarkEnabled
            prefs[Keys.LIVE_DURATION] = next.livePhotoDefaultDuration.name
            prefs[Keys.LIVE_SOUND] = next.livePhotoSoundEnabled
            prefs[Keys.LIVE_LOOP] = next.livePhotoAutoLoopViewer
            prefs[Keys.LIVE_HIGH_BITRATE] = next.livePhotoHighBitrate
            prefs[Keys.AI_MODE] = next.aiDefaultMode.name
            prefs[Keys.AI_DENOISE] = next.aiAutoDenoise
            prefs[Keys.AI_SHARPEN] = next.aiAutoSharpen
            prefs[Keys.AI_STABILIZE] = next.aiAutoStabilize
            prefs[Keys.AI_LOCAL_ONLY] = next.aiLocalProcessingOnly
            prefs[Keys.DARK_MODE] = next.darkModeEnabled
            prefs[Keys.ACCENT_PRESET] = next.accentColorPreset.name
            prefs[Keys.HAPTICS] = next.hapticFeedbackEnabled
            prefs[Keys.ANIMATIONS] = next.animationsEnabled
            prefs[Keys.LOCATION_META] = next.locationMetadataEnabled
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, default: T): T {
        if (raw == null) return default
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
    }
}
