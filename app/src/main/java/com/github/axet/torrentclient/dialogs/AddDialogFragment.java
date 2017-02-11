package com.github.axet.torrentclient.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.github.axet.androidlibrary.app.LibraryApplication;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;
import com.github.axet.torrentclient.app.MainApplication;
import com.github.axet.torrentclient.app.Storage;
import com.github.axet.torrentclient.widgets.Pieces;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import go.libtorrent.Libtorrent;

public class AddDialogFragment extends DialogFragment implements MainActivity.TorrentFragmentInterface {
    View v;
    View header;
    ListView list;
    View toolbar;
    View download;
    TextView name;
    TextView size;
    View info;
    TextView pieces;
    TextView path;
    ImageView check;
    Pieces pview;
    View renameButton;
    View browse;

    Files files;

    String torrentName;

    Result result = new Result();

    public static class Result implements DialogInterface {
        public long t;
        public boolean ok;

        @Override
        public void cancel() {
        }

        @Override
        public void dismiss() {
        }
    }

    static class TorFile {
        public long index;
        public go.libtorrent.File file;

        public TorFile(long i, go.libtorrent.File f) {
            this.file = f;
            this.index = i;
        }
    }

    static class SortFiles implements Comparator<TorFile> {
        @Override
        public int compare(TorFile file, TorFile file2) {
            List<String> s1 = splitPath(file.file.getPath());
            List<String> s2 = splitPath(file2.file.getPath());

            int c = new Integer(s1.size()).compareTo(s2.size());
            if (c != 0)
                return c;

            for (int i = 0; i < s1.size(); i++) {
                String p1 = s1.get(i);
                String p2 = s2.get(i);
                c = p1.compareTo(p2);
                if (c != 0)
                    return c;
            }

            return 0;
        }
    }

    ArrayList<TorFile> ff = new ArrayList<>();

    class Files extends BaseAdapter {

        @Override
        public int getCount() {
            return ff.size();
        }

        @Override
        public TorFile getItem(int i) {
            return ff.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        boolean single(File path) {
            return path.getName().equals(path);
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            LayoutInflater inflater = LayoutInflater.from(getContext());

            if (view == null) {
                view = inflater.inflate(R.layout.torrent_files_item, viewGroup, false);
            }

            final long t = getArguments().getLong("torrent");

            final TorFile f = getItem(i);

            final CheckBox check = (CheckBox) view.findViewById(R.id.torrent_files_check);
            check.setChecked(f.file.getCheck());
            check.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Libtorrent.torrentFilesCheck(t, f.index, check.isChecked());
                }
            });

            TextView percent = (TextView) view.findViewById(R.id.torrent_files_percent);
            percent.setEnabled(false);
            MainApplication.setText(percent, (f.file.getBytesCompleted() * 100 / f.file.getLength()) + "%");

            TextView size = (TextView) view.findViewById(R.id.torrent_files_size);
            size.setText(getContext().getString(R.string.size_tab) + " " + LibraryApplication.formatSize(getContext(), f.file.getLength()));

            TextView folder = (TextView) view.findViewById(R.id.torrent_files_folder);
            TextView file = (TextView) view.findViewById(R.id.torrent_files_name);

