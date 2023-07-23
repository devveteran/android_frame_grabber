package com.example.streamerapp

//import android.app.Fragment
import android.content.ContentValues
import android.content.Context
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
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.streamerapp.databinding.ActivityMainBinding
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IVLCVout
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.*

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

//    private var rtsp_url: String = "rtsp://wowzaec2demo.streamlock.net/vod/mp4:BigBuckBunny_115k.mp4"
    private var rtspUrl: String = "rtsp://192.168.106.160:8554/"

    private lateinit var mTextureView: TextureView
    private lateinit var cameraLayout: FrameLayout

    private lateinit var btn_stream: Button
    private lateinit var btn_camera: Button
    private lateinit var btn_gallery: Button

    private lateinit var btn_capture: Button
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

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set for RTSP
        libVlc = LibVLC(this)
        mediaPlayer = MediaPlayer(libVlc)
        mTextureView = findViewById(R.id.textureView)
//        attachViewSurface()



        btn_stream = findViewById(R.id.btn_stream)
        btn_stream.setOnClickListener(this)
        btn_camera = findViewById(R.id.btn_camera)
        btn_camera.setOnClickListener(this)
        btn_gallery = findViewById(R.id.btn_gallery)
        btn_gallery.setOnClickListener(this)
        btn_capture = findViewById(R.id.btn_capture)
        btn_capture.setOnClickListener(this)


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
        mediaPlayer.vlcVout.setVideoView(mTextureView)
        mediaPlayer.vlcVout.setWindowSize(mTextureView.width, mTextureView.height)
        mediaPlayer.vlcVout.attachViews()
        mTextureView.keepScreenOn = true
        mTextureView.surfaceTextureListener = this
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
        Log.d("", p0!!.id.toString())
        if (p0!!.id == R.id.btn_stream) {
            mTextureView.visibility = View.VISIBLE
            cameraLayout.visibility = View.GONE

            if (status != STATUS.STS_STREAM) {
                playStream()
                status = STATUS.STS_STREAM
            } else {
                stopStream()
                status = STATUS.STS_IDLE
            }
        } else if (p0!!.id == R.id.btn_camera) {
            mTextureView.visibility = View.GONE
            cameraLayout.visibility = View.VISIBLE
            if (status != STATUS.STS_CAMERA) {
                setCameraFragment()
                status = STATUS.STS_CAMERA
            } else {
                supportFragmentManager.beginTransaction().remove(fragment)
                status = STATUS.STS_IDLE
            }
        } else if (p0!!.id == R.id.btn_gallery) {
            attachViewSurface()
            mTextureView.visibility = View.VISIBLE
            cameraLayout.visibility = View.GONE
            resultLauncher.launch("video/*")
            counter = 0
        } else if (p0!!.id == R.id.btn_capture) {
            counter = 0
        }
    }

    override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
//        if (mediaPlayer.hasMedia())
//            mediaPlayer.play()
    }

    override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {
        TODO("Not yet implemented")
    }

    override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
        TODO("Not yet implemented")
    }

    override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
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