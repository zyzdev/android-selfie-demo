/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.omniguider.nmnsselfier.fragments.camera

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.*
import android.hardware.display.DisplayManager
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.util.SparseArray
import android.view.*
import android.view.animation.*
import android.widget.ImageButton
import android.widget.RadioGroup
import androidx.annotation.DrawableRes
import androidx.annotation.experimental.UseExperimental
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture.Metadata
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnRepeat
import androidx.core.animation.doOnStart
import androidx.core.content.ContextCompat
import androidx.core.util.forEach
import androidx.core.util.set
import androidx.databinding.DataBindingUtil
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.resource.bitmap.TransformationUtils.rotateImage
import com.omniguider.nmnsselfier.KEY_EVENT_ACTION
import com.omniguider.nmnsselfier.KEY_EVENT_EXTRA
import com.omniguider.nmnsselfier.MainActivity
import com.omniguider.nmnsselfier.R
import com.omniguider.nmnsselfier.databinding.FragmentCameraBinding
import com.omniguider.nmnsselfier.databinding.FrameItemBinding
import com.omniguider.nmnsselfier.fragments.photoedit.FrameSelectionStatus
import com.omniguider.nmnsselfier.fragments.PermissionsFragment
import com.omniguider.nmnsselfier.utils.*
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


/** Helper type alias used for analysis use case callbacks */
typealias LumaListener = (luma: Double) -> Unit

/**
 * Main fragment for this app. Implements all camera operations including:
 * - Viewfinder
 * - Photo taking
 * - Image analysis
 */
class CameraFragment : Fragment() {

    private val dTag = javaClass.simpleName
    private lateinit var binding: FragmentCameraBinding
    private lateinit var viewModel: CameraViewModel
    private lateinit var outputDirectory: File
    private lateinit var broadcastManager: LocalBroadcastManager

    private var displayId: Int = -1
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private lateinit var frameSelectionStatus: ArrayList<FrameSelectionStatus>