            View fc = view.findViewById(R.id.torrent_files_file);
            fc.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Libtorrent.torrentFilesCheck(t, f.index, check.isChecked());
                }
            });

            String s = f.file.getPath();

            List<String> ss = splitPathFilter(s);

            if (ss.size() == 0) {
                folder.setVisibility(View.GONE);
                file.setText("./" + s);
            } else {
                if (i == 0) {
                    folder.setVisibility(View.GONE);
                } else {
                    File p1 = new File(makePath(ss)).getParentFile();
                    File p2 = new File(makePath(splitPathFilter(getItem(i - 1).file.getPath()))).getParentFile();
                    if (p1 == null || p1.equals(p2)) {
                        folder.setVisibility(View.GONE);
                    } else {
                        folder.setText("./" + p1.getPath());
                        folder.setVisibility(View.VISIBLE);
                    }
                }
                file.setText("./" + ss.get(ss.size() - 1));
            }

            updateView(view);

            return view;
        }
    }

    public void updateView(View view) {
    }

    public static String makePath(List<String> ss) {
        if (ss.size() == 0)
            return "/";
        return TextUtils.join(File.separator, ss);
    }

    public List<String> splitPathFilter(String s) {
        List<String> ss = splitPath(s);
        if (ss.get(0).equals(torrentName))
            ss.remove(0);
        return ss;
    }

    public static List<String> splitPath(String s) {
        return new ArrayList<String>(Arrays.asList(s.split(Pattern.quote(File.separator))));
    }

    MainApplication getApp() {
        return (MainApplication) getActivity().getApplicationContext();
    }

    MainActivity getMainActivity() {
        return (MainActivity) getActivity();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);

        long t = getArguments().getLong("torrent");
        if (t != -1) {
            Libtorrent.removeTorrent(t);
            getArguments().putLong("torrent", -1);
        }

        // stop update
        v = null;

        final Activity activity = getActivity();
        if (activity instanceof DialogInterface.OnDismissListener) {
            ((DialogInterface.OnDismissListener) activity).onDismiss(result);
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder b = new AlertDialog.Builder(getActivity())
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                long t = getArguments().getLong("torrent");
                                String path = getArguments().getString("path");
                                getArguments().putLong("torrent", -1);
                                getApp().getStorage().add(new Storage.Torrent(getContext(), t, path, true));
                                result.t = t;
                                result.ok = true;
                                dialog.dismiss();
                                onDismiss(dialog);
                            }
                        }
                )
                .setNegativeButton(R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                dialog.dismiss();
                                onDismiss(dialog);
                            }
                        }
                )
                .setTitle(R.string.add_torrent)
                .setView(createView(LayoutInflater.from(getContext()), null, savedInstanceState));

        builder(b);

        return b.create();
    }

    void builder(AlertDialog.Builder b) {
    }

    @Nullable
    @Override
    public View getView() {
        return null;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return v;
    }

    public View createView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        header = inflater.inflate(R.layout.torrent_add, container, false);

        final long t = getArguments().getLong("torrent");

        download = header.findViewById(R.id.torrent_files_metadata);
        download.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long t = getArguments().getLong("torrent");
                if (!Libtorrent.downloadMetadata(t)) {
                    getMainActivity().Error(Libtorrent.error());
                    return;
                }
            }
        });

        list = new ListView(getContext());

        list.addHeaderView(header);

        v = list;

        toolbar = header.findViewById(R.id.torrent_files_toolbar);

        files = new Files();

        list.setAdapter(files);

        View none = header.findViewById(R.id.torrent_files_none);
        none.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long t = getArguments().getLong("torrent");
                for (TorFile f : ff) {
                    Libtorrent.torrentFilesCheck(t, f.index, false);
                }
                files.notifyDataSetChanged();
            }
        });

        View all = header.findViewById(R.id.torrent_files_all);
        all.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long t = getArguments().getLong("torrent");
                for (TorFile f : ff) {
                    Libtorrent.torrentFilesCheck(t, f.index, true);
                }
                files.notifyDataSetChanged();
            }
        });

        browse = header.findViewById(R.id.torrent_add_browse);
        browse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final OpenFileDialog f = new OpenFileDialog(getContext());

                f.setCurrentPath(new File(getArguments().getString("path")));
                f.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        File p = f.getCurrentPath();
                        getArguments().putString("path", p.getPath());

                        long t = getArguments().getLong("torrent");

                        byte[] buf = Libtorrent.getTorrent(t);
                        Libtorrent.removeTorrent(t);

                        t = Libtorrent.addTorrentFromBytes(p.getPath(), buf);
                        getArguments().putLong("torrent", t);

                        update();
                    }
                });
                f.show();
            }
        });

        final String h = Libtorrent.torrentHash(t);
        final TextView hash = (TextView) header.findViewById(R.id.torrent_hash);
        hash.setText(h);

        size = (TextView) header.findViewById(R.id.torrent_size);
        name = (TextView) v.findViewById(R.id.torrent_name);
        info = header.findViewById(R.id.torrent_add_info_section);
        pieces = (TextView) header.findViewById(R.id.torrent_pieces);
        path = (TextView) header.findViewById(R.id.torrent_add_path);
        check = (ImageView) header.findViewById(R.id.torrent_add_check);
        pview = (Pieces) header.findViewById(R.id.torrent_status_pieces);

        renameButton = v.findViewById(R.id.torrent_add_rename);
        renameButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final OpenFileDialog.EditTextDialog e = new OpenFileDialog.EditTextDialog(getContext());
                e.setTitle(getString(R.string.rename_torrent));
                e.setText(Libtorrent.torrentName(t));
                e.setPositiveButton(new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = e.getText();
                        // clear slashes
                        name = new File(name).getName();
                        if (name.isEmpty())
                            return;
                        Libtorrent.torrentSetName(t, name);

                        if (Libtorrent.torrentStatus(t) != Libtorrent.StatusChecking)
                            Libtorrent.checkTorrent(t);

                        update();
                    }
                });
                e.show();
            }
        });

        update();

        return v;
    }

    @Override
    public void update() {
        // dialog maybe created but onCreateView not yet called
        if (v == null)
            return;

        long t = getArguments().getLong("torrent");

        name.setText(Libtorrent.torrentName(t));

        info.setVisibility(Libtorrent.metaTorrent(t) ? View.VISIBLE : View.GONE);

        renameButton.setVisibility(Libtorrent.metaTorrent(t) ? View.VISIBLE : View.GONE);

        MainApplication.setText(size, !Libtorrent.metaTorrent(t) ? "" : LibraryApplication.formatSize(getContext(), Libtorrent.torrentBytesLength(t)));

        MainApplication.setText(pieces, !Libtorrent.metaTorrent(t) ? "" : Libtorrent.torrentPiecesCount(t) + " / " + LibraryApplication.formatSize(getContext(), Libtorrent.torrentPieceLength(t)));

        path.setText(getArguments().getString("path"));

        check.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long t = getArguments().getLong("torrent");
                ((MainActivity) getActivity()).checkTorrent(t);
                update();
            }
        });

        if (Libtorrent.torrentStatus(t) == Libtorrent.StatusChecking) {
            check.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_stop_black_24dp));
        } else {
            check.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_done_all_black_24dp));
        }

        pview.setTorrent(t);

        download.setVisibility(Libtorrent.metaTorrent(t) ? View.GONE : View.VISIBLE);
        toolbar.setVisibility(Libtorrent.metaTorrent(t) ? View.VISIBLE : View.GONE);

        torrentName = Libtorrent.torrentName(t);

        long l = Libtorrent.torrentFilesCount(t);
        ff.clear();
        for (long i = 0; i < l; i++) {
            ff.add(new TorFile(i, Libtorrent.torrentFiles(t, i)));
        }
        Collections.sort(ff, new SortFiles());
        files.notifyDataSetChanged();

        if (Libtorrent.metaTorrent(t)) {
            String n = "./" + Libtorrent.torrentName(t);
            if (ff.size() > 1)
                n += "/";
            name.setText(n);
        } else {
            String n = Libtorrent.torrentName(t);
            if (n.isEmpty())
                n = getString(R.string.n_a);
            name.setText(n);
        }
    }

    @Override
    public void close() {

    }
}
