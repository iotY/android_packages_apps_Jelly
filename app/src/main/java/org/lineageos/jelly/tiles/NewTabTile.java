package org.lineageos.jelly.tiles;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.TileService;

import org.lineageos.jelly.MainActivity;

@TargetApi(Build.VERSION_CODES.N)
public class NewTabTile extends TileService {

    @Override
    public void onClick() {
        startActivityAndCollapse(new Intent(getApplicationContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}