    private val displayManager by lazy {
        requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

    /** Volume down button receiver used to trigger shutter */
    private val volumeDownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(KEY_EVENT_EXTRA, KeyEvent.KEYCODE_UNKNOWN)) {
                // When the volume down button is pressed, simulate a shutter button click
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    val shutter = binding.container
                        .findViewById<ImageButton>(R.id.camera_capture_button)
                    shutter.simulateClick()
                }
            }
        }
    }

    /**
     * We need a display listener for orientation changes that do not trigger a configuration
     * change, for example if we choose to override config change in manifest or for 180-degree
     * orientation changes.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = view?.let { view ->
            if (displayId == this@CameraFragment.displayId) {
                Log.d(dTag, "Rotation changed: ${view.display.rotation}")
                imageCapture?.targetRotation = view.display.rotation
                imageAnalyzer?.targetRotation = view.display.rotation
            }
        } ?: Unit
    }

    private var countDownHandler: Handler? = null
    private var countDownThread: HandlerThread? = null
    private val countDownRunnable = Runnable { doCountDown() }
    private var countDownCnt = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_camera, container, false)
        binding.delayType = TakePhotoDelay.FIVE
        binding.isCountDown = false
        binding.showCameraController = false
        binding.frameDrawableRes = ConstantUtil.NONE_FRAME
        frameSelectionStatus = arrayListOf(
            FrameSelectionStatus(R.drawable.frame1),
            FrameSelectionStatus(R.drawable.frame2),
            FrameSelectionStatus(R.drawable.frame3),
        )
        binding.root.findViewById<RecyclerView>(R.id.frameList).apply {
            (layoutManager as LinearLayoutManager).orientation = LinearLayoutManager.HORIZONTAL
            adapter = FrameListAdapter(frameSelectionStatus)
        }
        binding.root.findViewById<RadioGroup>(R.id.delaySelector)
            .setOnCheckedChangeListener { _, checkedId ->
                when (checkedId) {
                    R.id.delayFive -> {
                        binding.delayType = TakePhotoDelay.FIVE
                    }
                    R.id.delayEight -> {
                        binding.delayType = TakePhotoDelay.EIGHT
                    }
                    R.id.delayTen -> {
                        binding.delayType = TakePhotoDelay.TEN
                    }
                }
                if (binding.isCountDown!!) startCountDown()
            }
        return binding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        broadcastManager = LocalBroadcastManager.getInstance(view.context)

        // Set up the intent filter that will receive events from our main activity
        val filter = IntentFilter().apply { addAction(KEY_EVENT_ACTION) }
        broadcastManager.registerReceiver(volumeDownReceiver, filter)

        // Every time the orientation of device changes, update rotation for use cases
        displayManager.registerDisplayListener(displayListener, null)

        // Determine the output directory
        outputDirectory = MainActivity.getOutputDirectory(requireContext())

        // Wait for the views to be properly laid out
        binding.viewFinder.post {

            // Keep track of the display in which this view is attached
            displayId = binding.viewFinder.display.displayId

            // Build UI controls
            updateCameraUi()

            // Set up the camera and its use cases
            setUpCamera()
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider.AndroidViewModelFactory(requireActivity().application).create(
            CameraViewModel::class.java
        )
    }

    /**
     * Inflate camera controls and update the UI manually upon config changes to avoid removing
     * and re-adding the view finder from the view hierarchy; this provides a seamless rotation
     * transition on devices that support it.
     *
     * NOTE: The flag is supported starting in Android 8 but there still is a small flash on the
     * screen for devices that run Android 9 or below.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Redraw the camera UI controls
        updateCameraUi()
    }

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                CameraFragmentDirections.actionCameraToPermissions()
            )
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d(dTag, "onStop")
        stopCountDown()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Shut down our background executor
        cameraExecutor.shutdown()

        // Unregister the broadcast receivers and listeners
        broadcastManager.unregisterReceiver(volumeDownReceiver)
        displayManager.unregisterDisplayListener(displayListener)
    }

    /** Initialize CameraX, and prepare to bind the camera use cases  */
    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({

            // CameraProvider
            cameraProvider = cameraProviderFuture.get()

            // Select lensFacing depending on the available cameras
            lensFacing = when {
                hasBackCamera() -> CameraSelector.LENS_FACING_BACK
                hasFrontCamera() -> CameraSelector.LENS_FACING_FRONT
                else -> throw IllegalStateException("Back and front camera are unavailable")
            }

            // Build and bind the camera use cases
            bindCameraUseCases()

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    /** Declare and bind preview, capture and analysis use cases */
    private fun bindCameraUseCases() {

        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also { binding.viewFinder.display.getRealMetrics(it) }
        Log.d(dTag, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        Log.d(dTag, "Preview aspect ratio: $screenAspectRatio")

        //val rotation = binding.viewFinder.display.rotation
        //force rotate to 0 degree
        val rotation = Surface.ROTATION_0

        binding.rotation = rotation
        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        // CameraSelector
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        // Preview
        preview = Preview.Builder()
            // We request aspect ratio but no resolution
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation
            .setTargetRotation(rotation)
            .build()

        // ImageCapture
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            // We request aspect ratio but no resolution to match preview config, but letting
            // CameraX optimize for whatever specific resolution best fits our use cases
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            .setTargetRotation(rotation)
            .build()

        // ImageAnalysis
        imageAnalyzer = ImageAnalysis.Builder()
            // We request aspect ratio but no resolution
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            .setTargetRotation(rotation)
            .build()
            // The analyzer can then be assigned to the instance
            .also {
/*                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
                        // Values returned from our analyzer are passed to the attached listener
                        // We log image analysis results here - you should do something useful
                        // instead!
                        Log.d(dTag, "Average luminosity: $luma")
                    })*/
/*                    it.setAnalyzer(cameraExecutor, MyAnalyzer { bitmap ->
                        Log.d(dTag, "get bitmap: ${bitmap.byteCount}")
                    })*/
            }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageCapture, imageAnalyzer
            )

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)

        } catch (exc: Exception) {
            Log.e(dTag, "Use case binding failed", exc)
        }
    }

    private fun unbindCamera() =
        view?.post {
            cameraProvider?.unbindAll()
        }

    /**
     *  [androidx.camera.core.ImageAnalysisConfig] requires enum value of
     *  [androidx.camera.core.AspectRatio]. Currently it has values of 4:3 & 16:9.
     *
     *  Detecting the most suitable ratio for dimensions provided in @params by counting absolute
     *  of preview ratio to one of the provided values.
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    /** Method used to re-draw the camera UI controls, called every time configuration changes. */
    private fun updateCameraUi() {
        binding.showCameraController = true
        binding.onClickListener = onClickListener
    }

    private val onClickListener = View.OnClickListener {
        when (it) {
            binding.cameraUiContainer.cameraCaptureButton -> startCountDown()
            binding.cameraUiContainer.cancel -> stopCountDown()
        }
    }

    private fun startCountDown() {
        Log.d(dTag, "startCountDown")
        if (binding.isCountDown!!) stopCountDown()
        countDownCnt = binding.delayType!!.delay
        binding.isCountDown = true
        doCountDown()
    }

    private fun doCountDown() {
        Log.d(dTag, "doCountDown")
        val rate = 6f / binding.delayType!!.delay
        binding.root.post {
            binding.countDownClock.apply {
                alpha = 1f
                animate().apply {
                    scaleXBy(rate)
                    scaleYBy(rate)
                    alpha(0f)
                    duration = 1000
                    withStartAction {
                        binding.countdownInfo = "$countDownCnt"
                        if (DEBUG) binding.debugInfo = binding.countdownInfo
                        binding.executePendingBindings()
                    }
                    withEndAction {
                        countDownCnt--
                        if (countDownCnt < 1) {
                            binding.countdownInfo = null
                            binding.isCountDown = false
                            if (DEBUG) binding.debugInfo = null
                            capturePhoto()
                        } else binding.root.post(countDownRunnable)
                    }
                    start()
                }
            }
        }
    }

    private fun stopCountDown() {
        Log.d(dTag, "stopCountDown")
        if (!binding.isCountDown!!) return
        binding.mask.alpha = 0f
        binding.debugInfo = null
        binding.countdownInfo = null
        binding.root.removeCallbacks(countDownRunnable)
        binding.isCountDown = false
    }

    private fun capturePhoto() {
        // Get a stable reference of the modifiable image capture use case
        imageCapture?.let { imageCapture ->
            var freshDone = false
            fun startEdit() {
                binding.root.post {
                    findNavController().navigate(
                        R.id.action_camera_fragment_to_photoEditFragment,
                        Bundle().apply {
                            putParcelable(ConstantUtil.KEY_BASE_PHOTO, binding.base)
                            putParcelableArrayList(
                                ConstantUtil.KEY_FRAME_SELECTION_STATUS,
                                frameSelectionStatus
                            )
                        })
                }
            }
            fun capture() {
                // Create output file to hold the image
                val photoFile = createFile(outputDirectory, FILENAME, PHOTO_EXTENSION)

                // Setup image capture metadata
                val metadata = Metadata().apply {

                    // Mirror image when using the front camera
                    isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
                }
                imageCapture.takePicture(
                    cameraExecutor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            binding.base = image.convertImageProxyToBitmap(binding)
                            //binding.tmpPhoto.post { binding.tmpPhoto.setImageBitmap(base) }
                            //image.close()
                            //binding.showCameraController = false
                            //pause camera
                            unbindCamera()
                            if(freshDone) {
                                Log.d(dTag, "capture")
                                startEdit()
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e(dTag, exception.toString())
                        }
                    })
            }
            //just for making pause effect, this is not final picture
            //this picture may has a little different with the picture was made by takePicture()
            binding.tmpPhoto.post {
                ObjectAnimator.ofFloat(binding.mask, "alpha", 0.5f, 0f).apply {
                    duration = 100
                    interpolator = FastOutSlowInInterpolator()
                    doOnStart {
                        capture()
                    }
                    doOnEnd {
                        freshDone = true
                        if(binding.base != null) {
                            Log.d(dTag, "animation")
                            startEdit()
                        }
                    }
                    start()
                }
                binding.tmpPhoto.setImageBitmap(binding.viewFinder.bitmap)
            }
        }
    }

    /** Returns true if the device has an available back camera. False otherwise */
    private fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    /** Returns true if the device has an available front camera. False otherwise */
    private fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    private fun setFrame(@DrawableRes drawable: Int) {
        if (binding.frameDrawableRes != drawable) {
            binding.frame.setImageResource(drawable)
            binding.frameDrawableRes = drawable
        } else removeFrame()
    }

    private fun removeFrame() {
        Log.d(dTag, "binding.frameDrawableRes:${binding.frameDrawableRes}")
        if (binding.frameDrawableRes != ConstantUtil.NONE_FRAME) {
            binding.frame.setImageDrawable(null)
            binding.frameDrawableRes = ConstantUtil.NONE_FRAME
            frameSelectionStatus.forEach {
                it.selected = false
            }
            binding.root.findViewById<RecyclerView>(R.id.frameList).adapter?.also {
                it as FrameListAdapter
                it.cleanSelected()
            }
        }
    }

    /**
     * Our custom image analysis class.
     *
     * <p>All we need to do is override the function `analyze` with our desired operations. Here,
     * we compute the average luminosity of the image by looking at the Y plane of the YUV frame.
     */
    private class LuminosityAnalyzer(listener: LumaListener? = null) : ImageAnalysis.Analyzer {
        private val frameRateWindow = 8
        private val frameTimestamps = ArrayDeque<Long>(5)
        private val listeners = ArrayList<LumaListener>().apply { listener?.let { add(it) } }
        private var lastAnalyzedTimestamp = 0L
        var framesPerSecond: Double = -1.0
            private set

        /**
         * Used to add listeners that will be called with each luma computed
         */
        fun onFrameAnalyzed(listener: LumaListener) = listeners.add(listener)

        /**
         * Helper extension function used to extract a byte array from an image plane buffer
         */
        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        /**
         * Analyzes an image to produce a result.
         *
         * <p>The caller is responsible for ensuring this analysis method can be executed quickly
         * enough to prevent stalls in the image acquisition pipeline. Otherwise, newly available
         * images will not be acquired and analyzed.
         *
         * <p>The image passed to this method becomes invalid after this method returns. The caller
         * should not store external references to this image, as these references will become
         * invalid.
         *
         * @param image image being analyzed VERY IMPORTANT: Analyzer method implementation must
         * call image.close() on received images when finished using them. Otherwise, new images
         * may not be received or the camera may stall, depending on back pressure setting.
         *
         */
        override fun analyze(image: ImageProxy) {
            // If there are no listeners attached, we don't need to perform analysis
            if (listeners.isEmpty()) {
                image.close()
                return
            }

            // Keep track of frames analyzed
            val currentTime = System.currentTimeMillis()
            frameTimestamps.push(currentTime)

            // Compute the FPS using a moving average
            while (frameTimestamps.size >= frameRateWindow) frameTimestamps.removeLast()
            val timestampFirst = frameTimestamps.peekFirst() ?: currentTime
            val timestampLast = frameTimestamps.peekLast() ?: currentTime
            framesPerSecond = 1.0 / ((timestampFirst - timestampLast) /
                    frameTimestamps.size.coerceAtLeast(1).toDouble()) * 1000.0

            // Analysis could take an arbitrarily long amount of time
            // Since we are running in a different thread, it won't stall other use cases

            lastAnalyzedTimestamp = frameTimestamps.first

            // Since format in ImageAnalysis is YUV, image.planes[0] contains the luminance plane
            val buffer = image.planes[0].buffer

            // Extract image data from callback object
            val data = buffer.toByteArray()

            // Convert the data into an array of pixel values ranging 0-255
            val pixels = data.map { it.toInt() and 0xFF }

            // Compute average luminance for the image
            val luma = pixels.average()

            // Call all listeners with new value
            listeners.forEach { it(luma) }

            image.close()
        }
    }

    companion object {
        private const val DEBUG = false
        private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PHOTO_EXTENSION = ".jpg"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0

        /** Helper function used to create a timestamped file */
        private fun createFile(baseFolder: File, format: String, extension: String) =
            File(
                baseFolder, SimpleDateFormat(format, Locale.US)
                    .format(System.currentTimeMillis()) + extension
            )
    }

    inner class FrameListAdapter(private val frameDrawableRes: ArrayList<FrameSelectionStatus>) :
        RecyclerView.Adapter<FrameListAdapter.FrameListHolder>() {
        inner class FrameListHolder constructor(val binding: FrameItemBinding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(position: Int) {
                binding.isClearOption = position == 0
                if (binding.isClearOption!!) {
                    binding.clearFrame.setOnClickListener {
                        cleanSelected()
                        removeFrame()
                    }
                } else {
                    val index = position - 1
                    binding.selected = frameDrawableRes[index].selected
                    binding.frame.setImageResource(frameDrawableRes[index].drawableRes)
                    binding.root.setOnClickListener {
                        cleanSelected(position)
                        binding.selected = !binding.selected!!
                        frameDrawableRes[index].selected = binding.selected
                        if (binding.selected!!) {
                            setFrame(frameDrawableRes[position - 1].drawableRes)
                        } else removeFrame()
                    }
                }
            }
        }

        private val allBinding = SparseArray<FrameItemBinding>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FrameListHolder =
            FrameListHolder(
                DataBindingUtil.inflate<FrameItemBinding>(
                    LayoutInflater.from(parent.context),
                    viewType,
                    parent,
                    false
                ).apply {
                    selected = false
                }
            )

        override fun onBindViewHolder(holder: FrameListHolder, position: Int) =
            holder.bind(position).apply {
                allBinding[position] = holder.binding
            }

        override fun getItemCount(): Int = frameDrawableRes.size + 1

        override fun getItemViewType(position: Int): Int = R.layout.frame_item

        fun cleanSelected(ignore: Int? = null) {
            allBinding.forEach { key, value ->
                if (ignore != null) {
                    if (key != ignore) value.selected = false
                } else value.selected = false
            }
        }
    }
}

fun ImageProxy.convertImageProxyToBitmap(binding: FragmentCameraBinding): Bitmap? {
    if (image == null) return null
    val planeProxy = image!!.planes[0]
    val buffer: ByteBuffer = planeProxy.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val ei = ExifInterface(ByteArrayInputStream(bytes))
    val img = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val o = ei.getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL
    )
/*    binding.debugInfo = "rotation:${
        binding.rotation?.let {
            when (it) {
                Surface.ROTATION_0 -> "ROTATION_0"
                Surface.ROTATION_90 -> "ROTATION_90"
                Surface.ROTATION_180 -> "ROTATION_180"
                Surface.ROTATION_270 -> "ROTATION_270"
                else -> "$it"
            }
        }
    }\no_rotation:${
        when (o) {
            ExifInterface.ORIENTATION_ROTATE_90 -> "ROTATION_90"
            ExifInterface.ORIENTATION_ROTATE_180 -> "ROTATION_180"
            ExifInterface.ORIENTATION_ROTATE_270 -> "ROTATION_270"
            else -> "$o"
        }
    }"*/
    return when (o) {
        ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(img, 90)
        ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(img, 180)
        ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(img, 270)
        else -> img
    }
}