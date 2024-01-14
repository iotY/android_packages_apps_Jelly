package org.lineageos.jelly.tiles;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.service.quicksettings.TileService;

import org.lineageos.jelly.MainActivity;

@TargetApi(Build.VERSION_CODES.N)
public class KillAll extends TileService {

    @Override
    public void onClick() {
        Context context = getApplicationContext();
        MainActivity.Companion.handleShortcuts(context, "killall");
    }
}