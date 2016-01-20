# UnstableCursorLoader
An Android CursorLoader implementation that uses an unstable ContentProviderClient. For use when you do not trust the stability of the target ContentProvider.  This turns off the mechanism in the platform clean up that kills processes bound to a stable ContentProvider that has died.  When using this CursorLoader your app will not be killed if the ContentProvider process dies, and will reattempt to connect to it gracefully.

http://developer.android.com/reference/android/content/CursorLoader.html
http://developer.android.com/reference/android/content/ContentResolver.html#acquireUnstableContentProviderClient(android.net.Uri)
