package com.example.karaoke

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.karaoke.di.AppContainer

class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        container = AppContainer(this)
        container.initPlayback()

        setContent {
            KaraokeApp(container = container)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            KeyEventRouter.handler?.let { handler ->
                if (handler(event.keyCode)) return true
            }
            if (event.keyCode == KeyEvent.KEYCODE_BACK && KeyEventRouter.handler == null) {
                finish()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
