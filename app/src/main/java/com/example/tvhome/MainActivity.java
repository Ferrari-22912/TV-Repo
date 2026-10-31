package com.example.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.media.tv.*;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    int cols;
    TextView greet;
    LinearLayout dock;
    RecyclerView grid;
    final List<ResolveInfo> apps = new ArrayList<>();

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    GradientDrawable round(int color, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(r)); return g;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        cols = getResources().getInteger(R.integer.columns);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(48), dp(24), dp(48), dp(8));

        // top bar
        RelativeLayout top = new RelativeLayout(this);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        greet = text("", 18, Color.GRAY);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm"); clock.setFormat24Hour("HH:mm");
        clock.setTextSize(56); clock.setTextColor(Color.WHITE);
        clock.setTypeface(android.graphics.Typeface.create("sans-serif-thin", android.graphics.Typeface.NORMAL));
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM"); date.setFormat24Hour("EEEE, d MMMM");
        date.setTextSize(18); date.setTextColor(Color.GRAY);
        left.addView(greet); left.addView(clock); left.addView(date);
        top.addView(left);
        LinearLayout pills = new LinearLayout(this);
        pills.addView(pill("Inputs", v -> showInputs()));
        pills.addView(pill("Wi-Fi", v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS))));
        pills.addView(pill("Settings", v -> open(new Intent(Settings.ACTION_SETTINGS))));
        RelativeLayout.LayoutParams pl = new RelativeLayout.LayoutParams(-2, -2);
        pl.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        top.addView(pills, pl);
        root.addView(top);

        // dock band
        dock = new LinearLayout(this);
        dock.setBackground(round(0xFF1C1C1E, 24));
        dock.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
        dl.topMargin = dp(24);
        root.addView(dock, dl);

        // grid
        grid = new RecyclerView(this);
        grid.setLayoutManager(new GridLayoutManager(this, cols));
        grid.setClipToPadding(false);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-1, 0, 1f);
        gl.topMargin = dp(16);
        root.addView(grid, gl);
        setContentView(root);
    }

    TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color); return t;
    }

    View pill(String label, View.OnClickListener l) {
        TextView t = text(label, 16, Color.WHITE);
        t.setPadding(dp(20), dp(10), dp(20), dp(10));
        t.setFocusable(true); t.setClickable(true);
        t.setBackground(round(0x33FFFFFF, 20));
        t.setOnClickListener(l);
        t.setOnFocusChangeListener((v, f) -> {
            v.setBackground(round(f ? 0x88FFFFFF : 0x33FFFFFF, 20));
            v.animate().scaleX(f ? 1.08f : 1f).scaleY(f ? 1.08f : 1f).setDuration(120).start();
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.leftMargin = dp(12); t.setLayoutParams(p);
        return t;
    }

    void open(Intent i) {
        try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); } catch (Exception ignored) {}
    }

    void showInputs() {
        TvInputManager m = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        final List<String> ids = new ArrayList<>(); List<String> names = new ArrayList<>();
        try {
            for (TvInputInfo i : m.getTvInputList()) {
                ids.add(i.getId());
                CharSequence n = i.loadLabel(this);
                names.add(n == null ? i.getId() : n.toString());
            }
        } catch (Exception ignored) {}
        if (ids.isEmpty()) { Toast.makeText(this, "No inputs found", Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this).setTitle("Inputs")
            .setItems(names.toArray(new String[0]), (d, w) ->
                open(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(ids.get(w)))))
            .show();
    }

    @Override protected void onResume() {
        super.onResume();
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greet.setText(h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening");
        load();
    }

    void load() {
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> all = pm.queryIntentActivities(q, 0);
        List<String> dockPk = Arrays.asList(getResources().getStringArray(R.array.dock_apps));
        dock.removeAllViews(); apps.clear();
        for (String pk : dockPk) {
            for (ResolveInfo r : all) if (r.activityInfo.packageName.equals(pk)) {
                View t = tile(r, pm, true);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
                p.setMargins(dp(6), 0, dp(6), 0);
                dock.addView(t, p); break;
            }
        }
        for (ResolveInfo r : all) {
            String pk = r.activityInfo.packageName;
            if (!dockPk.contains(pk) && !pk.equals(getPackageName())) apps.add(r);
        }
        Collections.sort(apps, new ResolveInfo.DisplayNameComparator(pm));
        grid.setAdapter(new Adapter(pm));
    }

    View tile(ResolveInfo r, PackageManager pm, boolean inDock) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(dp(8), dp(8), dp(8), dp(8));
        t.setFocusable(true); t.setClickable(true);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Drawable d = r.activityInfo.loadBanner(pm);
        if (d == null) d = r.loadIcon(pm);
        iv.setImageDrawable(d);
        t.addView(iv, new LinearLayout.LayoutParams(-1, dp(inDock ? 90 : 100)));
        TextView n = text(r.loadLabel(pm).toString(), 14, Color.WHITE);
        n.setGravity(Gravity.CENTER); n.setSingleLine(true); n.setVisibility(View.INVISIBLE);
        t.addView(n);
        final String pk = r.activityInfo.packageName;
        t.setOnClickListener(v -> {
            Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pk);
            if (i != null) open(i);
        });
        t.setOnFocusChangeListener((v, f) -> {
            v.setBackground(f ? round(0x55FFFFFF, 16) : null);
            n.setVisibility(f ? View.VISIBLE : View.INVISIBLE);
            v.animate().scaleX(f ? 1.08f : 1f).scaleY(f ? 1.08f : 1f).setDuration(120).start();
        });
        return t;
    }

    class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        PackageManager pm;
        Adapter(PackageManager pm) { this.pm = pm; }
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p, int type) {
            FrameLayout f = new FrameLayout(MainActivity.this);
            f.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerView.ViewHolder(f) {};
        }
        public void onBindViewHolder(RecyclerView.ViewHolder h, int i) {
            FrameLayout f = (FrameLayout) h.itemView;
            f.removeAllViews();
            f.addView(tile(apps.get(i), pm, false));
        }
        public int getItemCount() { return apps.size(); }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent e) {
        String pk = null;
        switch (e.getScanCode()) {
            case 242: pk = "com.netflix.ninja"; break;
            case 752: pk = "com.amazon.amazonvideo.livingroom"; break;
            case 245: pk = "com.google.android.youtube.tv"; break;
        }
        if (pk != null) {
            if (e.getRepeatCount() == 0) {
                Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pk);
                if (i != null) open(i);
            }
            return true;
        }
        return super.onKeyDown(keyCode, e);
    }

    @Override public void onBackPressed() { /* stay on Home */ }
}
