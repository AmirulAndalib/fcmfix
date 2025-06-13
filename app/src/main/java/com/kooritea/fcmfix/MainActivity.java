package com.kooritea.fcmfix;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.kooritea.fcmfix.util.IceboxUtils;

public class MainActivity extends AppCompatActivity {
    private AppListAdapter appListAdapter;
    Set<String> allowList = new HashSet<>();
    JSONObject config = new JSONObject();

    private class AppInfo {
        public String name;
        public String packageName;
        public Drawable icon;
        public Boolean isAllow = false;
        public Boolean includeFcm = false;
        public AppInfo(PackageInfo packageInfo){
            this.name = packageInfo.applicationInfo.loadLabel(getPackageManager()).toString();
            this.packageName = packageInfo.packageName;
            this.icon = packageInfo.applicationInfo.loadIcon(getPackageManager());
        }
    }

    private class AppListAdapter  extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {

        private final List<AppInfo> mAppList;
        class ViewHolder extends RecyclerView.ViewHolder {
            View appView;
            ImageView icon;
            TextView name;
            TextView packageName;
            TextView includeFcm;
            CheckBox isAllow;

            public ViewHolder(View view) {
                super(view);
                appView = view;
                icon = view.findViewById(R.id.icon);
                name = view.findViewById(R.id.name);
                packageName = view.findViewById(R.id.packageName);
                includeFcm = view.findViewById(R.id.includeFcm);
                isAllow = view.findViewById(R.id.isAllow);
            }
        }

        public AppListAdapter(){
            Set<String> allowListSet = new HashSet<>(allowList);
            allowListSet.containsAll(allowList);
            List<AppInfo> _allowList = new ArrayList<>();
            List<AppInfo> _notAllowList = new ArrayList<>();
            List<AppInfo> _notFoundFcm = new ArrayList<>();
            PackageManager packageManager = getPackageManager();
            for(PackageInfo packageInfo : packageManager.getInstalledPackages(PackageManager.GET_RECEIVERS | PackageManager.MATCH_DISABLED_COMPONENTS | PackageManager.MATCH_UNINSTALLED_PACKAGES)) {
                boolean flag = false;
                AppInfo appInfo = new AppInfo(packageInfo);
                if (packageInfo.receivers != null) {
                    for (ActivityInfo  receiverInfo : packageInfo.receivers ){
                        if(receiverInfo.name.equals("com.google.firebase.iid.FirebaseInstanceIdReceiver") || receiverInfo.name.equals("com.google.android.gms.measurement.AppMeasurementReceiver")){
                            flag = true;
                            appInfo.includeFcm = true;
                            break;
                        }
                    }
                }else{
                    continue;
                }
                if(allowListSet.contains(appInfo.packageName)){
                    appInfo.isAllow = true;
                    _allowList.add(appInfo);
                }else{
                    if(flag){
                        _notAllowList.add(appInfo);
                    }else{
                        _notFoundFcm.add(appInfo);
                    }
                }
            }
            class SortName implements Comparator<AppInfo> {
                final Collator localCompare = Collator.getInstance(Locale.getDefault());
                @Override
                public int compare(AppInfo a1, AppInfo a2) {
                    if(localCompare.compare(a1.name,a2.name)>0){
                        return 1;
                    }else if (localCompare.compare(a1.name, a2.name) < 0) {
                        return -1;
                    }
                    return 0;
                }
            }
            final SortName sortName = new SortName();
            _allowList.sort(sortName);
            _notAllowList.sort(sortName);
            _notFoundFcm.sort(sortName);
            _allowList.addAll(_notAllowList);
            _allowList.addAll(_notFoundFcm);
            this.mAppList = _allowList;
            if(_allowList.size() == 0 || _allowList.isEmpty() ||(_allowList.size() == 1 && "com.kooritea.fcmfix".equals(_allowList.get(0).packageName))){
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("请在系统设置中授予读取应用列表权限")
                        .setMessage("或直接编辑" + getApplicationContext().getFilesDir().getAbsolutePath() + "/config.json(需重启生效)")
                        .setPositiveButton("确定", (dialog, which) -> {})
                        .show();
            }
        }


