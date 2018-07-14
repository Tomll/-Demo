package com.example.drp.tracedemo;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.location.BDLocation;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.model.LatLng;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, EasyPermissions.PermissionCallbacks, ServiceConnection {

    private static final String TAG = "MianActivity_dongrp";
    AlertDialog gpsAlertDialog;
    AlertDialog netAlertDialog;
    TextView tvAddress;
    MapView mapView;
    BaiduMap mBaiduMap;
    Timer timerTrace;//定时请求轨迹数据的计时器

//    LocationClient mLocationClient;//定位客户端
//    Trace mTrace;//轨迹服务
//    LBSTraceClient mTraceClient;//轨迹服务客户端

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SDKInitializer.initialize(getApplicationContext());//地图SDK初始化
        setContentView(R.layout.activity_main);
        //请求忽略电池优化(忽略针对我们应用的查杀优化)
        requestIgnoringBatteryOptimization();
        //申请运行时权限
        requestRuntimePermission();
    }

    /**
     * Doze模式是Android6.0上新出的一种模式，是一种全新的、低能耗的状态，在后台只有部分任务允许运行，
     * 其他都被强制停止。当用户一段时间没有使用手机的时候，Doze模式通过延缓app后台的CPU和网络活动减少电量的消耗。
     * 若手机厂商生产的定制机型中使用到该模式，需要申请将app添加进白名单，可尽量帮助鹰眼服务在后台持续运行.
     * 此方法：请求忽略电池优化（忽略针对我们应用的查杀优化）
     */
    public void requestIgnoringBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String packageName = getPackageName();
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            boolean isIgnoring = powerManager.isIgnoringBatteryOptimizations(packageName);
            if (!isIgnoring) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                try {
                    startActivity(intent);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    /**
     * 申请运行时权限
     */
    public void requestRuntimePermission() {
        //所要申请的权限
        String[] allPermissions = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION};
        if (EasyPermissions.hasPermissions(this, allPermissions)) {//检查是否有上述权限
            Log.d(TAG, "已经具备所需的权限了");
            tvAddress = findViewById(R.id.tvAddress);
            mapView = findViewById(R.id.mapView);
            mBaiduMap = mapView.getMap();
        } else {
            //第二个参数是被拒绝后再次申请该权限的解释 //第三个参数是请求码 //第四个参数是要申请的权限
            EasyPermissions.requestPermissions(this, "智邦国际需要：存储、定位等必要权限，请在稍后弹出的授权对话框中授予上述权限！", 0, allPermissions);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //把申请权限的回调交由EasyPermissions处理
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    //下面两个方法是实现EasyPermissions.PermissionCallbacks接口的回调，分别返回授权成功和失败的权限
    @Override
    public void onPermissionsGranted(int requestCode, List<String> perms) {
        Log.d(TAG, "获取成功的权限" + perms);
        tvAddress = findViewById(R.id.tvAddress);
        mapView = findViewById(R.id.mapView);
        mBaiduMap = mapView.getMap();
    }

    @Override
    public void onPermissionsDenied(int requestCode, final List<String> perms) {
        Log.d(TAG, "获取失败的权限" + perms);
        Toast.makeText(MainActivity.this, "权限获取失败", Toast.LENGTH_LONG).show();
        finish();
        //todo  优化逻辑
/*        new AlertDialog.Builder(this).setMessage("应用缺少必须权限，影响正常使用，请您重新授予应用权限")
                .setPositiveButton("是", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            requestPermissions(permissions,0);
                        }
                    }
                })
                .setNegativeButton("否", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(MainActivity.this, "权限获取失败", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }).show();*/

    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (isGPSAndNetworkOK(MainActivity.this)) {
            Log.d(TAG, "GPS  NET  IS OK");

        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (null != netAlertDialog) {
            netAlertDialog.dismiss();
        }
        if (null != gpsAlertDialog) {
            gpsAlertDialog.dismiss();
        }
    }


    int transportMode = 2; //0 汽车 1 骑行 2 步行
    boolean isOnBind;//服务绑定标志

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.bt_start://开始服务
                Log.d(TAG, "bt_start:开启");
                showStartDialog();
                break;
            case R.id.bt_pause://停止服务
                Log.d(TAG, "bt_pause：停止");
                showStopDialog();
                break;
        }
    }

    //弹出开启服务对话框，内部包含:出行方式选择、开启服务等逻辑
    public void showStartDialog() {
        if (!isOnBind) {
            View layout_alert_dialog = LayoutInflater.from(MainActivity.this).inflate(R.layout.layout_alert_dialog, null);
            final AlertDialog alertDialog = new AlertDialog.Builder(this).setTitle("请选择出行方式").setView(layout_alert_dialog).show();
            RadioGroup radioGroup = layout_alert_dialog.findViewById(R.id.transportModeRadioGroup);
            radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    switch (checkedId) {
                        case R.id.driving://汽车 :0
                            transportMode = 0;
                            break;
                        case R.id.riding: //骑行 ：1
                            transportMode = 1;
                            break;
                        case R.id.walking: //步行 ：2
                            transportMode = 2;
                            break;
                    }
                    Intent intent = new Intent(MainActivity.this, LocationTraceService.class);
                    intent.putExtra("transportMode", transportMode);
                    isOnBind = bindService(intent, MainActivity.this, Context.BIND_AUTO_CREATE);
                    alertDialog.dismiss();
                    Toast.makeText(MainActivity.this, "行动轨迹开启", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            Toast.makeText(this, "服务已在运行", Toast.LENGTH_SHORT).show();
        }
    }

    //弹出关闭服务对话框，内部包含:关闭服务、停止定时器任务等逻辑
    public void showStopDialog() {
        if (isOnBind) {
            new AlertDialog.Builder(this).setMessage("确定关闭行动轨迹服务？")
                    .setPositiveButton("是", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Toast.makeText(MainActivity.this, "行动轨迹关闭", Toast.LENGTH_SHORT).show();
                            if (null != timerTrace) {
                                timerTrace.cancel();
                                timerTrace = null;
                                Log.d(TAG, "timerTrace 空空");
                            }
                            unbindService(MainActivity.this);
                            isOnBind = false;
                        }
                    }).setNegativeButton("否", null).show();
        } else {
            Toast.makeText(this, "请先开启服务", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        final LocationTraceService locationTraceService = ((LocationTraceService.MyBinder) service).getLocationTraceService();
        //将locationTraceService中的定位数据 回调到MainActivity
        locationTraceService.setMyLocationDataListener(new LocationTraceService.MyLocationDataListener() {
            @Override
            public void onLocationDataChange(BDLocation bdLocation, ArrayList<BDLocation> historyBDLocations) {
                Log.d(TAG, "回调成功");
                //定位绘制
                if ((transportMode == 1 || transportMode == 2) && null != historyBDLocations && historyBDLocations.size() >= 2) {
                    Log.d(TAG, "bdLocations.size():" + historyBDLocations.size());
                    drawLocationPolyline(historyBDLocations);
                }

                //以下逻辑展示定位点
                String addrStr = bdLocation.getAddrStr();
                LatLng latLng = new LatLng(bdLocation.getLatitude(), bdLocation.getLongitude());
                tvAddress.setText("纬度：" + latLng.latitude + "\n经度：" + latLng.longitude + "\n时间：" + bdLocation.getTime() + "\n地址：" + addrStr + "\n描述：" + bdLocation.getLocationDescribe());
                //addLog(LocationTraceService.this, "定位信息：\n" + "纬度：" + latLng.latitude + "\n经度：" + latLng.longitude + "\n地址信息：" + addrStr);
                mBaiduMap.setMyLocationEnabled(true);// 开启定位图层
                // 构造定位数据
                MyLocationData locData = new MyLocationData.Builder()
                        .latitude(latLng.latitude)
                        .longitude(latLng.longitude)
                        .direction(bdLocation.getDirection())// 此处设置开发者获取到的方向信息，顺时针0-360
                        .accuracy(bdLocation.getRadius())// 获取定位精度
                        .build();
                mBaiduMap.setMyLocationData(locData);// 设置定位数据
                mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newLatLng(latLng));
            }
        });
/*        //定时请求定位数据
        timerLocation = new Timer();
        timerLocation.schedule(new TimerTask() {
            @Override
            public void run() {
                ArrayList<BDLocation> bdLocations = locationTraceService.requestBDLocations();
                if (null != bdLocations && bdLocations.size() > 0) {
                    Log.d(TAG, "bdLocations.size():" + bdLocations.size());
                    drawLocationPolyline(bdLocations);
                }
            }
        }, 1 * 1000, 10 * 1000);*/
        //如果是驾车模式，定时请求轨迹数据
        if (transportMode == 0) {
            timerTrace = new Timer();
            timerTrace.schedule(new TimerTask() {
                @Override
                public void run() {
                    ArrayList<LatLng> latLngs = locationTraceService.requestTrack(0);
                    Log.d(TAG, "请求的鹰眼数据点个数：" + latLngs.size());
                    if (null != latLngs && latLngs.size() >= 2) {//需要两个以上定位点，才能绘制折线
                        drawTracePolyline(latLngs);
                    }
                }
            }, 1 * 1000, 10 * 1000);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.d(TAG, "onServiceDisconnected");
/*        if (null != timerTrace) {
            timerTrace.cancel();
            timerTrace = null;
            Log.d(TAG, "timerTrace 制空");

        }*/
    }


    //鹰眼轨迹绘制折线
    public void drawTracePolyline(ArrayList<LatLng> points) {
        mBaiduMap.clear();//先清空地图
        //绘制折线,注意需要两个以上的点才能绘制，所以我们在前面做了判断
        OverlayOptions ooPolyline = new PolylineOptions().width(10).color(0xAAFF0000).points(points);
        mBaiduMap.addOverlay(ooPolyline);
        //绘制起点
        //BitmapDescriptor startBitmap = BitmapDescriptorFactory.fromResource(R.drawable.start_point);
        //mBaiduMap.addOverlay(new MarkerOptions().position(points.get(0)).icon(startBitmap));
        Log.d(TAG, "draw_by_trace");
    }

    //定位点绘制折线
    public void drawLocationPolyline(List<BDLocation> bdLocations) {
        //从bdLocations得到点的集合
        ArrayList<LatLng> locationPoints = new ArrayList<>();
        for (BDLocation bdLocation : bdLocations) {
            LatLng latLng = new LatLng(bdLocation.getLatitude(), bdLocation.getLongitude());
            locationPoints.add(latLng);
        }
        mBaiduMap.clear();//先清空地图
        //绘制轨迹折线,注意需要两个以上的点才能绘制，所以我们在前面做了判断
        OverlayOptions ooPolyline = new PolylineOptions().width(10).color(0xAA0000FF).points(locationPoints);
        mBaiduMap.addOverlay(ooPolyline);
        //绘制起点
        //BitmapDescriptor startBitmap = BitmapDescriptorFactory.fromResource(R.drawable.start_point);
        //LatLng latLng1 = new LatLng(bdLocations.get(0).getLatitude(), bdLocations.get(0).getLongitude());
        //mBaiduMap.addOverlay(new MarkerOptions().position(latLng1).icon(startBitmap));
        Log.d(TAG, "draw_by_location");

    }


    /**
     * 检查GPS和网络状态并弹窗提醒
     */
    public boolean isGPSAndNetworkOK(final Context context) {
        if (isGPSAvailable(context) && isNetworkAvailable(context)) {
            return true;
        }
        if (!isNetworkAvailable(context)) {
            Log.d(TAG, "net 不可用");
            showNetAlertDialog();
        }
        if (!isGPSAvailable(context)) {
            Log.d(TAG, "gps 不可用");
            showGPSAlertDialog();
        }
        return false;
    }

    public void showNetAlertDialog() {
        if (null != netAlertDialog && !netAlertDialog.isShowing()) {
            netAlertDialog.show();
            Log.d(TAG, "net dialog 不为空，直接show");
        } else {
            netAlertDialog = new AlertDialog.Builder(MainActivity.this)
                    .setCancelable(false)
                    .setTitle("温馨提示")
                    .setMessage("系统检测到您还未开启网络服务，为提高定位的精准性，建议您开启网络服务，是否现在开启？")
                    .setPositiveButton("是", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            openNetworkSetting(MainActivity.this);
                        }
                    })
                    .setNegativeButton("否", null).show();
            Log.d(TAG, "net dialog 创建 show");
        }

    }

    public void showGPSAlertDialog() {
        if (null != gpsAlertDialog && !gpsAlertDialog.isShowing()) {
            gpsAlertDialog.show();
            Log.d(TAG, "gps dialog 不为空，直接show");
        } else {
            gpsAlertDialog = new AlertDialog.Builder(MainActivity.this)
                    .setCancelable(false)
                    .setTitle("温馨提示")
                    .setMessage("系统检测到您还未开启GPS服务，为提高定位的精准性，建议您开启此服务，是否现在开启？")
                    .setPositiveButton("是",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    openGPSSetting(MainActivity.this);
                                }
                            }).setNegativeButton("否", null).show();
            Log.d(TAG, "gps dialog 创建 show");

        }
    }

    //判断GPS是否打开
    public boolean isGPSAvailable(Context context) {
        android.location.LocationManager locationManager = (android.location.LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        // 通过GPS卫星定位，定位级别可以精确到街（通过24颗卫星定位，在室外和空旷的地方定位准确、速度快）
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
    }

    //判断网络是否可用
    public boolean isNetworkAvailable(Context context) {
        if (context != null) {
            ConnectivityManager mConnectivityManager = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo mNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
            if (mNetworkInfo != null) {
                return mNetworkInfo.isAvailable();
            }
        }
        return false;
    }

    //引导用户去GPS设置界面
    public void openGPSSetting(Context context) {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            intent.setAction(Settings.ACTION_SETTINGS);
            try {
                context.startActivity(intent);
            } catch (Exception e) {
            }
        }
    }

    //引导用户去Network设置界面
    public void openNetworkSetting(Context context) {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_WIRELESS_SETTINGS);//跳转到无线设置界面
