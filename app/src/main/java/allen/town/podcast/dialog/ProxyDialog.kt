package allen.town.podcast.dialog

import allen.town.podcast.common.util.Timber
import allen.town.podcast.common.views.AccentMaterialDialog
import allen.town.podcast.R
import allen.town.podcast.core.pref.Prefs
import allen.town.podcast.core.service.download.PodcastHttpClient
import allen.town.podcast.databinding.ProxySettingsBinding
import allen.town.podcast.model.download.ProxyConfig
import android.app.Dialog
import android.content.Context
import android.os.Build
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import io.reactivex.Completable
import io.reactivex.CompletableEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.Locale
import okhttp3.Credentials
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.lang.NumberFormatException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketAddress
import java.util.ArrayList
import java.util.concurrent.TimeUnit

class ProxyDialog(private val context: Context) {
    private lateinit var binding: ProxySettingsBinding
    private lateinit var dialog: AlertDialog
    private var testSuccessful = false
    private var disposable: Disposable? = null

    fun show(): Dialog {
        binding = ProxySettingsBinding.inflate(LayoutInflater.from(context))
        dialog = AccentMaterialDialog(
            context,
            R.style.MaterialAlertDialogTheme
        )
            .setTitle(R.string.pref_proxy_title)
            .setView(binding.root)
            .setNegativeButton(R.string.cancel_label, null)
            .setPositiveButton(R.string.proxy_test_label, null)
            .setNeutralButton(R.string.reset, null)
            .show()
        // To prevent cancelling the dialog on button click
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (!testSuccessful) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                test()
                return@setOnClickListener
            }
            setProxyConfig()
            PodcastHttpClient.reinit()
            dialog.dismiss()
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            binding.etHost.text?.clear()
            binding.etPort.text?.clear()
            binding.etUsername.text?.clear()
            binding.etPassword.text?.clear()
            setProxyConfig()
        }
        val types: MutableList<String> = ArrayList()
        types.add(Proxy.Type.DIRECT.name)
        types.add(Proxy.Type.HTTP.name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            types.add(Proxy.Type.SOCKS.name)
        }
        val adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item, types
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spType.adapter = adapter
        val proxyConfig = Prefs.proxyConfig
        binding.spType.setSelection(adapter.getPosition(proxyConfig.type.name))
        if (!TextUtils.isEmpty(proxyConfig.host)) {
            binding.etHost.setText(proxyConfig.host)
        }
        binding.etHost.addTextChangedListener(requireTestOnChange)
        if (proxyConfig.port > 0) {
            binding.etPort.setText(proxyConfig.port.toString())
        }
        binding.etPort.addTextChangedListener(requireTestOnChange)
        if (!TextUtils.isEmpty(proxyConfig.username)) {
            binding.etUsername.setText(proxyConfig.username)
        }
        binding.etUsername.addTextChangedListener(requireTestOnChange)
        if (!TextUtils.isEmpty(proxyConfig.password)) {
            binding.etPassword.setText(proxyConfig.password)
        }
        binding.etPassword.addTextChangedListener(requireTestOnChange)
        if (proxyConfig.type == Proxy.Type.DIRECT) {
            enableSettings(false)
            setTestRequired(false)
        }
        binding.spType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View,
                position: Int,
                id: Long
            ) {
                if (position == 0) {
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).visibility = View.GONE
                } else {
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).visibility = View.VISIBLE
                }
                enableSettings(position > 0)
                setTestRequired(position > 0)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                enableSettings(false)
            }
        }
        checkValidity()
        return dialog
    }

    private fun setProxyConfig() {
        val type = binding.spType.selectedItem as String
        val typeEnum = Proxy.Type.valueOf(type)
        val host = binding.etHost.text?.toString().orEmpty()
        val port = binding.etPort.text?.toString().orEmpty()
        var username: String? = binding.etUsername.text?.toString().orEmpty()
        if (TextUtils.isEmpty(username)) {
            username = null
        }
        var password: String? = binding.etPassword.text?.toString().orEmpty()
        if (TextUtils.isEmpty(password)) {
            password = null
        }
        var portValue = 0
        if (!TextUtils.isEmpty(port)) {
            portValue = port.toInt()
        }
        val config = ProxyConfig(typeEnum, host, portValue, username, password)
        Prefs.proxyConfig = config
        PodcastHttpClient.setProxyConfig(config)
    }

    private val requireTestOnChange: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable) {
            setTestRequired(true)
        }
    }

    private fun enableSettings(enable: Boolean) {
        binding.etHost.isEnabled = enable
        binding.etPort.isEnabled = enable
        binding.etUsername.isEnabled = enable
        binding.etPassword.isEnabled = enable
    }

    private fun checkValidity(): Boolean {
        var valid = true
        if (binding.spType.selectedItemPosition > 0) {
            valid = checkHost()
        }
        valid = valid and checkPort()
        return valid
    }

    private fun checkHost(): Boolean {
        val host = binding.etHost.text?.toString().orEmpty()
        if (host.length == 0) {
            binding.etHost.error = context.getString(R.string.proxy_host_empty_error)
            return false
        }
        if ("localhost" != host && !Patterns.DOMAIN_NAME.matcher(host).matches()) {
            binding.etHost.error = context.getString(R.string.proxy_host_invalid_error)
            return false
        }
        return true
    }

    private fun checkPort(): Boolean {
        val port = port
        if (port < 0 || port > 65535) {
            binding.etPort.error = context.getString(R.string.proxy_port_invalid_error)
            return false
        }
        return true
    }

    // ignore
    private val port: Int
        private get() {
            val port = binding.etPort.text?.toString().orEmpty()
            if (port.length > 0) {
                try {
                    return port.toInt()
                } catch (e: NumberFormatException) {
                    // safe to continue: 0 below means "no explicit port", the caller's default
                    Timber.d(e, "ignoring an unparsable proxy port")
                }
            }
            return 0
        }

    private fun setTestRequired(required: Boolean) {
        if (required) {
            testSuccessful = false
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(R.string.proxy_test_label)
        } else {
            testSuccessful = true
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(android.R.string.ok)
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
    }

    private fun test() {
        disposable?.dispose()
        if (!checkValidity()) {
            setTestRequired(true)
            return
        }
        val res = context.theme.obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
        val textColorPrimary = res.getColor(0, 0)
        res.recycle()
        val checking = context.getString(R.string.proxy_checking)
        binding.txtvMessage.setTextColor(textColorPrimary)
        binding.txtvMessage.text = "{fa-circle-o-notch spin} $checking"
        binding.txtvMessage.visibility = View.VISIBLE
        disposable = Completable.create { emitter: CompletableEmitter ->
            val type = binding.spType.selectedItem as String
            val host = binding.etHost.text?.toString().orEmpty()
            val port = binding.etPort.text?.toString().orEmpty()
            val username = binding.etUsername.text?.toString().orEmpty()
            val password = binding.etPassword.text?.toString().orEmpty()
            var portValue = 8080
            if (!TextUtils.isEmpty(port)) {
                portValue = port.toInt()
            }
            val address: SocketAddress = InetSocketAddress.createUnresolved(host, portValue)
            val proxyType = Proxy.Type.valueOf(type.uppercase())
            val builder = PodcastHttpClient.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .proxy(Proxy(proxyType, address))
            if (!TextUtils.isEmpty(username)) {
                builder.proxyAuthenticator { route: Route?, response: Response ->
                    val credentials = Credentials.basic(username, password)
                    response.request.newBuilder()
                        .header("Proxy-Authorization", credentials)
                        .build()
                }
            }
            val client = builder.build()
            val request = Request.Builder().url("https://www.example.com").head().build()
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        emitter.onComplete()
                    } else {
                        emitter.onError(IOException(response.message))
                    }
                }
            } catch (e: IOException) {
                emitter.onError(e)
            }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    binding.txtvMessage.setTextColor(
                        ContextCompat.getColor(
                            context,
                            R.color.download_success_green
                        )
                    )
                    val message = String.format(
                        Locale.getDefault(),
                        "%s %s", "{fa-check}",
                        context.getString(R.string.proxy_test_successful)
                    )
                    binding.txtvMessage.text = message
                    setTestRequired(false)
                }
            ) { error: Throwable ->
                Timber.e(error, "the proxy test failed")
                binding.txtvMessage.setTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.download_failed_red
                    )
                )
                val message = String.format(
                    Locale.getDefault(),
                    "%s %s: %s", "{fa-close}",
                    context.getString(R.string.proxy_test_failed), error.message
                )
                binding.txtvMessage.text = message
                setTestRequired(true)
            }
    }
}
