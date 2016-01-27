package org.tme.unstablecursorloader;

import android.content.ContentProviderClient;
import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.os.RemoteException;
import android.os.SystemClock;

/**
 * A CursorLoader implementation that uses an unstable ContentProviderClient. For use when you do
 * not trust the stability of the target ContentProvider.  This turns off the mechanism in the platform clean up
 * that kills processes bound to a stable ContentProvider that has died.  So when using this CursorLoader your app
 * will not be killed if the ContentProvider process dies, and will reattempt to connect to it gracefully.
 *
 * @author Tamer El Calamawy
 */
public class UnstableCursorLoader extends CursorLoader {

  private static final int MAX_RETRY_ATTEMPTS = 5;
  private static final long RETRY_DELAY_MS = 300;
  final ForceLoadContentObserver mObserver;

  CancellationSignal mCancellationSignal;
  ContentProviderClient mContentProviderClient;

  /**
   * Creates an empty unspecified CursorLoader.  You must follow this with
   * calls to {@link #setUri(android.net.Uri)}, {@link #setSelection(String)}, etc
   * to specify the query to perform.
   */
  public UnstableCursorLoader(Context context) {
    super(context);
    mObserver = new ForceLoadContentObserver();
  }

  /**
   * Creates a fully-specified CursorLoader.  See
   * {@link android.content.ContentResolver#query(android.net.Uri, String[], String, String[], String)
   * ContentResolver.query()} for documentation on the meaning of the
   * parameters.  These will be passed as-is to that call.
   */
  public UnstableCursorLoader(Context context, Uri uri, String[] projection, String selection,
                              String[] selectionArgs, String sortOrder) {
    super(context, uri, projection, selection, selectionArgs, sortOrder);
    mObserver = new ForceLoadContentObserver();
  }

  @Override
  public Cursor loadInBackground() {
    synchronized (this) {
      if (isLoadInBackgroundCanceled()) {
        throw new OperationCanceledException();
      }
      mCancellationSignal = new CancellationSignal();
    }

    Cursor cursor = null;
    int retryCount = 0;
    boolean result = false;

    while (!result && retryCount < MAX_RETRY_ATTEMPTS) {
      try {
        retryCount++;
        if (mContentProviderClient == null) {
          mContentProviderClient = getContext().getContentResolver().acquireUnstableContentProviderClient(getUri());
          if (mContentProviderClient == null) {
            SystemClock.sleep(RETRY_DELAY_MS);
            continue;
          }
        }
        cursor = mContentProviderClient.query(getUri(), getProjection(), getSelection(),
            getSelectionArgs(), getSortOrder(), mCancellationSignal);
        if (cursor != null) {
          // Ensure the cursor window is filled
          cursor.getCount();
          cursor.registerContentObserver(mObserver);
        }
        result = true;
      } catch (RemoteException e) {
        // ContentProvider has gone away. The ContentProviderClient is now invalid, so release it and acquire a new one
        // to try to restart the provider and perform new operations on it.
        mContentProviderClient.release();
        mContentProviderClient = null;
        SystemClock.sleep(RETRY_DELAY_MS);
      } catch (Exception e) {
        e.printStackTrace();
        break;
      }
    }
    synchronized (this) {
      mCancellationSignal = null;
    }
    return cursor;
  }

  @Override
  public void cancelLoadInBackground() {
    super.cancelLoadInBackground();

    synchronized (this) {
      if (mCancellationSignal != null) {
        mCancellationSignal.cancel();
      }
    }
  }

  @Override
  protected void onReset() {
    super.onReset();

    if (mContentProviderClient != null) {
      mContentProviderClient.release();
      mContentProviderClient = null;
    }
  }
}
