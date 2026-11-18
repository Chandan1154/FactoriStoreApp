package com.factori.storeapp

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorPanel: View
    private lateinit var errorMessage: TextView
    private var fileChooserCallback: android.webkit.ValueCallback<Array<Uri>>? = null
    private var storefrontUrl: String = ""
    private var storefrontHost: String = ""

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val selected = result.data
        val uris = when {
            result.resultCode != RESULT_OK || selected == null -> null
            selected.clipData != null -> Array(selected.clipData!!.itemCount) { index ->
                selected.clipData!!.getItemAt(index).uri
            }
            selected.data != null -> arrayOf(selected.data!!)
            else -> null
        }
        fileChooserCallback?.onReceiveValue(uris)
        fileChooserCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        progressBar = findViewById(R.id.loading_progress)
        errorPanel = findViewById(R.id.error_panel)
        errorMessage = findViewById(R.id.error_message)
        findViewById<Button>(R.id.retry_button).setOnClickListener {
            errorPanel.visibility = View.GONE
            webView.loadUrl(storefrontUrl)
        }

        val configuredUrl = StoreUrlPolicy.normalizeStorefrontUrl(
            getString(R.string.storefront_url)
        )
        if (configuredUrl == null) {
            findViewById<Button>(R.id.retry_button).isEnabled = false
            showError(getString(R.string.invalid_storefront_message))
            return
        }
        storefrontUrl = configuredUrl
        storefrontHost = Uri.parse(storefrontUrl).host.orEmpty()
        if (storefrontHost.isEmpty()) {
            findViewById<Button>(R.id.retry_button).isEnabled = false
            showError(getString(R.string.invalid_storefront_message))
            return
        }

        webView = findViewById(R.id.storefront_webview)
        configureWebView()
        if (savedInstanceState == null) {
            webView.loadUrl(storefrontUrl)
        } else {
            if (webView.restoreState(savedInstanceState) == null) {
                webView.loadUrl(storefrontUrl)
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) webView.goBack() else finish()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = routeNavigation(request.url)

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                routeNavigation(Uri.parse(url))

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                progressBar.visibility = View.VISIBLE
                errorPanel.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progressBar.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    progressBar.visibility = View.GONE
                    showError(getString(R.string.network_error_message))
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                if (request.isForMainFrame && errorResponse.statusCode >= 500) {
                    progressBar.visibility = View.GONE
                    showError(getString(R.string.server_error_message))
                }
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: android.webkit.SslErrorHandler,
                error: android.net.http.SslError
            ) {
                handler.cancel()
                progressBar.visibility = View.GONE
                showError(getString(R.string.secure_connection_error_message))
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: android.webkit.ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback
                return try {
                    filePicker.launch(fileChooserParams.createIntent())
                    true
                } catch (_: ActivityNotFoundException) {
                    fileChooserCallback = null
                    filePathCallback.onReceiveValue(null)
                    Toast.makeText(
                        this@MainActivity,
                        R.string.file_picker_unavailable,
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }
            }
        }
    }

    private fun routeNavigation(uri: Uri): Boolean {
        if (StoreUrlPolicy.isAllowedWebViewUrl(uri, storefrontHost)) return false

        val scheme = uri.scheme?.lowercase()
        when {
            scheme == "intent" -> openPaymentIntent(uri)
            scheme == "upi" -> openExternal(uri, showChooser = true)
            scheme in setOf("https", "tel", "mailto", "sms") ->
                openExternal(uri, showChooser = false)
            else -> Toast.makeText(this, R.string.unsupported_link_message, Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private fun openPaymentIntent(uri: Uri) {
        val parsed = try {
            Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
        } catch (_: java.net.URISyntaxException) {
            Toast.makeText(this, R.string.unsupported_link_message, Toast.LENGTH_SHORT).show()
            return
        }
        val packageName = parsed.`package`
        if (packageName !in allowedPaymentPackages) {
            Toast.makeText(this, R.string.unsupported_link_message, Toast.LENGTH_SHORT).show()
            return
        }
        val target = parsed.data
        val targetHost = target?.host
        val isSupportedTarget = when {
            target?.scheme.equals("upi", ignoreCase = true) -> true
            target?.scheme.equals("https", ignoreCase = true) && !targetHost.isNullOrBlank() ->
                StoreUrlPolicy.isStorefrontHost(targetHost, storefrontHost) ||
                    StoreUrlPolicy.isSupportedPaymentHost(targetHost)
            else -> false
        }
        if (!isSupportedTarget) {
            Toast.makeText(this, R.string.unsupported_link_message, Toast.LENGTH_SHORT).show()
            return
        }
        parsed.component = null
        parsed.selector = null
        parsed.action = Intent.ACTION_VIEW
        parsed.flags = 0
        openExternal(parsed, showChooser = false)
    }

    private fun openExternal(uri: Uri, showChooser: Boolean) {
        if (uri.scheme.equals("https", ignoreCase = true) &&
            (uri.host.isNullOrBlank() || uri.userInfo != null)
        ) {
            Toast.makeText(this, R.string.unsupported_link_message, Toast.LENGTH_SHORT).show()
            return
        }
        openExternal(Intent(Intent.ACTION_VIEW, uri), showChooser)
    }

    private fun openExternal(intent: Intent, showChooser: Boolean) {
        try {
            if (showChooser) {
                startActivity(Intent.createChooser(intent, getString(R.string.open_payment_with)))
            } else if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, R.string.no_handler_message, Toast.LENGTH_SHORT).show()
            }
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_handler_message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showError(message: String) {
        errorMessage.text = message
        errorPanel.visibility = View.VISIBLE
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::webView.isInitialized) webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }

    private companion object {
        val allowedPaymentPackages = setOf(
            "com.google.android.apps.nbu.paisa.user",
            "com.phonepe.app",
            "net.one97.paytm",
            "in.org.npci.upiapp",
            "in.amazon.mShop.android.shopping"
        )
    }
}
