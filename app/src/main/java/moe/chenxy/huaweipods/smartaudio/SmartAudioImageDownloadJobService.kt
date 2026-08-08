package moe.chenxy.huaweipods.smartaudio

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

class SmartAudioImageDownloadJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val identity = SmartAudioResourceIdentityPolicy.normalize(
            address = params.extras.getString(SmartAudioImageCache.EXTRA_ADDRESS),
            modelId = params.extras.getString(SmartAudioImageCache.EXTRA_MODEL_ID),
            subModelId = params.extras.getString(SmartAudioImageCache.EXTRA_SUB_MODEL_ID),
        ) ?: return false
        if (SmartAudioImageCache.isReady(this, identity)) return false
        Log.i(TAG, "Official image job started model=${identity.modelId}/${identity.subModelId}")

        val task = Thread(
            {
                val result = runCatching {
                    SmartAudioImageCache.downloadAndInstall(applicationContext, identity)
                }.onFailure {
                    Log.w(TAG, "Official image job failed for ${identity.modelId}", it)
                }
                if (result.isSuccess) {
                    Log.i(TAG, "Official image job finished model=${identity.modelId}/${identity.subModelId}")
                }
                if (runningTasks.remove(params.jobId, Thread.currentThread())) {
                    jobFinished(params, SmartAudioImageJobPolicy.shouldRescheduleAfterFailure())
                }
            },
            "HuaweiPods-cloud-image-${params.jobId}",
        )
        runningTasks.put(params.jobId, task)?.interrupt()
        task.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        runningTasks.remove(params.jobId)?.interrupt()
        return SmartAudioImageJobPolicy.shouldRescheduleAfterFailure()
    }

    companion object {
        private const val TAG = "HuaweiPods-CloudImage"
        private val runningTasks = ConcurrentHashMap<Int, Thread>()
    }
}
