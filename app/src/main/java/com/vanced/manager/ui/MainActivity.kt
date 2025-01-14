package com.vanced.manager.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.crowdin.platform.Crowdin
import com.crowdin.platform.LoadingStateListener
import com.google.firebase.messaging.FirebaseMessaging
import com.vanced.manager.BuildConfig.ENABLE_CROWDIN_AUTH
import com.vanced.manager.BuildConfig.VERSION_CODE
import com.vanced.manager.R
import com.vanced.manager.databinding.ActivityMainBinding
import com.vanced.manager.ui.dialogs.DialogContainer
import com.vanced.manager.ui.dialogs.ManagerUpdateDialog
import com.vanced.manager.ui.dialogs.URLChangeDialog
import com.vanced.manager.ui.fragments.HomeFragmentDirections
import com.vanced.manager.ui.fragments.SettingsFragmentDirections
import com.vanced.manager.utils.*
import com.vanced.manager.utils.AppUtils.currentLocale
import com.vanced.manager.utils.AppUtils.faqpkg
import com.vanced.manager.utils.AppUtils.log
import com.vanced.manager.utils.AppUtils.playStorePkg
import com.vanced.manager.utils.AppUtils.vancedRootPkg
import com.vanced.manager.utils.PackageHelper.isPackageInstalled

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding
    private val navHost by lazy { findNavController(R.id.nav_host) }

    companion object {
        const val REQUEST_CODE = 69
    }

    private val loadingObserver = object : LoadingStateListener {
        val tag = "VMLocalisation"
        override fun onDataChanged() {
            log(tag, "Loaded data")
        }

        override fun onFailure(throwable: Throwable) {
            log(tag, "Failed to load data: ${throwable.stackTraceToString()}")
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setFinalTheme()
        super.onCreate(savedInstanceState)
        if (ENABLE_CROWDIN_AUTH)
            authCrowdin()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        with(binding) {
            setSupportActionBar(toolbar)
            toolbar.setupWithNavController(
                this@MainActivity.navHost,
                AppBarConfiguration(this@MainActivity.navHost.graph)
            )
        }
        navHost.addOnDestinationChangedListener { _, currFrag: NavDestination, _ ->
            setDisplayHomeAsUpEnabled(currFrag.id != R.id.home_fragment)
        }

        initDialogs(intent.getBooleanExtra("firstLaunch", false))
        manager.observe(this) {
            if (manager.value?.int("versionCode") ?: 0 > VERSION_CODE) {
                ManagerUpdateDialog.newInstance(true).show(this)
            }
        }
    }

    override fun onBackPressed() {
        if (!navHost.popBackStack())
            finish()
    }

    private fun setDisplayHomeAsUpEnabled(isNeeded: Boolean) {
        binding.toolbar.navigationIcon = if (isNeeded) ContextCompat.getDrawable(this, R.drawable.ic_keyboard_backspace_black_24dp) else null
    }

    override fun onPause() {
        super.onPause()
        Crowdin.unregisterDataLoadingObserver(loadingObserver)
    }

    override fun onResume() {
        setFinalTheme()
        super.onResume()
        Crowdin.registerDataLoadingObserver(loadingObserver)
        if (!canAccessStorage(this)) {
            DialogContainer.storageDialog(this)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.toolbar_about -> {
                navHost.navigate(HomeFragmentDirections.toAboutFragment())
                true
            }
            R.id.toolbar_settings -> {
                navHost.navigate(HomeFragmentDirections.toSettingsFragment())
                true
            }
            R.id.toolbar_log -> {
                navHost.navigate(HomeFragmentDirections.toLogFragment())
                true
            }
            R.id.toolbar_guide -> {
                try {
                    val intent = if (isPackageInstalled(faqpkg, packageManager)) {
                        Intent().apply {
                            component = ComponentName(faqpkg, "$faqpkg.ui.MainActivity")
                        }
                    } else {
                        Intent(Intent.ACTION_VIEW).apply {
                            val uriBuilder = Uri.parse("https://play.google.com/store/apps/details")
                                .buildUpon()
                                .appendQueryParameter("id", faqpkg)
                                .appendQueryParameter("launch", "true")
                            data = uriBuilder.build()
                            setPackage(playStorePkg)
                        }
                    }
                    startActivity(intent)
                    true
                } catch (e: ActivityNotFoundException) {
                    false
                }
            }
            R.id.toolbar_update_manager -> {
                ManagerUpdateDialog.newInstance(false).show(supportFragmentManager, "manager_update")
                true
            }
            R.id.dev_settings -> {
                navHost.navigate(SettingsFragmentDirections.toDevSettingsFragment())
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Crowdin.wrapContext(LanguageContextWrapper.wrap(newBase)))
    }

    //I have no idea why the fuck is super method deprecated
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        onActivityResult(requestCode)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        //update manager language when system language is changed
        @Suppress("DEPRECATION")
        if (newConfig.locale != currentLocale) {
            recreate() //restarting activity in order to recreate viewmodels, otherwise some text won't update
            return
        }

        when (newConfig.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> log("VMUI", "screen orientation changed to portrait")
            Configuration.ORIENTATION_LANDSCAPE -> log("VMUI", "screen orientation changed to landscape")
            else -> log("VMUI", "screen orientation changed to [REDACTED]")
        }

    }

    override fun recreate() {
        //needed for setting language smh
        startActivity(Intent(this, this::class.java))
        finish()
    }

    private fun initDialogs(firstLaunch: Boolean) {
        val prefs = getDefaultSharedPreferences(this)
        val variant = prefs.managerVariant
        prefs.getBoolean("show_root_dialog", true)

        if (intent?.data != null && intent.dataString?.startsWith("https") == true) {
            val urldialog = URLChangeDialog()
            val arg = Bundle()
            arg.putString("url", intent.dataString)
            urldialog.arguments = arg
            urldialog.show(this)
        }

        if (firstLaunch) {
            DialogContainer.showSecurityDialog(this)
            with(FirebaseMessaging.getInstance()) {
                subscribeToTopic("Vanced-Update")
                subscribeToTopic("Music-Update")
                subscribeToTopic("MicroG-Update")
            }
        } else {
            if (isMiuiOptimizationsEnabled) {
                DialogContainer.miuiDialog(this)
            }
        }

        if (!prefs.getBoolean("statement", true)) {
            DialogContainer.statementFalse(this)
        }

        if (variant == "root") {
            if (PackageHelper.getPackageVersionName(vancedRootPkg, packageManager) == "14.21.54") {
                DialogContainer.basicDialog(
                    getString(R.string.hold_on),
                    getString(R.string.magisk_vanced),
                    this
                )
            }
        }
    }

}
