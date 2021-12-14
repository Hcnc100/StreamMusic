package com.nullpointer.streammusic.ui.activitys

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.nullpointer.streammusic.R
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_StreamMusic)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}