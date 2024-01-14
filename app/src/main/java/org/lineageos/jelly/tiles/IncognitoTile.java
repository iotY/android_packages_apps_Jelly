package org.lineageos.jelly.tiles;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.TileService;

import org.lineageos.jelly.MainActivity;
import org.lineageos.jelly.utils.IntentUtils;

@TargetApi(Build.VERSION_CODES.N)
public class IncognitoTile extends TileService {

    @Override
    public void onClick() {
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.putExtra(IntentUtils.EXTRA_INCOGNITO, true);
        startActivityAndCollapse(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}