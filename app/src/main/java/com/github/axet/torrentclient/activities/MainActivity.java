package com.github.axet.torrentclient.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.getbase.floatingactionbutton.FloatingActionButton;
import com.getbase.floatingactionbutton.FloatingActionsMenu;
import com.github.axet.androidlibrary.animations.RemoveItemAnimation;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.androidlibrary.widgets.PopupShareActionProvider;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.dialogs.AddDialogFragment;
import com.github.axet.torrentclient.dialogs.CreateDialogFragment;
import com.github.axet.torrentclient.dialogs.OpenIntentDialogFragment;
import com.github.axet.torrentclient.dialogs.TorrentDialogFragment;
import com.github.axet.torrentclient.animations.RecordingAnimation;
import com.github.axet.torrentclient.app.MainApplication;
import com.github.axet.torrentclient.app.Storage;
import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import go.libtorrent.Libtorrent;

public class MainActivity extends AppCompatActivity implements AbsListView.OnScrollListener,
        DialogInterface.OnDismissListener, SharedPreferences.OnSharedPreferenceChangeListener,
        NavigationView.OnNavigationItemSelectedListener {
    public final static String TAG = MainActivity.class.getSimpleName();

    static final int TYPE_COLLAPSED = 0;
    static final int TYPE_EXPANDED = 1;
    static final int TYPE_DELETED = 2;

    static final long INFO_MANUAL_REFRESH = 5 * 1000;

    static final long INFO_AUTO_REFRESH = 5 * 60 * 1000;

    public final static String HIDE = "hide";

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;

    int scrollState;

    Runnable refresh;
    Runnable refreshUI;
    TorrentFragmentInterface dialog;

    Torrents torrents;
    ProgressBar progress;
    ListView list;
    View empty;
    Handler handler;
    PopupShareActionProvider shareProvider;

    NavigationView navigationView;
    DrawerLayout drawer;

    Thread infoThread;
    List<String> infoOld;
    boolean infoPort;
    long infoTime; // last time checked

    int themeId;

    // not delared locally - used from two places
    FloatingActionsMenu fab;
    FloatingActionButton create;
    FloatingActionButton add;

    // delayedIntent delayedIntent
    Intent delayedIntent;
    Thread initThread;
    Runnable delayedInit;

    BroadcastReceiver screenreceiver;

    public static void startActivity(Context context) {
        Intent i = new Intent(context, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(i);
    }

    public static class Tag {
        public int tag;
        public int position;

        public Tag(int t, int p) {
            this.tag = t;
            this.position = p;
        }

        public static boolean animate(View v, int s, int p) {
            if (v.getTag() == null)
                return true;
            if (animate(v, s))
                return true;
            return ((Tag) v.getTag()).position != p;
        }

        public static boolean animate(View v, int s) {
            if (v.getTag() == null)
                return false;
            return ((Tag) v.getTag()).tag == s;
        }

        public static void setTag(View v, int t, int p) {
            v.setTag(new Tag(t, p));
        }
    }

    public interface TorrentFragmentInterface {
        void update();
    }

    public class Torrents extends BaseAdapter {
        int selected = -1;
        Context context;

        public Torrents(Context context) {
            super();

            this.context = context;
        }

        public Context getContext() {
            return context;
        }

        public void update() {
            for (int i = 0; i < getCount(); i++) {
                Storage.Torrent t = getItem(i);
                if (Libtorrent.TorrentActive(t.t)) {
                    t.update();
                }
            }

            notifyDataSetChanged();
        }

        public void close() {
        }

        @Override
        public int getCount() {
            Storage s = getStorage();
            if (s == null) // happens on shutdown() from ListView
                return 0;
            return s.count();
        }

        @Override
        public Storage.Torrent getItem(int i) {
            return getStorage().torrent(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(final int position, View convertView, final ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(getContext());

            if (convertView == null) {
                convertView = inflater.inflate(R.layout.torrent, parent, false);
                convertView.setTag(null);
            }

            final View view = convertView;
            final View base = convertView.findViewById(R.id.recording_base);

            if (Tag.animate(convertView, TYPE_DELETED)) {
                RemoveItemAnimation.restore(base);
                convertView.setTag(null);
            }

            final Storage.Torrent t = (Storage.Torrent) getItem(position);

            TextView title = (TextView) convertView.findViewById(R.id.torrent_title);
            title.setText(t.name());

            TextView time = (TextView) convertView.findViewById(R.id.torrent_status);
            time.setText(t.status(getContext()));

            final View playerBase = convertView.findViewById(R.id.recording_player);
            // cover area, prevent click over to convertView
            playerBase.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                }
            });

            // we need runnable because we have View references
            final Runnable delete = new Runnable() {
                @Override
                public void run() {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle("Delete Torrent");

                    String name = Libtorrent.MetaTorrent(t.t) ? ".../" + t.name() : t.name();

                    builder.setMessage(name + "\n\n" + "Are you sure ? ");
                    if (Libtorrent.MetaTorrent(t.t)) {
                        builder.setNeutralButton("Delete With Data", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                                RemoveItemAnimation.apply(list, base, new Runnable() {
                                    @Override
                                    public void run() {
                                        t.stop();
                                        File f = new File(t.path, t.name());
                                        FileUtils.deleteQuietly(f);
                                        getStorage().remove(t);
                                        Tag.setTag(view, TYPE_DELETED, -1);
                                        select(-1);
                                    }
                                });
                            }
                        });
                    }
                    builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            RemoveItemAnimation.apply(list, base, new Runnable() {
                                @Override
                                public void run() {
                                    t.stop();
                                    getStorage().remove(t);
                                    Tag.setTag(view, TYPE_DELETED, -1);
                                    select(-1);
                                }
                            });
                        }
                    });
                    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
                    builder.show();
                }
            };

            View play = convertView.findViewById(R.id.torrent_play);
            play.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int s = Libtorrent.TorrentStatus(t.t);

                    if (s == Libtorrent.StatusChecking) {
                        Libtorrent.StopTorrent(t.t);
                        torrents.notifyDataSetChanged();
                        return;
                    }

                    if (s == Libtorrent.StatusQueued) {
                        // are we on wifi pause mode?
                        if (Libtorrent.Paused()) // drop torrent from queue
                            getStorage().stop(t);
                        else // nope, we are on library pause, start torrent
                            getStorage().start(t);
                        torrents.notifyDataSetChanged();
                        return;
                    }

                    if (s == Libtorrent.StatusPaused)
                        getStorage().start(t);
                    else
                        getStorage().stop(t);
                    torrents.notifyDataSetChanged();
                }
            });

            {
                // should be done using states, so animation will apply
                ProgressBar bar = (ProgressBar) convertView.findViewById(R.id.torrent_process);
                ImageView stateImage = (ImageView) convertView.findViewById(R.id.torrent_state_image);

                TextView tt = (TextView) convertView.findViewById(R.id.torrent_process_text);

                long p = t.getProgress();

                int color = 0;
                String text = "";

                Drawable d = null;
                switch (Libtorrent.TorrentStatus(t.t)) {
                    case Libtorrent.StatusChecking:
                        d = ContextCompat.getDrawable(getContext(), R.drawable.ic_pause_24dp);
                        color = Color.YELLOW;
                        text = p + "%";
                        break;
                    case Libtorrent.StatusPaused:
                        d = ContextCompat.getDrawable(getContext(), R.drawable.ic_pause_24dp);
                        color = ThemeUtils.getThemeColor(getContext(), R.attr.secondBackground);
                        text = p + "%";
                        break;
                    case Libtorrent.StatusQueued:
                        d = ContextCompat.getDrawable(getContext(), R.drawable.ic_pause_24dp);
                        color = Color.GREEN;
                        text = "Qued";
                        break;
                    case Libtorrent.StatusDownloading:
                        d = ContextCompat.getDrawable(getContext(), R.drawable.play);
                        color = ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent);
                        text = p + "%";
                        break;
                    case Libtorrent.StatusSeeding:
                        d = ContextCompat.getDrawable(getContext(), R.drawable.play);
                        color = ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent);
                        text = "Seed";
                        break;
                }
                PorterDuffColorFilter filter = new PorterDuffColorFilter(0x60000000 | (0xFFFFFF & color), PorterDuff.Mode.MULTIPLY);
                stateImage.setColorFilter(filter);
                stateImage.setImageDrawable(d);

                bar.getBackground().setColorFilter(filter);
                bar.getProgressDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
                bar.setProgress((int) p);

                tt.setText(text);
            }

            ImageView expand = (ImageView) convertView.findViewById(R.id.torrent_expand);

            if (selected == position) {
                if (Tag.animate(convertView, TYPE_COLLAPSED, position))
                    RecordingAnimation.apply(list, convertView, true, scrollState == SCROLL_STATE_IDLE && Tag.animate(convertView, TYPE_COLLAPSED));
                Tag.setTag(convertView, TYPE_EXPANDED, position);

                final View rename = convertView.findViewById(R.id.recording_player_rename);
                rename.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        //renameDialog(t);
                    }
                });

                final ImageView open = (ImageView) convertView.findViewById(R.id.recording_player_open);

                open.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openFolder(new File(t.path));
                    }
                });

                KeyguardManager myKM = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                if (myKM.inKeyguardRestrictedInputMode()) {
                    open.setColorFilter(Color.GRAY);
                    open.setOnClickListener(null);
                } else {
                    open.setColorFilter(ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent));
                }

                final View share = convertView.findViewById(R.id.recording_player_share);
                share.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        shareProvider = new PopupShareActionProvider(getContext(), share);

                        Intent emailIntent = new Intent(Intent.ACTION_SEND);
                        emailIntent.setType("text/plain");
                        emailIntent.putExtra(Intent.EXTRA_EMAIL, "");
                        emailIntent.putExtra(Intent.EXTRA_SUBJECT, Libtorrent.TorrentName(t.t));
                        emailIntent.putExtra(Intent.EXTRA_TEXT, Libtorrent.TorrentMagnet(t.t));

                        shareProvider.setShareIntent(emailIntent);

                        shareProvider.show();
                    }
                });

                View trash = convertView.findViewById(R.id.recording_player_trash);
                trash.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        delete.run();
                    }
                });

                expand.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_expand_less_black_24dp));
                expand.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        select(-1);
                    }
                });
            } else {
                if (Tag.animate(convertView, TYPE_EXPANDED, position))
                    RecordingAnimation.apply(list, convertView, false, scrollState == SCROLL_STATE_IDLE && Tag.animate(convertView, TYPE_EXPANDED));
                Tag.setTag(convertView, TYPE_COLLAPSED, position);

                expand.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_expand_more_black_24dp));
                expand.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        select(position);
                    }
                });
            }

            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showDetails(t.t);
                }
            });

            convertView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    PopupMenu popup = new PopupMenu(getContext(), v);
                    MenuInflater inflater = popup.getMenuInflater();
                    inflater.inflate(R.menu.menu_context, popup.getMenu());
                    popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            if (item.getItemId() == R.id.action_delete) {
                                delete.run();
                                return true;
                            }
                            if (item.getItemId() == R.id.action_rename) {
                                //renameDialog(t);
                                return true;
                            }
                            return false;
                        }
                    });
                    popup.show();
                    return true;
                }
            });

            return convertView;
        }

        public void select(int pos) {
            selected = pos;
            notifyDataSetChanged();
        }
    }

    void showDetails(Long f) {
        TorrentDialogFragment d = TorrentDialogFragment.create(f);
        dialog = d;
        d.show(getSupportFragmentManager(), "");
    }

    void renameDialog(final Long f) {
        final OpenFileDialog.EditTextDialog e = new OpenFileDialog.EditTextDialog(this);
        e.setTitle("Rename Torrent");
        e.setText(Libtorrent.TorrentName(f));
        e.setPositiveButton(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Libtorrent.TorrentFileRename(f, 0, e.getText());
            }
        });
        e.show();
    }

    public MainApplication getApp() {
        return (MainApplication) getApplication();
    }

    public void setAppTheme(int id) {
        super.setTheme(id);

        themeId = id;
    }

    int getAppTheme() {
        return MainApplication.getTheme(this, R.style.AppThemeLight_NoActionBar, R.style.AppThemeDark_NoActionBar);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        setAppTheme(getAppTheme());

        setContentView(R.layout.activity_main);

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setBackground(new ColorDrawable(MainApplication.getActionbarColor(this)));
        setSupportActionBar(toolbar);

        drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        TextView ver = (TextView) navigationView.findViewById(R.id.nav_version);
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = pInfo.versionName;
            ver.setText(version);
        } catch (PackageManager.NameNotFoundException e) {
            ver.setVisibility(View.GONE);
        }

        handler = new Handler();

        fab = (FloatingActionsMenu) findViewById(R.id.fab);

        create = (FloatingActionButton) findViewById(R.id.torrent_create_button);
        create.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final OpenFileDialog f = new OpenFileDialog(MainActivity.this);

                String path = "";

                final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

                if (path == null || path.isEmpty()) {
                    path = shared.getString(MainApplication.PREFERENCE_LAST_PATH, Environment.getExternalStorageDirectory().getPath());
                }

                f.setCurrentPath(new File(path));
                f.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        File p = f.getCurrentPath();

                        shared.edit().putString(MainApplication.PREFERENCE_LAST_PATH, p.getParent()).commit();

                        File pp = p.getParentFile();
                        long t = Libtorrent.CreateTorrent(p.getPath());
                        if (t == -1) {
                            Error(Libtorrent.Error());
                            return;
                        }
                        if (shared.getBoolean(MainApplication.PREFERENCE_DIALOG, false)) {
                            createTorrentDialog(t, pp.getPath());
                        } else {
                            getStorage().add(new Storage.Torrent(t, pp.getPath()));
                            torrents.notifyDataSetChanged();
                        }
                    }
                });
                f.show();
                fab.collapse();
            }
        });

        add = (FloatingActionButton) findViewById(R.id.torrent_add_button);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final OpenFileDialog f = new OpenFileDialog(MainActivity.this);

                String path = "";

                final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

                if (path == null || path.isEmpty()) {
                    path = shared.getString(MainApplication.PREFERENCE_LAST_PATH, Environment.getExternalStorageDirectory().getPath());
                }

                f.setCurrentPath(new File(path));
                f.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        File p = f.getCurrentPath();

                        shared.edit().putString(MainApplication.PREFERENCE_LAST_PATH, p.getParent()).commit();

                        long t = Libtorrent.AddTorrent(p.getPath());

                        if (t == -1) {
                            Error(Libtorrent.Error());
                            return;
                        }

                        addTorrentDialog(t, p.getParent());
                    }
                });
                f.show();
                fab.collapse();
            }
        });

        FloatingActionButton magnet = (FloatingActionButton) findViewById(R.id.torrent_magnet_button);
        magnet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final OpenFileDialog.EditTextDialog f = new OpenFileDialog.EditTextDialog(MainActivity.this);
                f.setTitle("Add Magnet");
                f.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String ff = f.getText();
                        addMagnet(ff);
                    }
                });
                f.show();
                fab.collapse();
            }
        });

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        progress = (ProgressBar) findViewById(R.id.progress);

        list = (ListView) findViewById(R.id.list);
        empty = findViewById(R.id.empty_list);

        fab.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
        list.setVisibility(View.GONE);
        fab.setVisibility(View.GONE);

        screenreceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                    Log.d(TAG, "Screen OFF");
                    if (drawer.isDrawerOpen(GravityCompat.START)) {
                        drawer.closeDrawer(GravityCompat.START);
                    }
                    moveTaskToBack(true);
                }
            }
        };
        IntentFilter screenfilter = new IntentFilter();
        screenfilter.addAction(Intent.ACTION_SCREEN_ON);
        screenfilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenreceiver, screenfilter);

        delayedIntent = getIntent();

        delayedInit = new Runnable() {
            @Override
            public void run() {
                progress.setVisibility(View.GONE);
                list.setVisibility(View.VISIBLE);
                fab.setVisibility(View.VISIBLE);

                invalidateOptionsMenu();

                list.setOnScrollListener(MainActivity.this);
                list.setEmptyView(findViewById(R.id.empty_list));

                final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                shared.registerOnSharedPreferenceChangeListener(MainActivity.this);

                torrents = new Torrents(MainActivity.this);

                list.setAdapter(torrents);

                if (permitted()) {
                    try {
                        getStorage().migrateLocalStorage();
                    } catch (RuntimeException e) {
                        Error(e.getMessage());
                    }
                } else {
                    // with no permission we can't choise files to 'torrent', or select downloaded torrent
                    // file, since we have no persmission to user files.
                    create.setVisibility(View.GONE);
                    add.setVisibility(View.GONE);
                }

                if (delayedIntent != null) {
                    openIntent(delayedIntent);
                    delayedIntent = null;
                }
            }
        };

        updateHeader(new Storage(this));

        initThread = new Thread(new Runnable() {
            @Override
            public void run() {
                getApp().create();

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        // acctivity can be destoryed already do not init
                        if (delayedInit != null) {
                            delayedInit.run();
                            delayedInit = null;
                        }
                    }
                });
            }
        });
        initThread.start();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(MainApplication.PREFERENCE_ANNOUNCE)) {
            Libtorrent.SetDefaultAnnouncesList(sharedPreferences.getString(MainApplication.PREFERENCE_ANNOUNCE, ""));
        }
        if (key.equals(MainApplication.PREFERENCE_WIFI)) {
            if (!sharedPreferences.getBoolean(MainApplication.PREFERENCE_WIFI, true))
                getStorage().resume(); // wifi only disabled
            else {
                // wifi only enabed
                if (!getStorage().isConnectedWifi()) // are we on wifi?
                    getStorage().pause(); // no, pause all
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.menu_main, menu);

        KeyguardManager myKM = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (myKM.inKeyguardRestrictedInputMode() || delayedInit != null) {
            menu.removeItem(R.id.action_settings);
            menu.removeItem(R.id.action_show_folder);
        }

        return true;
    }

    public void close() {
        if (initThread != null) {
            try {
                initThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // prevent delayed delayedInit
            delayedInit = null;
        }

        refreshUI = null;

        if (refresh != null) {
            handler.removeCallbacks(refresh);
            refresh = null;
        }

        if (torrents != null) {
            torrents.close();
            torrents = null;
        }

        Storage s = getStorage();
        if (s != null)
            s.save();

        if (screenreceiver != null) {
            unregisterReceiver(screenreceiver);
            screenreceiver = null;
        }

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        shared.unregisterOnSharedPreferenceChangeListener(MainActivity.this);

        // do not close storage when mainactivity closes. it may be restarted due to theme change.
        // only close it on shutdown()
        // getApp().close();
    }

    public void shutdown() {
        close();
        getApp().close();
        finishAffinity();
        ExitActivity.exitApplication(this);
    }

    public void Error(String err) {
        Log.e(TAG, Libtorrent.Error());

        new AlertDialog.Builder(MainActivity.this)
                .setTitle("Error")
                .setMessage(err)
                .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    public void Fatal(String err) {
        Log.e(TAG, Libtorrent.Error());

        new AlertDialog.Builder(this)
                .setTitle("Fatal")
                .setMessage(err)
                .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        shutdown();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        shutdown();
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar base clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        if (id == R.id.action_shutdown) {
            shutdown();
            return true;
        }

        if (id == R.id.action_show_folder) {
            openFolder(getStorage().getStoragePath());
        }

        return super.onOptionsItemSelected(item);
    }

    public void openFolder(File path) {
        Uri selectedUri = Uri.fromFile(path);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(selectedUri, "resource/folder");
        if (intent.resolveActivityInfo(getPackageManager(), 0) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(this, "No folder view application installed", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");

        invalidateOptionsMenu();

        KeyguardManager myKM = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (myKM.inKeyguardRestrictedInputMode()) {
            add.setVisibility(View.GONE);
            create.setVisibility(View.GONE);
        } else {
            if (permitted(PERMISSIONS)) {
                add.setVisibility(View.VISIBLE);
                create.setVisibility(View.VISIBLE);
            }
        }

        if (themeId != getAppTheme()) {
            finish();
            MainActivity.startActivity(this);
            return;
        }

        refreshUI = new Runnable() {
            @Override
            public void run() {
                torrents.notifyDataSetChanged();

                if (dialog != null)
                    dialog.update();
            }
        };

        refresh = new Runnable() {
            @Override
            public void run() {
                handler.removeCallbacks(refresh);
                handler.postDelayed(refresh, 1000);

                if (delayedInit != null)
                    return;

                Storage s = getStorage();

                if (s == null) { // sholud never happens, unless if onResume called after shutdown()
                    return;
                }

                s.update();
                updateHeader(s);

                torrents.update();

                if (refreshUI != null)
                    refreshUI.run();
            }
        };
        refresh.run();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");

        refreshUI = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1:
                if (permitted(permissions)) {
                    try {
                        getStorage().migrateLocalStorage();
                    } catch (RuntimeException e) {
                        Error(e.getMessage());
                    }
                    create.setVisibility(View.VISIBLE);
                    add.setVisibility(View.VISIBLE);
                } else {
                    Toast.makeText(this, "Not permitted", Toast.LENGTH_SHORT).show();
                }
        }
    }

    public static final String[] PERMISSIONS = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    boolean permitted(String[] ss) {
        for (String s : ss) {
            if (ContextCompat.checkSelfPermission(this, s) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    boolean permitted() {
        for (String s : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, s) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, PERMISSIONS, 1);
                return false;
            }
        }
        return true;
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        this.scrollState = scrollState;
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            moveTaskToBack(true);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (delayedInit == null)
                    list.smoothScrollToPosition(torrents.selected);
            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestory");

        close();
    }

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app deep link URI is correct.
                Uri.parse("android-app://com.github.axet.torrentclient/http/host/path")
        );
        AppIndex.AppIndexApi.start(client, viewAction);
    }


    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app deep link URI is correct.
                Uri.parse("android-app://com.github.axet.torrentclient/http/host/path")
        );
        AppIndex.AppIndexApi.end(client, viewAction);
        client.disconnect();
    }

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
        dialog = null;
        torrents.notifyDataSetChanged();
    }

    void updateHeader(Storage s) {
        TextView text = (TextView) findViewById(R.id.space_left);
        text.setText(s.formatHeader());

        ArrayList<String> info = new ArrayList<>();
        long c = Libtorrent.PortCount();
        for (long i = 0; i < c; i++) {
            info.add(Libtorrent.Port(i));
        }

        String str = "";
        for (String ip : info) {
            str += ip + "\n";
        }
        str = str.trim();
        TextView textView = (TextView) navigationView.findViewById(R.id.torrent_ip);
        textView.setText(str);

        View portButton = navigationView.findViewById(R.id.torrent_port_button);
        ImageView portIcon = (ImageView) navigationView.findViewById(R.id.torrent_port_icon);
        TextView port = (TextView) navigationView.findViewById(R.id.torrent_port_text);

        portButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long time = System.currentTimeMillis();
                if (infoTime + INFO_MANUAL_REFRESH < time) {
                    infoTime = time;
                    infoOld = null;
                }
            }
        });

        long time = System.currentTimeMillis();
        if (infoTime + INFO_AUTO_REFRESH < time) {
            infoTime = time;
            infoOld = null;
        }

        if (infoOld == null || !Arrays.equals(info.toArray(), infoOld.toArray())) {
            if (drawer.isDrawerOpen(GravityCompat.START)) { // only probe port when drawer is open
                if (infoThread != null) {
                    return;
                }
                infoOld = info;
                infoThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final boolean b = Libtorrent.PortCheck();
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                infoPort = b;
                                infoThread = null;
                            }
                        });
                    }
                });
                infoThread.start();
                infoPort = false;
                portIcon.setImageResource(R.drawable.port_no);
                port.setText("Port checking...");
            } else {
                portIcon.setImageResource(R.drawable.port_no);
                port.setText("Port Closed");
            }
        } else {
            if (infoPort) {
                portIcon.setImageResource(R.drawable.port_ok);
                port.setText("Port Open");
            } else {
                portIcon.setImageResource(R.drawable.port_no);
                port.setText("Port Closed");
            }
        }
    }

    public Storage getStorage() {
        return getApp().getStorage();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (delayedInit == null)
            openIntent(intent);
        else
            this.delayedIntent = intent;
    }

    void openIntent(Intent intent) {
        if (intent == null)
            return;

        Uri openUri = intent.getData();
        if (openUri == null)
            return;

        OpenIntentDialogFragment dialog = new OpenIntentDialogFragment();

        Bundle args = new Bundle();
        args.putString("url", openUri.toString());

        dialog.setArguments(args);
        dialog.show(getSupportFragmentManager(), "");
    }

    public void addMagnet(String ff) {
        try {
            final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
            List<String> m = getStorage().splitMagnets(ff);
            if (shared.getBoolean(MainApplication.PREFERENCE_DIALOG, false) && m.size() == 1) {
                String s = m.get(0);

                String p = getStorage().getStoragePath().getPath();
                long t = Libtorrent.AddMagnet(p, s);
                if (t == -1) {
                    throw new RuntimeException(Libtorrent.Error());
                }

                addTorrentDialog(t, p);
            } else {
                for (String s : m) {
                    getStorage().addMagnet(s);
                }
            }
        } catch (RuntimeException e) {
            Error(e.getMessage());
        }
        torrents.notifyDataSetChanged();
    }

    public void addTorrentFromURL(String p) {
        try {
            getStorage().addTorrentFromURL(p);
        } catch (RuntimeException e) {
            Error(e.getMessage());
        }
        torrents.notifyDataSetChanged();
    }

    public void addTorrentFromBytes(byte[] buf) {
        try {
            final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
            if (shared.getBoolean(MainApplication.PREFERENCE_DIALOG, false)) {
                String s = getStorage().getStoragePath().getPath();
                long t = Libtorrent.AddTorrentFromBytes(s, buf);
                if (t == -1) {
                    throw new RuntimeException(Libtorrent.Error());
                }
                addTorrentDialog(t, s);
            } else {
                getStorage().addTorrentFromBytes(buf);
            }
        } catch (RuntimeException e) {
            Error(e.getMessage());
        }
        torrents.notifyDataSetChanged();
    }

    void addTorrentDialog(long t, String path) {
        AddDialogFragment fragment = new AddDialogFragment();

        dialog = fragment;

        Bundle args = new Bundle();
        args.putLong("torrent", t);
        args.putString("path", path);

        fragment.setArguments(args);

        fragment.show(getSupportFragmentManager(), "");
    }

    void createTorrentDialog(long t, String path) {
        CreateDialogFragment fragment = new CreateDialogFragment();

        dialog = fragment;

        Bundle args = new Bundle();
        args.putLong("torrent", t);
        args.putString("path", path);

        fragment.setArguments(args);

        fragment.show(getSupportFragmentManager(), "");
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        drawer.closeDrawer(GravityCompat.START);
        return true;
    }
}