        @SuppressLint("NotifyDataSetChanged")
        @NonNull
        @Override
        public AppListAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.app_item, parent, false);
            final ViewHolder holder = new ViewHolder(view);
            holder.appView.setOnClickListener(v -> {
                int position = holder.getBindingAdapterPosition();
                AppInfo appInfo = mAppList.get(position);
                if(appInfo.isAllow){
                    deleteAppInAllowList(appInfo.packageName);
                }else{
                    addAppInAllowList(appInfo.packageName);
                }
                appInfo.isAllow = !appInfo.isAllow;
                appListAdapter.notifyDataSetChanged();
            });
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull AppListAdapter.ViewHolder holder, int position) {
            AppInfo appInfo = mAppList.get(position);
            holder.icon.setImageDrawable(appInfo.icon);
            holder.name.setText(appInfo.name);
            holder.packageName.setText(appInfo.packageName);
            holder.includeFcm.setVisibility(appInfo.includeFcm ? View.VISIBLE : View.GONE);
            holder.isAllow.setChecked(appInfo.isAllow);
        }

        @Override
        public int getItemCount() {
            return mAppList.size();
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        try {
            if (ContextCompat.checkSelfPermission(this, IceboxUtils.SDK_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{IceboxUtils.SDK_PERMISSION}, IceboxUtils.REQUEST_CODE);
            }
        } catch (Throwable ignored) {
        }

        try {
            FileInputStream fis = this.openFileInput("config.json");
            InputStreamReader inputStreamReader = new InputStreamReader(fis, StandardCharsets.UTF_8);
            StringBuilder stringBuilder = new StringBuilder();
            BufferedReader reader = new BufferedReader(inputStreamReader);
            String line = reader.readLine();
            while (line != null) {
                stringBuilder.append(line).append('\n');
                line = reader.readLine();
            }
            this.config = new JSONObject(stringBuilder.toString());
            JSONArray jsonAllowList = this.config.getJSONArray("allowList");
            for(int i = 0; i < jsonAllowList.length(); i++){
                this.allowList.add(jsonAllowList.getString(i));
            }
            if(this.config.isNull("disableAutoCleanNotification")){
                this.config.put("disableAutoCleanNotification", false);
            }
            if(this.config.isNull("includeIceBoxDisableApp")){
                this.config.put("includeIceBoxDisableApp", false);
            }
            if(this.config.isNull("noResponseNotification")){
                this.config.put("noResponseNotification", false);
            }
        } catch (IOException | JSONException e) {
            Log.e("onCreate",e.toString());
        }
        new Handler().postDelayed(() -> {
            appListAdapter = new AppListAdapter();
            recyclerView.setAdapter(appListAdapter);
            findViewById(R.id.progress_bar).setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }, 1000);
    }

    @Nullable
    @Override
    public View onCreateView(@Nullable View parent, @NonNull String name, @NonNull Context context, @NonNull AttributeSet attrs) {
        return super.onCreateView(parent, name, context, attrs);
    }

    private void addAppInAllowList(String packageName){
        this.allowList.add(packageName);
        this.updateConfig();
    }
    private void deleteAppInAllowList(String packageName){
        this.allowList.remove(packageName);
        this.updateConfig();
    }

    private void updateConfig(){
        try {
            SharedPreferences pref;
            try {
                pref = this.getSharedPreferences("config", Context.MODE_WORLD_READABLE);
            } catch (SecurityException ignored) {
                pref = null;
            }
            if(pref != null){
                SharedPreferences.Editor sharedPreferencesEditor = pref.edit();
                sharedPreferencesEditor.putBoolean("init", true);
                sharedPreferencesEditor.putStringSet("allowList", this.allowList);
                sharedPreferencesEditor.putBoolean("disableAutoCleanNotification", this.config.getBoolean("disableAutoCleanNotification"));
                sharedPreferencesEditor.putBoolean("includeIceBoxDisableApp", this.config.getBoolean("includeIceBoxDisableApp"));
                sharedPreferencesEditor.putBoolean("noResponseNotification", this.config.getBoolean("noResponseNotification"));
                sharedPreferencesEditor.commit();
            }
        } catch (Throwable e) {
            Log.e("updateConfig",e.toString());
        }
        try {
            FileOutputStream fos = this.openFileOutput("config.json", Context.MODE_PRIVATE);
            this.config.put("allowList", new JSONArray(this.allowList));
            fos.write(this.config.toString(2).getBytes());
            this.sendBroadcast(new Intent("com.kooritea.fcmfix.update.config"));
        } catch (IOException | JSONException e) {
            Log.e("updateConfig",e.toString());
            new AlertDialog.Builder(this).setTitle("更新配置文件失败").setMessage(e.getMessage()).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu (Menu menu){
        menu.add("隐藏启动器图标").setCheckable(true);

        menu.add("阻止应用停止时自动清除通知").setCheckable(true);

        menu.add("允许唤醒被冰箱冻结的应用").setCheckable(true);

        menu.add("目标无响应时代发提示通知").setCheckable(true);

        menu.add("全选包含 FCM 的应用");

        menu.add("打开FCM Diagnostics");
        return true;
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public final boolean onPrepareOptionsMenu(Menu menu) {
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if("隐藏启动器图标".equals(item.getTitle())){
                PackageManager packageManager = getPackageManager();
                item.setChecked(packageManager.getComponentEnabledSetting(new ComponentName("com.kooritea.fcmfix", "com.kooritea.fcmfix.Home")) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
            }
            if("阻止应用停止时自动清除通知".equals(item.getTitle())){
                try {
                    item.setChecked(this.config.getBoolean("disableAutoCleanNotification"));
                } catch (JSONException e) {
                    item.setChecked(false);
                }
            }
            if("允许唤醒被冰箱冻结的应用".equals(item.getTitle())){
                try {
                    item.setChecked(this.config.getBoolean("includeIceBoxDisableApp"));
                } catch (JSONException e) {
                    item.setChecked(false);
                }
            }
            if("目标无响应时代发提示通知".equals(item.getTitle())){
                try {
                    item.setChecked(this.config.getBoolean("noResponseNotification"));
                } catch (JSONException e) {
                    item.setChecked(false);
                }
            }
            if("全选包含 FCM 的应用".equals(item.getTitle())){
                item.setOnMenuItemClickListener(menuItem -> {
                    for(AppInfo appInfo : appListAdapter.mAppList){
                        if(appInfo.includeFcm){
                            addAppInAllowList(appInfo.packageName);
                            appInfo.isAllow = true;
                        }
                    }
                    appListAdapter.notifyDataSetChanged();
                    return false;
                });
            }
            if("打开FCM Diagnostics".equals(item.getTitle())){
                item.setOnMenuItemClickListener(menuItem -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.setPackage("com.google.android.gms");
                    intent.setComponent(new ComponentName("com.google.android.gms","com.google.android.gms.gcm.GcmDiagnostics"));
                    startActivity(intent);
                    return false;
                });
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public final boolean onOptionsItemSelected(MenuItem menuItem) {
        if(menuItem.getTitle().equals("隐藏启动器图标")){
            PackageManager packageManager = getPackageManager();
            packageManager.setComponentEnabledSetting(
                    new ComponentName("com.kooritea.fcmfix", "com.kooritea.fcmfix.Home"),
                    menuItem.isChecked() ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
            );
        }
        if(menuItem.getTitle().equals("阻止应用停止时自动清除通知")){
            try {
                this.config.put("disableAutoCleanNotification", !menuItem.isChecked());
                this.updateConfig();
            } catch (JSONException e) {
                Log.e("onOptionsItemSelected",e.toString());
            }
        }
        if(menuItem.getTitle().equals("允许唤醒被冰箱冻结的应用")){
            try {
                this.config.put("includeIceBoxDisableApp", !menuItem.isChecked());
                this.updateConfig();
            } catch (JSONException e) {
                Log.e("onOptionsItemSelected",e.toString());
            }
        }
        if(menuItem.getTitle().equals("目标无响应时代发提示通知")){
            try {
                this.config.put("noResponseNotification", !menuItem.isChecked());
                this.updateConfig();
            } catch (JSONException e) {
                Log.e("onOptionsItemSelected",e.toString());
            }
        }
        return true;
    }
}