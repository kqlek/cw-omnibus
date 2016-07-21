/***
 * Copyright (c) 2008-2012 CommonsWare, LLC
 * Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not
 * use this file except in compliance with the License. You may
 * obtain	a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0.
 * Unless required
 * by applicable law or agreed to in writing, software
 * distributed under the
 * License is distributed on an "AS IS" BASIS,	WITHOUT	WARRANTIES
 * OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for
 * the specific
 * language governing permissions and limitations under the
 * License.
 * <p/>
 * From _The Busy Coder's Guide to Android Development_
 * https://commonsware.com/Android
 */

package com.commonsware.android.downloader;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.FileProvider;
import android.util.Log;
import com.commonsware.cwac.netsecurity.TrustManagerBuilder;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class Downloader extends IntentService {
  private static final String AUTHORITY=
    BuildConfig.APPLICATION_ID+".provider";
  private static int NOTIFY_ID=1337;
  private static int FOREGROUND_ID=1338;

  public Downloader() {
    super("Downloader");
  }

  @Override
  public void onHandleIntent(Intent i) {
    File output=new File(getFilesDir(),
      i.getData().getLastPathSegment());

    startForeground(FOREGROUND_ID,
      buildForegroundNotification(output.getName()));

    try {
      if (output.exists()) {
        output.delete();
      }

      URL url=new URL(i.getData().toString());
      HttpURLConnection c=
        (HttpURLConnection)url.openConnection();
      TrustManagerBuilder tmb=
        new TrustManagerBuilder().withManifestConfig(this);

      tmb.build().setHost(url.getHost());
      tmb.applyTo(c);

      FileOutputStream fos=
        new FileOutputStream(output.getPath());
      BufferedOutputStream out=new BufferedOutputStream(fos);
      String mimeType=c.getHeaderField("Content-type");

      try {
        InputStream in=c.getInputStream();
        byte[] buffer=new byte[8192];
        int len=0;

        while ((len=in.read(buffer))>=0) {
          out.write(buffer, 0, len);
        }

        out.flush();
      }
      finally {
        fos.getFD().sync();
        out.close();
        c.disconnect();
      }

      raiseNotification(mimeType, output, null);
    }
    catch (Exception e2) {
      Log.e(getClass().getSimpleName(),
        "Exception downloading file", e2);
      raiseNotification(null, null, e2);
    }
    finally {
      stopForeground(true);
    }
  }

  private void raiseNotification(String mimeType, File output,
                                 Exception e) {
    NotificationCompat.Builder b=
      new NotificationCompat.Builder(this);

    b.setAutoCancel(true).setDefaults(Notification.DEFAULT_ALL);

    if (e==null) {
      b.setContentTitle(getString(R.string.download_complete))
        .setContentText(getString(R.string.fun))
        .setSmallIcon(android.R.drawable.stat_sys_download_done)
        .setTicker(getString(R.string.download_complete));

      Intent outbound=new Intent(Intent.ACTION_VIEW);
      Uri doc=
        FileProvider.getUriForFile(this, AUTHORITY, output);

      outbound.setDataAndType(doc, mimeType);
      outbound.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

      b.setContentIntent(
        PendingIntent.getActivity(this, 0, outbound, 0));
    }
    else {
      b.setContentTitle(getString(R.string.exception))
        .setContentText(e.getMessage())
        .setSmallIcon(android.R.drawable.stat_notify_error)
        .setTicker(getString(R.string.exception));
    }

    NotificationManager mgr=
      (NotificationManager)getSystemService(
        NOTIFICATION_SERVICE);

    mgr.notify(NOTIFY_ID, b.build());
  }

  private Notification buildForegroundNotification(String filename) {
    NotificationCompat.Builder b=new NotificationCompat.Builder(this);

    b.setOngoing(true);

    b.setContentTitle(getString(R.string.downloading))
      .setContentText(filename)
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setTicker(getString(R.string.downloading));

    return(b.build());
  }
}
