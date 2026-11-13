package kr.kkaeumbaram

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
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

    // 시연 패널 단축키: 패널을 닫은 채로도 영상 촬영 중 신호를 넣을 수 있다
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val ui = vm.ui.value
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> vm.setSpeed(ui.speedKmh + 20)
            KeyEvent.KEYCODE_DPAD_DOWN -> vm.setSpeed(ui.speedKmh - 20)
            KeyEvent.KEYCODE_1 -> vm.fastForward(10)
            KeyEvent.KEYCODE_3 -> vm.fastForward(30)
            KeyEvent.KEYCODE_P -> vm.gearPark()
            KeyEvent.KEYCODE_D -> vm.gearDrive()
            KeyEvent.KEYCODE_C -> vm.charge()
            KeyEvent.KEYCODE_R -> vm.gleo("GO_REST")
            KeyEvent.KEYCODE_V -> vm.ventNow()
            KeyEvent.KEYCODE_Y -> vm.answered()
            KeyEvent.KEYCODE_N -> vm.noAnswer()
            KeyEvent.KEYCODE_S -> vm.strongMode(!ui.s.strongMode)
            KeyEvent.KEYCODE_M -> vm.toggleDemo()
            KeyEvent.KEYCODE_A -> vm.toggleAdmin()
            else -> return super.onKeyUp(keyCode, event)
        }
        return true
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleGleo(intent) }
    private fun handleGleo(i: Intent?) { i?.getStringExtra(EXTRA_GLEO_ACTION)?.let { vm.gleo(it); i.removeExtra(EXTRA_GLEO_ACTION) } }
}
