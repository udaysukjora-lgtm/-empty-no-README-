package com.example.askqustion

import android.Manifest
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.activity.addCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraImageUri: Uri? = null
    private var pendingCameraVideoUri: Uri? = null
    private var pendingChooserParams: WebChromeClient.FileChooserParams? = null

    private val requestCameraPermission: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Granted or not, proceed with whatever chooser options are now available.
            launchFileChooser(pendingChooserParams)
        }

    private val fileChooserLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = pendingFileCallback
            pendingFileCallback = null
            if (callback == null) return@registerForActivityResult

            if (result.resultCode != RESULT_OK) {
                callback.onReceiveValue(null)
                return@registerForActivityResult
            }

            val data = result.data
            val uris: Array<Uri> = when {
                data?.clipData != null -> {
                    val clip = data.clipData!!
                    Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
                }
                data?.data != null -> arrayOf(data.data!!)
                pendingCameraVideoUri != null -> arrayOf(pendingCameraVideoUri!!)
                pendingCameraImageUri != null -> arrayOf(pendingCameraImageUri!!)
                else -> emptyArray()
            }
            callback.onReceiveValue(uris.takeIf { it.isNotEmpty() })
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        setupWebView()
        swipeRefresh.setOnRefreshListener { webView.reload() }

        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) webView.goBack() else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(BuildConfig.BASE_URL)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    private fun setupWebView() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val host = uri.host.orEmpty()
                val scheme = uri.scheme.orEmpty()

                val isOurSite = host == APEX_HOST || host.endsWith(".$APEX_HOST")
                if (isOurSite && (scheme == "http" || scheme == "https")) return false

                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                } catch (e: ActivityNotFoundException) {
                    true
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                swipeRefresh.isRefreshing = false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onShowFileChooser(
                view: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams,
            ): Boolean {
                pendingFileCallback?.onReceiveValue(null)
                pendingFileCallback = filePathCallback
                pendingChooserParams = fileChooserParams

                if (hasCameraPermission()) {
                    launchFileChooser(fileChooserParams)
                } else {
                    requestCameraPermission.launch(Manifest.permission.CAMERA)
                }
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                val granted = request.resources.filter { resource ->
                    when (resource) {
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> hasCameraPermission()
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> hasPermission(Manifest.permission.RECORD_AUDIO)
                        else -> false
                    }
                }
                if (granted.isNotEmpty()) request.grant(granted.toTypedArray()) else request.deny()
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url))
                addRequestHeader("User-Agent", userAgent)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            getSystemService<DownloadManager>()?.enqueue(request)
        }
    }

    private fun launchFileChooser(params: WebChromeClient.FileChooserParams?) {
        pendingChooserParams = null
        val callback = pendingFileCallback
        if (callback == null || params == null) return

        val acceptTypes = params.acceptTypes.filter { it.isNotBlank() && it.contains("/") }

        val contentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            when (acceptTypes.size) {
                0 -> type = "*/*"
                1 -> type = acceptTypes[0]
                else -> {
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes.toTypedArray())
                }
            }
            if (params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        }

        val initialIntents = mutableListOf<Intent>()
        if (hasCameraPermission()) {
            createImageCaptureIntent()?.let { initialIntents.add(it) }
            createVideoCaptureIntent()?.let { initialIntents.add(it) }
        }

        val chooserIntent = Intent.createChooser(contentIntent, "Photo, video, ya file chunein").apply {
            if (initialIntents.isNotEmpty()) {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, initialIntents.toTypedArray())
            }
        }

        fileChooserLauncher.launch(chooserIntent)
    }

    private fun createImageCaptureIntent(): Intent? {
        val photoFile = createCaptureFile("images", ".jpg") ?: return null
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
        pendingCameraImageUri = uri
        return Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.takeIf { it.resolveActivity(packageManager) != null }
    }

    private fun createVideoCaptureIntent(): Intent? {
        val videoFile = createCaptureFile("videos", ".mp4") ?: return null
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", videoFile)
        pendingCameraVideoUri = uri
        return Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.takeIf { it.resolveActivity(packageManager) != null }
    }

    private fun createCaptureFile(subDir: String, suffix: String): File? {
        val dir = File(cacheDir, subDir).apply { mkdirs() }
        return try {
            File.createTempFile("capture_${System.currentTimeMillis()}_", suffix, dir)
        } catch (e: java.io.IOException) {
            null
        }
    }

    private fun hasCameraPermission() = hasPermission(Manifest.permission.CAMERA)

    private fun hasPermission(permission: String) =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        /** "www.askqustion.in" -> "askqustion.in", so both the apex and any subdomain match. */
        private val APEX_HOST = (Uri.parse(BuildConfig.BASE_URL).host ?: "www.askqustion.in")
            .removePrefix("www.")
    }
}
