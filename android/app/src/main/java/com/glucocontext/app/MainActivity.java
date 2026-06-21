package com.glucocontext.app;

import android.content.Intent;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(AapsMonitorWorkerPlugin.class);
        registerPlugin(AapsShareIntakePlugin.class);
        registerPlugin(AapsOcrPlugin.class);
        registerPlugin(AapsSmartWidgetPlugin.class);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }
}
