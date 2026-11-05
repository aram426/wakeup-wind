package kr.kkaeumbaram

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Gleo Interface가 보내는 Action 브로드캐스트를 받아 앱으로 전달. 스키마: assets/gleo_schema.json */
class GleoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val param = intent.getStringExtra("ACTION_PARAM") ?: "{}"
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_GLEO_ACTION, action)
                .putExtra(MainActivity.EXTRA_GLEO_PARAM, param)
        )
    }
}
