package ph.edu.batstateu.ecoconnect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ph.edu.batstateu.ecoconnect.navigation.EcoConnectRoot
import ph.edu.batstateu.ecoconnect.ui.theme.EcoConnectTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EcoConnectTheme {
                EcoConnectRoot()
            }
        }
    }
}
