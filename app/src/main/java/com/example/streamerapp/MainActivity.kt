package com.example.streamerapp

//import android.app.Fragment
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.Image
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.streamerapp.databinding.ActivityMainBinding
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.w3c.dom.Text
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

enum class STATUS {
    STS_IDLE,
    STS_STREAM,
    STS_CAMERA,
    STS_GALLERY
}

class MainActivity : AppCompatActivity(), View.OnClickListener,
    ImageReader.OnImageAvailableListener, SurfaceTextureListener {

    private lateinit var binding: ActivityMainBinding

    lateinit var libVlc: LibVLC
    lateinit var mediaPlayer: MediaPlayer

    private var rtspUrl: String = "rtsp://192.168.106.160:8554/"

    private lateinit var mTextureView: TextureView
    private lateinit var cameraLayout: FrameLayout

    private lateinit var btnStream: Button
    private lateinit var btnCamera: Button
    private lateinit var btnGallery: Button

    private var prevTimeVLC: Long = 0
    private var prevTimeCAM: Long = 0

    private var timeCounterVLC: Long = 0
    private var timeCounterCAM: Long = 0

    private var fpsVLC: Int = 0
    private var fpsCAM: Int = 0

    private lateinit var txtPrevTimeVLC: TextView
    private lateinit var txtPrevTimeCAM: TextView

    private lateinit var txtFPSVLC: TextView
    private lateinit var txtFPSCAM: TextView

    private var counter : Int = 0

    private var status: STATUS = STATUS.STS_IDLE

    private val resultLauncher = registerForActivityResult(GetContent()) { uri: Uri? -> playVideo(uri)}

    private fun playVideo(uri: Uri?) {
        if (uri == null) {
            return
        }
        val fd = contentResolver.openFileDescriptor(uri, "r")
        val media = Media(libVlc, fd!!.fileDescriptor)
        media.setHWDecoderEnabled(true, false)
        media.addOption(":network-caching=600")

        mediaPlayer.media = media
        media.release()
        mediaPlayer.play()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set for RTSP
        libVlc = LibVLC(this)
        mediaPlayer = MediaPlayer(libVlc)
        mTextureView = findViewById(R.id.textureView)

        btnStream = findViewById(R.id.btn_stream)
        btnStream.setOnClickListener(this)
        btnCamera = findViewById(R.id.btn_camera)
        btnCamera.setOnClickListener(this)
        btnGallery = findViewById(R.id.btn_gallery)
        btnGallery.setOnClickListener(this)
        txtPrevTimeVLC = findViewById(R.id.txt_framerate_vlc)
        txtPrevTimeCAM = findViewById(R.id.txt_framerate_cam)
        txtFPSVLC = findViewById(R.id.txt_fps_vlc)
        txtFPSCAM = findViewById(R.id.txt_fps_cam)

        // Set for Phone Camera
        cameraLayout = findViewById(R.id.cameraLayout)

        if (!Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            startActivity(intent)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED || checkSelfPermission(
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                    == PackageManager.PERMISSION_DENIED
                ) {
                    val permission = arrayOf<String>(
                        android.Manifest.permission.CAMERA,
                        android.Manifest.permission.MANAGE_EXTERNAL_STORAGE
                    )
                    requestPermissions(permission, 1)
                }
            }
        }
    }

    var previewHeight = 0
    var previewWidth = 0
    var sensorOrientation = 0
    private lateinit var fragment: Fragment

    private fun setCameraFragment() {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var cameraId: String? = null
        try {
            cameraId = manager.cameraIdList[0]
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

        val camera2Fragment = CameraConnectionFragment.newInstance(object: CameraConnectionFragment.ConnectionCallback {
            override fun onPreviewSizeChosen(size: Size?, cameraRotation: Int) {
                previewHeight = size!!.height
                previewWidth = size.width
                sensorOrientation = cameraRotation - getScreenOrientation()
            }
        }, this, R.layout.camera_fragment, Size(640, 480))
        camera2Fragment.setCamera(cameraId)

        fragment = camera2Fragment

        supportFragmentManager.beginTransaction().replace(R.id.cameraLayout, fragment).commit()
    }

    fun getScreenOrientation(): Int {
        return when(windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_270 -> 270
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_90 -> 90
            else -> 0
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun attachViewSurface () {
        if (!mediaPlayer.vlcVout.areViewsAttached()) {
            mediaPlayer.vlcVout.setVideoView(mTextureView)
            mediaPlayer.vlcVout.setWindowSize(mTextureView.width, mTextureView.height)
            mediaPlayer.vlcVout.attachViews()
            mTextureView.keepScreenOn = true
            mTextureView.surfaceTextureListener = this
        }
    }

    private fun playStream() {
        val media = Media(libVlc, Uri.parse(rtspUrl))

        media.setHWDecoderEnabled(true, false)
        media.addOption(":network-caching=600")

        mediaPlayer.media = media
        media.release()
        mediaPlayer.play()
    }

    private fun stopStream() {
        mediaPlayer.stop()
        mediaPlayer.detachViews()
    }

    override fun onDestroy() {
        super.onDestroy()

        mediaPlayer.release()
        libVlc.release()
    }

    override fun onClick(p0: View?) {
        prevTimeCAM = 0
        prevTimeVLC = 0
        timeCounterVLC = 0
        timeCounterCAM = 0
        fpsCAM = 0
        fpsVLC = 0

        if (p0!!.id == R.id.btn_stream) {
            if (status == STATUS.STS_STREAM)
                return

            mTextureView.visibility = View.VISIBLE
            cameraLayout.visibility = View.GONE
            txtPrevTimeCAM.visibility = View.GONE
            txtPrevTimeVLC.visibility = View.VISIBLE
            txtFPSVLC.visibility = View.VISIBLE
            txtFPSCAM.visibility = View.GONE

            attachViewSurface()

            var builder: AlertDialog.Builder = AlertDialog.Builder(this)
            builder.setTitle("Enter a RTSP url")
            val inputbox: EditText = EditText(this)
            inputbox.inputType = InputType.TYPE_CLASS_TEXT
            builder.setView(inputbox)

            builder.setPositiveButton("OK") { dialog, which ->
                rtspUrl = inputbox.text.toString()
                counter = 0
                playStream()
                status = STATUS.STS_STREAM
            }
            builder.setNegativeButton("Cancel") {_, _ -> }
            builder.show()
        } else if (p0!!.id == R.id.btn_camera) {
            if (status == STATUS.STS_CAMERA)
                return
            cameraLayout.visibility = View.VISIBLE
            mTextureView.visibility = View.GONE

            if (!supportFragmentManager.beginTransaction().isEmpty)
                supportFragmentManager.beginTransaction().remove(fragment)
            setCameraFragment()

            status = STATUS.STS_CAMERA

            txtPrevTimeCAM.visibility = View.VISIBLE
            txtPrevTimeVLC.visibility = View.GONE
            txtFPSCAM.visibility = View.VISIBLE
            txtFPSVLC.visibility = View.GONE

            counter = 0
        } else if (p0!!.id == R.id.btn_gallery) {
            if (status == STATUS.STS_GALLERY)
                return
            status = STATUS.STS_GALLERY

            mTextureView.visibility = View.VISIBLE
            cameraLayout.visibility = View.GONE
            txtPrevTimeCAM.visibility = View.GONE
            txtPrevTimeVLC.visibility = View.VISIBLE
            txtFPSVLC.visibility = View.VISIBLE
            txtFPSCAM.visibility = View.GONE

            attachViewSurface()

            resultLauncher.launch("video/*")
            counter = 0
        }
    }

    override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
    }

    override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {
        TODO("Not yet implemented")
    }

    override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
        TODO("Not yet implemented")
    }

    override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
        val current: Long = Calendar.getInstance().time.time
        if (prevTimeVLC > 0) {
            val latency = current - prevTimeVLC
            txtPrevTimeVLC.text = latency.milliseconds.toString()
            timeCounterVLC += latency.milliseconds.toLong(DurationUnit.MILLISECONDS)
            fpsVLC += 1
            if (timeCounterVLC >= 1000) {
                txtFPSVLC.text = "$fpsVLC fps"
                fpsVLC = 0
                timeCounterVLC = 0
            }
        }

        prevTimeVLC = current

        if (counter < 5) {
            if (mTextureView.bitmap != null)
                saveImageToStorage(mTextureView.bitmap!!, "gallery")
            counter +=1
        }
    }

    private var isProcessingFrame = false
    private val yuvBytes = arrayOfNulls<ByteArray>(3)
    private var rgbBytes: IntArray? = null
    private var yRowStride = 0
    private var postInferenceCallback: Runnable? = null
    private var imageConverter: Runnable? = null
    private var rgbFrameBitmap: Bitmap? = null // where frame is being stored

    override fun onImageAvailable(p0: ImageReader?) {
        if (previewWidth == 0 || previewHeight == 0) {
            return
        }
        if (rgbBytes == null) {
            rgbBytes = IntArray(previewWidth * previewHeight)
        }

        try {
            val image = p0!!.acquireLatestImage() ?: return
            if (isProcessingFrame) {
                image.close()
                return
            }
            isProcessingFrame = true
            val planes = image.planes
            fillBytes(planes, yuvBytes)
            yRowStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride
            imageConverter = Runnable {
                ImageUtils.convertYUV420ToARGB8888(
                    yuvBytes[0]!!,
                    yuvBytes[1]!!,
                    yuvBytes[2]!!,
                    previewWidth,
                    previewHeight,
                    yRowStride,
                    uvRowStride,
                    uvPixelStride,
                    rgbBytes!!
                )
            }
            postInferenceCallback = Runnable {
                image.close()
                isProcessingFrame = false
            }
            val current2: Long = Calendar.getInstance().time.time
            if (prevTimeCAM > 0) {
                val latency = current2 - prevTimeCAM
                txtPrevTimeCAM.text = latency.milliseconds.toString()
                timeCounterCAM += latency.milliseconds.toLong(DurationUnit.MILLISECONDS)
                fpsCAM += 1
                if (timeCounterCAM >= 1000) {
                    txtFPSCAM.text = "$fpsCAM fps"
                    fpsCAM = 0
                    timeCounterCAM = 0
                }
            }
            prevTimeCAM = current2
            processImage()
        } catch (e: Exception) {
            return
        }
    }

    private fun saveImageToStorage(bmp: Bitmap, fnamePrefix: String): Boolean {
        val current: String = Calendar.getInstance().time.time.toString()
        val filename = "$fnamePrefix$current.jpg"
        val mimeType: String = "image/jpeg"
        val directory: String = Environment.DIRECTORY_DCIM
        val mediaContentUri: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val imageOutStream: OutputStream

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, directory)
                }
                contentResolver.run {
                    val uri = contentResolver.insert(mediaContentUri, values) ?: return false
                    imageOutStream = openOutputStream(uri) ?: return false
                }
            } else {
                val imagePath = Environment.getExternalStoragePublicDirectory(directory).absolutePath
                val image = File(imagePath, filename)
                imageOutStream = FileOutputStream(image)
            }
            imageOutStream.use { bmp!!.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            return true
        } catch(e: java.lang.Exception) {
            return false
        }
    }

    private fun processImage() {
        imageConverter!!.run()
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        rgbFrameBitmap?.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight)

        if (counter < 5) {
            saveImageToStorage(rgbFrameBitmap as Bitmap, "frame")
            counter += 1
        }

        postInferenceCallback!!.run()
    }

    private fun fillBytes(planes: Array<Image.Plane>, yuvBytes:Array<ByteArray?>) {
        for (i in planes.indices) {
            val buffer = planes[i].buffer
            if (yuvBytes[i] == null) {
                yuvBytes[i] = ByteArray(buffer.capacity())
            }
            buffer[yuvBytes[i]]
        }
    }
}