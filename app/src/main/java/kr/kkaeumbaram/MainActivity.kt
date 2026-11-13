package kr.kkaeumbaram

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    companion object { const val EXTRA_GLEO_ACTION = "gleo_action"; const val EXTRA_GLEO_PARAM = "gleo_param" }
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}.launch(arrayOf(Manifest.permission.RECORD_AUDIO, "pleos.car.permission.FUSED_LOCATION", "android.car.permission.CAR_SPEED"))
        handleGleo(intent)
        setContent {
            val ui = vm.ui.collectAsStateWithLifecycle().value
            AppScreen(ui, vm)
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleGleo(intent) }
    private fun handleGleo(i: Intent?) { i?.getStringExtra(EXTRA_GLEO_ACTION)?.let { vm.gleo(it); i.removeExtra(EXTRA_GLEO_ACTION) } }
}