//        intent.setAction(Settings.ACTION_DATA_ROAMING_SETTINGS);//跳转到双卡移动网络设置界面
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            intent.setAction(Settings.ACTION_SETTINGS);
            try {
                context.startActivity(intent);
            } catch (Exception e) {
            }
        }
    }

    //记录日志
    public void addLog(Context context, String content) {
        SimpleDateFormat df = new SimpleDateFormat("MM.dd HH:mm:ss");//设置日期格式
        String net = MainActivity.this.isNetworkAvailable(context) ? (MainActivity.this.isWiFi(context) ? "wifi" : "移动网络") : "无网络";
        String time = df.format(new Date());
        String gps = MainActivity.this.isGPSAvailable(context) ? "GPS" : "无GPS";
        content = "[" + time + "][" + net + "][" + gps + "]:\n" + content + "\n";

        try {
            File fs = new File(Environment.getExternalStorageDirectory() + "/zbtracelog.txt");
            FileOutputStream outputStream = new FileOutputStream(fs, true);
            outputStream.write(content.getBytes());
            outputStream.flush();
            outputStream.close();
            Log.e(TAG, "write log Successful");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }




/*        FileOutputStream fos = null;
        try {
            fos = context.openFileOutput("zbsyslog.txt", Context.MODE_APPEND);
            fos.write(content.getBytes());
            fos.close();
        } catch (Exception e) {
        }
        fos = null;
        return;*/
    }

    //判断当前环境是否是Wifi
    public boolean isWiFi(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiNetworkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (wifiNetworkInfo.isConnected()) {
            return true;
        } else {
            return false;
        }
    }


}