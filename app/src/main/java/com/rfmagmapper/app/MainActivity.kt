package com.rfmagmapper.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rfmagmapper.app.ui.RFMagMapperScreen
import com.rfmagmapper.app.ui.theme.RFMagMapperTheme
import com.rfmagmapper.app.viewmodel.RFMagViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RFMagMapperTheme {
                val vm: RFMagViewModel = viewModel()
                RFMagMapperScreen(viewModel = vm)
            }
        }
    }
}
