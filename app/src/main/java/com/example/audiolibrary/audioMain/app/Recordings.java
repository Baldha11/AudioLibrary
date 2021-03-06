package com.example.audiolibrary.audioMain.app;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.audiolibrary.R;
import com.example.audiolibrary.androidlibrary.animations.ExpandItemAnimator;
import com.example.audiolibrary.androidlibrary.app.AssetsDexLoader;
import com.example.audiolibrary.androidlibrary.app.ProximityShader;
import com.example.audiolibrary.androidlibrary.services.StorageProvider;
import com.example.audiolibrary.androidlibrary.sound.MediaPlayerCompat;
import com.example.audiolibrary.androidlibrary.sound.ProximityPlayer;
import com.example.audiolibrary.androidlibrary.widgets.CacheImagesAdapter;
import com.example.audiolibrary.androidlibrary.widgets.CacheImagesRecyclerAdapter;
import com.example.audiolibrary.androidlibrary.widgets.HeaderRecyclerAdapter;
import com.example.audiolibrary.androidlibrary.widgets.OpenFileDialog;
import com.example.audiolibrary.androidlibrary.widgets.PopupShareActionProvider;
import com.example.audiolibrary.androidlibrary.widgets.ThemeUtils;
import com.example.audiolibrary.audioMain.animations.RecordingAnimation;
import com.example.audiolibrary.audioMain.encoders.Factory;
import com.example.audiolibrary.audioMain.widgets.MoodbarView;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class Recordings extends CacheImagesRecyclerAdapter<Recordings.RecordingHolder> implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static String TAG = Recordings.class.getSimpleName();

    protected Handler handler;
    protected Context context;
    protected Storage storage;
    protected MediaPlayerCompat player;
    protected ProximityShader proximity;
    protected Runnable updatePlayer;
    protected int selected = -1;
    protected Thread thread;
    protected String filter;

    protected ViewGroup toolbar;
    protected View toolbar_a;
    protected View toolbar_s;
    protected boolean toolbarFilterAll = true; // all or stars

    protected PhoneStateChangeListener pscl;

    protected Map<Uri, Storage.RecordingStats> cache = new ConcurrentHashMap<>();

    protected ArrayList<Storage.RecordingUri> items = new ArrayList<>();

    public ExpandItemAnimator animator;
    public HeaderRecyclerAdapter empty = new HeaderRecyclerAdapter(this);

    protected BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive()");
            String a = intent.getAction();
            if (a.equals(Intent.ACTION_MEDIA_EJECT))
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        load(false, null);
                    }
                });
            if (a.equals(Intent.ACTION_MEDIA_MOUNTED))
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        load(false, null);
                    }
                });
            if (a.equals(Intent.ACTION_MEDIA_UNMOUNTED))
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        load(false, null);
                    }
                });
        }
    };

    public static long getDuration(final Context context, final Uri u) {

        final Object lock = new Object();
        final AtomicLong duration = new AtomicLong();
        final MediaPlayerCompat mp = MediaPlayerCompat.create(context, u);
        if (mp == null)
            return 0;
        mp.addListener(new MediaPlayerCompat.Listener() {
            @Override
            public void onReady() {
                synchronized (lock) {
                    duration.set(mp.getDuration());
                    lock.notifyAll();
                }
            }

            @Override
            public void onEnd() {
                synchronized (lock) {
                    lock.notifyAll();
                }
            }

            @Override
            public void onError(Exception e) {
                synchronized (lock) {
                    lock.notifyAll();
                }
            }
        });
        try {
            synchronized (lock) {
                mp.prepare();
                duration.set(mp.getDuration());
                if (duration.longValue() == 0)
                    lock.wait();
            }
            mp.release();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return duration.longValue();
    }

    public static File getCover(Context context, Storage.RecordingUri f) {
        return CacheImagesAdapter.cacheUri(context, f.uri);
    }

    public static Storage.RecordingStats getFileStats(Map<String, ?> prefs, Uri f) {
        String json = (String) prefs.get(MainApplication.getFilePref(f) + MainApplication.PREFERENCE_DETAILS_FS);
        if (json != null && !json.isEmpty())
            return new Storage.RecordingStats(json);
        return null;
    }

    public static void setFileStats(Context context, Uri f, Storage.RecordingStats fs) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String p = MainApplication.getFilePref(f) + MainApplication.PREFERENCE_DETAILS_FS;
        SharedPreferences.Editor editor = shared.edit();
        editor.putString(p, fs.save().toString());
        editor.commit();
    }

    public static class ExoLoader extends AssetsDexLoader.JsonThreadLoader {
        public static final Object lock = new Object();

        public ExoLoader(Context context, boolean block) {
            super(context, block, "exoplayer");
        }

        @Override
        public boolean need() {
            synchronized (lock) {
                return Build.VERSION.SDK_INT >= 14 && MediaPlayerCompat.classLoader == MediaPlayerCompat.class.getClassLoader();
            }
        }

        @Override
        public void done(ClassLoader l) {
            synchronized (lock) {
                MediaPlayerCompat.classLoader = l;
            }
        }
    }

    public static class SortByName implements Comparator<Storage.RecordingUri> {
        @Override
        public int compare(Storage.RecordingUri file, Storage.RecordingUri file2) {
            return file.name.compareTo(file2.name);
        }
    }

    public static class SortByDate implements Comparator<Storage.RecordingUri> {
        @Override
        public int compare(Storage.RecordingUri file, Storage.RecordingUri file2) {
            return Long.valueOf(file.last).compareTo(file2.last);
        }
    }

    public static class RecordingHolder extends RecyclerView.ViewHolder {
        public View base;
        public ImageView star;
        public TextView title;
        public TextView time;
        public TextView dur;
        public TextView size;
        public View playerBase;
        public ImageView play;
        public TextView start;
        public SeekBar bar;
        public TextView end;
        public View edit;
        public View share;
        public ImageView trash;
        public MoodbarView moodbar;
        public ProgressBar moodbarProgress;

        public RecordingHolder(View v) {
            super(v);
            base = v.findViewById(R.id.recording_base);
            star = (ImageView) v.findViewById(R.id.recording_star);
            title = (TextView) v.findViewById(R.id.recording_title);
            time = (TextView) v.findViewById(R.id.recording_time);
            dur = (TextView) v.findViewById(R.id.recording_duration);
            size = (TextView) v.findViewById(R.id.recording_size);
            playerBase = v.findViewById(R.id.recording_player);
            play = (ImageView) v.findViewById(R.id.recording_player_play);
            start = (TextView) v.findViewById(R.id.recording_player_start);
            bar = (SeekBar) v.findViewById(R.id.recording_player_seek);
            end = (TextView) v.findViewById(R.id.recording_player_end);
            edit = v.findViewById(R.id.recording_player_edit);
            share = v.findViewById(R.id.recording_player_share);
            trash = (ImageView) v.findViewById(R.id.recording_player_trash);
            moodbar = (MoodbarView) v.findViewById(R.id.moodbar);
            moodbarProgress = (ProgressBar) v.findViewById(R.id.moodbar_progress);
        }
    }

    class PhoneStateChangeListener extends PhoneStateListener {
        public boolean wasRinging;
        public boolean pausedByCall;

        public RecordingHolder h;
        public Storage.RecordingUri f;

        public PhoneStateChangeListener(RecordingHolder h, final Storage.RecordingUri f) {
            this.h = h;
            this.f = f;
        }

        @Override
        public void onCallStateChanged(int s, String incomingNumber) {
            switch (s) {
                case TelephonyManager.CALL_STATE_RINGING:
                    wasRinging = true;
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    wasRinging = true;
                    if (player != null && player.getPlayWhenReady()) {
                        playerPause(h, f);
                        pausedByCall = true;
                    }
                    break;
                case TelephonyManager.CALL_STATE_IDLE:
                    if (pausedByCall) {
                        if (player != null && !player.getPlayWhenReady())
                            playerPause(h, f);
                    }
                    wasRinging = false;
                    pausedByCall = false;
                    break;
            }
        }
    }

    public Recordings(Context context, final RecyclerView list) {
        super(context);
        this.context = context;
        this.handler = new Handler();
        this.storage = new Storage(context);
        this.animator = new ExpandItemAnimator() {
            @Override
            public Animation apply(RecyclerView.ViewHolder h, boolean animate) {
                if (selected == h.getAdapterPosition())
                    return RecordingAnimation.apply(list, h.itemView, true, animate);
                else
                    return RecordingAnimation.apply(list, h.itemView, false, animate);
            }
        };
        list.setItemAnimator(animator);
        list.addOnScrollListener(animator.onScrollListener);
        load();
        IntentFilter ff = new IntentFilter();
        ff.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        ff.addAction(Intent.ACTION_MEDIA_MOUNTED);
        ff.addAction(Intent.ACTION_MEDIA_EJECT);
        context.registerReceiver(receiver, ff);
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        shared.registerOnSharedPreferenceChangeListener(this);
    }

    // true - include
    protected boolean filter(Storage.RecordingUri f) {
        if (filter != null) {
            if (!f.name.toLowerCase().contains(filter))
                return false;
        }
        if (toolbarFilterAll)
            return true;
        if (MainApplication.getStar(context, f.uri))
            return true;
        else
            return false;
    }

    public void scan(final List<Storage.Node> nn, final boolean clean, final Runnable done) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        final Map<String, ?> prefs = shared.getAll();

        final Thread old = thread;

        thread = new Thread("Recordings Scan") {
            @Override
            public void run() {
                if (old != null) {
                    old.interrupt();
                    try {
                        old.join();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                try {
                    new ExoLoader(context, true);
                } catch (Exception e) {
                    Log.e(TAG, "error", e);
                }
                final Thread t = Thread.currentThread();
                final ArrayList<Storage.RecordingUri> all = new ArrayList<>();
                for (Storage.Node n : nn) {
                    if (t.isInterrupted())
                        return;
                    Storage.RecordingStats fs = cache.get(n.uri);
                    if (fs == null) {
                        fs = getFileStats(prefs, n.uri);
                        if (fs != null)
                            cache.put(n.uri, fs);
                    }
                    if (fs != null) {
                        if (n.last != fs.last || n.size != fs.size)
                            fs = null;
                    }
                    if (fs == null) {
                        fs = new Storage.RecordingStats();
                        fs.size = n.size;
                        fs.last = n.last;
                        try {
                            fs.duration = getDuration(context, n.uri);
                            if (t.isInterrupted()) // getDuration can be interrupted and value is invalid
                                return;
                            cache.put(n.uri, fs);
                            setFileStats(context, n.uri, fs);
                            all.add(new Storage.RecordingUri(context, n.uri, fs));
                        } catch (Exception e) {
                            Log.d(TAG, n.toString(), e);
                        }
                    } else {
                        all.add(new Storage.RecordingUri(context, n.uri, fs));
                    }
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (thread != t)
                            return; // replaced with new thread, exit
                        items.clear(); // clear recordings
                        TreeSet<String> delete = new TreeSet<>();
                        for (String k : prefs.keySet()) {
                            if (k.startsWith(MainApplication.PREFERENCE_DETAILS_PREFIX))
                                delete.add(k);
                        }
                        TreeSet<Uri> delete2 = new TreeSet<>(cache.keySet());
                        for (Storage.RecordingUri f : all) {
                            if (filter(f))
                                items.add(f); // add recording
                            cleanDelete(delete, f.uri);
                            delete2.remove(f.uri);
                        }
                        if (clean) {
                            SharedPreferences.Editor editor = shared.edit();
                            for (String s : delete)
                                editor.remove(s);
                            for (Uri f : delete2)
                                cache.remove(f);
                            editor.commit();
                        }
                        sort();
                        if (done != null)
                            done.run();
                    }
                });
            }
        };
        thread.start();
    }

    public void cleanDelete(TreeSet<String> delete, Uri f) { // file exists, prevent it from cleaning
        String p = MainApplication.getFilePref(f);
        delete.remove(p + MainApplication.PREFERENCE_DETAILS_FS);
        delete.remove(p + MainApplication.PREFERENCE_DETAILS_STAR);
    }

    public Comparator<Storage.RecordingUri> getSort() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        int selected = context.getResources().getIdentifier(shared.getString(MainApplication.PREFERENCE_SORT, context.getResources().getResourceEntryName(R.id.sort_date_desc)), "id", context.getPackageName());
        if (selected == R.id.sort_date_ask)
            return new SortByDate();
        else if (selected == R.id.sort_date_desc)
            return Collections.reverseOrder(new SortByDate());
        else if (selected == R.id.sort_name_ask)
            return new SortByName();
        else if (selected == R.id.sort_name_desc)
            return Collections.reverseOrder(new SortByName());
        return new SortByName();
    }

    public void sort(Comparator<Storage.RecordingUri> sort) {
        Collections.sort(items, sort);
        notifyDataSetChanged();
    }

    public void sort() {
        sort(getSort());
    }

    public void close() {
        playerStop();
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        if (receiver != null) {
            context.unregisterReceiver(receiver);
            receiver = null;
        }
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        shared.unregisterOnSharedPreferenceChangeListener(this);
    }

    public void load(boolean clean, Runnable done) {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String path = shared.getString(MainApplication.PREFERENCE_STORAGE, "");

        Uri user;
        if (path.startsWith(ContentResolver.SCHEME_CONTENT))
            user = Uri.parse(path);
        else if (path.startsWith(ContentResolver.SCHEME_FILE))
            user = Uri.parse(path);
        else
            user = Uri.fromFile(new File(path));

        Uri mount = storage.getStoragePath(path);

        if (!user.equals(mount))
            clean = false; // do not clean if we failed to mount user selected folder

        load(mount, clean, done);
    }

    public void load(Uri mount, boolean clean, Runnable done) {
        scan(Storage.scan(context, mount, Factory.ENCODERS), clean, done);
    }

    public View inflate(LayoutInflater inflater, int id, ViewGroup parent) {
        return inflater.inflate(id, parent, false);
    }

    @Override
    public RecordingHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View convertView = inflate(inflater, R.layout.recording, parent);
        return new RecordingHolder(convertView);
    }

    @Override
    public void onBindViewHolder(final RecordingHolder h, int position) {
        final Storage.RecordingUri f = items.get(position);

        final boolean starb = MainApplication.getStar(context, f.uri);
        starUpdate(h.star, starb);
        h.star.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean b = !MainApplication.getStar(context, f.uri);
                MainApplication.setStar(context, f.uri, b);
                starUpdate(h.star, b);
            }
        });

        h.title.setText(f.name);

        h.time.setText(MainApplication.SIMPLE.format(new Date(f.last)));

        h.dur.setText(MainApplication.formatDuration(context, f.duration));

        h.size.setText(MainApplication.formatSize(context, f.size));

        h.playerBase.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });

        final Runnable delete = new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle(R.string.delete_recording);
                builder.setMessage("...\\" + f.name + "\n\n" + context.getString(R.string.are_you_sure));
                builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        playerStop();
                        try {
                            Storage.delete(context, f.uri);
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        }
                        select(-1);
                        int pos = items.indexOf(f);
                        items.remove(f); // instant remove
                        notifyItemRemoved(pos);
                    }
                });
                builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                showDialog(builder);
            }
        };

        final Runnable rename = new Runnable() {
            @Override
            public void run() {
                final OpenFileDialog.EditTextDialog e = new OpenFileDialog.EditTextDialog(context);
                e.setTitle(context.getString(R.string.rename_recording));
                e.setText(Storage.getNameNoExt(f.name));
                e.setPositiveButton(new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        playerStop();
                        String ext = Storage.getExt(f.name);
                        String s = String.format("%s.%s", e.getText(), ext);
                        try {
                            f.uri = storage.rename(f.uri, s);
                        } catch (FileNotFoundException fileNotFoundException) {
                            fileNotFoundException.printStackTrace();
                        }
                        f.name = s;
                        int pos = items.indexOf(f);
                        notifyItemChanged(pos);
                    }
                });
                showDialog(e);

            }
        };

        if (selected == position) {
            updatePlayerText(h, f);

            h.play.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (player == null) {
                        playerPlay(h, f);
                    } else if (player.getPlayWhenReady()) {
                        playerPause(h, f);
                    } else {
                        playerPlay(h, f);
                    }
                }
            });

            h.edit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    rename.run();
                }
            });

            h.share.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = StorageProvider.getProvider().shareIntent(f.uri, f.name, Storage.getTypeByName(f.name), f.name);
                    PopupShareActionProvider.show(context, h.share, intent);
                }
            });

            KeyguardManager myKM = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            final boolean locked = myKM.inKeyguardRestrictedInputMode();

            if (locked) {
                h.trash.setOnClickListener(null);
                h.trash.setClickable(true);
                h.trash.setColorFilter(Color.GRAY);
            } else {
                h.trash.setColorFilter(ThemeUtils.getThemeColor(context, R.color.colorPrimary));
                h.trash.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        delete.run();
                    }
                });
            }

            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(-1);
                }
            });
        } else {
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(h.getAdapterPosition());
                }
            });
        }

        h.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                PopupMenu popup = new PopupMenu(context, v);
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
                            rename.run();
                            return true;
                        }
                        return false;
                    }
                });
                popup.show();
                return true;
            }
        });

        animator.onBindViewHolder(h, position);

        if (Build.VERSION.SDK_INT >= 14)
            downloadTask(f, h.itemView);
        else
            downloadTaskUpdate(null, f, h.itemView);
    }

    @Override
    public Bitmap downloadImageTask(CacheImagesAdapter.DownloadImageTask task) {
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        Storage.RecordingUri f = (Storage.RecordingUri) task.item;
        try {
            File cover = getCover(context, f);
            if (cover.exists() && f.data == null)
                f.data = MoodbarView.loadMoodbar(cover);
            if (f.data == null)
                f.data = MoodbarView.getMoodbar(context, f.uri);
            if (f.data != null)
                MoodbarView.saveMoodbar(f.data, cover);
        } catch (Exception e) {
            Log.e(TAG, "Unable to load cover", e);
        }
        return null;
    }

    @Override
    public void downloadTaskUpdate(CacheImagesAdapter.DownloadImageTask task, Object item, Object view) {
        super.downloadTaskUpdate(task, item, view);
        RecordingHolder h = new RecordingHolder((View) view);
        h.moodbarProgress.setVisibility((task == null || task.done) ? View.GONE : View.VISIBLE);
        Storage.RecordingUri f = (Storage.RecordingUri) item;
        if (task == null || !task.done || f.data == null)
            h.moodbar.setVisibility(View.INVISIBLE);
        else
            h.moodbar.setVisibility(View.VISIBLE);
        h.moodbar.setData(f.data);
    }

    protected void starUpdate(ImageView star, boolean starb) {
        if (starb) {
            star.setImageResource(R.drawable.ic_star_black_24dp);
            star.setContentDescription(context.getString(R.string.starred));
        } else {
            star.setImageResource(R.drawable.ic_star_border_black_24dp);
            star.setContentDescription(context.getString(R.string.not_starred));
        }
    }

    public boolean getPrefCall() {
        return false;
    }

    protected void playerPlay(RecordingHolder h, final Storage.RecordingUri f) {
        if (player == null) {
            player = MediaPlayerCompat.create(context, f.uri);
            if (player == null) {
                Toast.makeText(context, R.string.file_not_found, Toast.LENGTH_SHORT).show();
                return;
            }
            player.prepare();
            if (getPrefCall()) {
                pscl = new PhoneStateChangeListener(h, f);
                TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                tm.listen(pscl, PhoneStateListener.LISTEN_CALL_STATE);
            }
        }
        player.setPlayWhenReady(true);

        if (proximity == null) {
            proximity = new ProximityPlayer(context) {
                @Override
                public void prepare() {
                    player.setAudioStreamType(streamType);
                }
            };
            proximity.create();
        }

        updatePlayerRun(h, f);
    }

    protected void playerPause(RecordingHolder h, Storage.RecordingUri f) {
        if (player != null)
            player.setPlayWhenReady(false);
        if (updatePlayer != null) {
            handler.removeCallbacks(updatePlayer);
            updatePlayer = null;
        }
        if (proximity != null) {
            proximity.close();
            proximity = null;
        }
        updatePlayerText(h, f);
    }

    protected void playerStop() {
        if (updatePlayer != null) {
            handler.removeCallbacks(updatePlayer);
            updatePlayer = null;
        }
        if (proximity != null) {
            proximity.close();
            proximity = null;
        }
        if (player != null) {
            player.setPlayWhenReady(false); // stop()
            player.release();
            player = null;
        }
        if (pscl != null) {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(pscl, PhoneStateListener.LISTEN_NONE);
            pscl = null;
        }
    }

    protected void updatePlayerRun(final RecordingHolder h, final Storage.RecordingUri f) {
        boolean playing = updatePlayerText(h, f);

        if (updatePlayer != null) {
            handler.removeCallbacks(updatePlayer);
            updatePlayer = null;
        }

        if (!playing) {
            playerStop(); // clear player instance
            updatePlayerText(h, f); // update length
            return;
        }

        updatePlayer = new Runnable() {
            @Override
            public void run() {
                updatePlayerRun(h, f);
            }
        };
        handler.postDelayed(updatePlayer, 200);
    }

    protected boolean updatePlayerText(final RecordingHolder h, final Storage.RecordingUri f) {
        final boolean playing = player != null && player.getPlayWhenReady();

        h.play.setImageResource(playing ? R.drawable.ic_pause_black_24dp : R.drawable.ic_play_arrow_black_24dp);
        h.play.setContentDescription(context.getString(playing ? R.string.pause_button : R.string.play_button));

        long c = 0;
        Long d = f.duration;

        if (player != null) {
            c = player.getCurrentPosition();
            d = player.getDuration();
        }

        h.bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser)
                    return;

                if (player == null)
                    playerPlay(h, f);

                if (player != null) {
                    player.seekTo(progress);
                    if (!player.getPlayWhenReady())
                        playerPlay(h, f);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        h.start.setText(MainApplication.formatDuration(context, c));
        h.bar.setMax(d.intValue());
        h.bar.setKeyProgressIncrement(1);
        h.bar.setProgress((int) c);
        h.end.setText("-" + MainApplication.formatDuration(context, d - c));

        return playing;
    }

    public void select(int pos) {
        if (selected != pos && selected != -1)
            notifyItemChanged(selected);
        selected = pos;
        if (pos != -1)
            notifyItemChanged(pos);
        playerStop();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public Storage.RecordingUri getItem(int pos) {
        return items.get(pos);
    }

    protected AppCompatImageButton getCheckBox(View v) {
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = getCheckBox(g.getChildAt(i));
                if (c != null)
                    return (AppCompatImageButton) c;
            }
        }
        if (v instanceof AppCompatImageButton)
            return (AppCompatImageButton) v;
        return null;
    }

    protected void selectToolbar(View v, boolean pressed) {
        AppCompatImageButton cc = getCheckBox(v);
        if (pressed) {
            int[] states = new int[]{
                    android.R.attr.state_checked,
            };
            cc.setImageState(states, false);
        } else {
            int[] states = new int[]{
                    -android.R.attr.state_checked,
            };
            cc.setImageState(states, false);
        }
    }

    protected void selectToolbar() {
        selectToolbar(toolbar_a, toolbarFilterAll);
        selectToolbar(toolbar_s, !toolbarFilterAll);
    }

    public void setToolbar(ViewGroup v) {
        this.toolbar = v;
        toolbar_a = v.findViewById(R.id.toolbar_all);
        toolbar_a.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toolbarFilterAll = true;
                selectToolbar();
                load(false, null);
                save();
            }
        });
        toolbar_s = v.findViewById(R.id.toolbar_stars);
        toolbar_s.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toolbarFilterAll = false;
                selectToolbar();
                load(false, null);
                save();
            }
        });
        selectToolbar();
    }

    protected void save() {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = shared.edit();
        edit.putBoolean(MainApplication.PREFERENCE_FILTER, toolbarFilterAll);
        edit.commit();
    }

    protected void load() {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        toolbarFilterAll = shared.getBoolean(MainApplication.PREFERENCE_FILTER, true);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(MainApplication.PREFERENCE_STORAGE))
            load(true, null);
    }

    public void search(String q) {
        filter = q.toLowerCase(Locale.US);
        load(false, null);
    }

    public void searchClose() {
        filter = null;
        load(false, null);
    }

    public void showDialog(AlertDialog.Builder e) {
        e.show();
    }

    public void onCreateOptionsMenu(Menu menu) {
        MenuItem sort = menu.findItem(R.id.action_sort);
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        int selected = context.getResources().getIdentifier(shared.getString(MainApplication.PREFERENCE_SORT, context.getResources().getResourceEntryName(R.id.sort_date_desc)), "id", context.getPackageName());
        SubMenu sorts = sort.getSubMenu();
        for (int i = 0; i < sorts.size(); i++) {
            MenuItem m = sorts.getItem(i);
            if (m.getItemId() == selected)
                m.setChecked(true);
            m.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    return false;
                }
            });
        }
    }

    public boolean onOptionsItemSelected(Activity a, MenuItem item) {
        int i = item.getItemId();
        if (i == R.id.sort_date_ask || i == R.id.sort_date_desc || i == R.id.sort_name_ask || i == R.id.sort_name_desc) {
            onSortOptionSelected(a, i);
            return true;
        }
        return false;
    }

    public void onSortOptionSelected(Activity a, int id) {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        shared.edit().putString(MainApplication.PREFERENCE_SORT, context.getResources().getResourceEntryName(id)).commit();
        select(-1);
        sort();
        ActivityCompat.invalidateOptionsMenu(a);
    }
}
