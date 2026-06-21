// ── MainActivity.kt — adicionar estas duas linhas ────────────────────────────
// ANTES (original gerado pelo Capacitor):
//   class MainActivity : BridgeActivity()
//
// DEPOIS (com o plugin registrado):
//   class MainActivity : BridgeActivity() {
//     override fun onCreate(savedInstanceState: Bundle?) {
//       registerPlugin(AapsBackgroundPlugin::class.java)
//       super.onCreate(savedInstanceState)
//     }
//   }
//
// Imports necessários no topo do arquivo:
//   import android.os.Bundle
//   import com.glucocontext.app.AapsBackgroundPlugin

package com.glucocontext.app

import android.os.Bundle
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(AapsBackgroundPlugin::class.java)
        super.onCreate(savedInstanceState)
    }
}
