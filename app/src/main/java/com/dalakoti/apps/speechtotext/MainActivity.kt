package com.dalakoti.apps.speechtotext

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import com.dalakoti.apps.speechtotext.ui.AppScaffold
import com.dalakoti.apps.speechtotext.ui.DictationViewModel
import com.dalakoti.apps.speechtotext.ui.theme.SpeechToTextTheme

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: DictationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as SpeechToTextApp).container
        viewModel = ViewModelProvider(
            this,
            DictationViewModel.factory(application, container),
        )[DictationViewModel::class.java]

        // All files access has no permission callback and can be revoked while we are
        // alive, so the grant is re-checked on every resume rather than trusted once.
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
            }
        )

        setContent {
            SpeechToTextTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppScaffold(viewModel)
                }
            }
        }
    }
}
