package org.lineageos.jelly.tiles;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.TileService;

import org.lineageos.jelly.MainActivity;

@TargetApi(Build.VERSION_CODES.N)
public class HistoryTile extends TileService {

    @Override
    public void onClick() {
        Intent collapseIntent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        sendBroadcast(collapseIntent);
        Context context = getApplicationContext();
        MainActivity.Companion.handleShortcuts(context, "history");
    }
}