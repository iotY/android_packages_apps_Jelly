package org.lineageos.jelly.tiles;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.TileService;

import org.lineageos.jelly.favorite.FavoriteActivity;

@TargetApi(Build.VERSION_CODES.N)
public class FavoritesTile extends TileService {

    @Override
    public void onClick() {
        startActivityAndCollapse(new Intent(getApplicationContext(), FavoriteActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}