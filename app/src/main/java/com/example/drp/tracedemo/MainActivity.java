package com.example.drp.tracedemo;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.model.LatLng;
import com.baidu.trace.LBSTraceClient;
import com.baidu.trace.Trace;
import com.baidu.trace.api.track.HistoryTrackRequest;
import com.baidu.trace.api.track.HistoryTrackResponse;
import com.baidu.trace.api.track.OnTrackListener;
import com.baidu.trace.api.track.TrackPoint;
import com.baidu.trace.model.OnTraceListener;
import com.baidu.trace.model.ProcessOption;
import com.baidu.trace.model.PushMessage;
import com.baidu.trace.model.TransportMode;

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

public class MainActivity extends AppCompatActivity implements BDLocationListener, OnTraceListener,
        View.OnClickListener, EasyPermissions.PermissionCallbacks {

    private static final String TAG = "dongrp";
    AlertDialog gpsAlertDialog;
    AlertDialog netAlertDialog;
    TextView tvAddress;
    MapView mapView;
    BaiduMap mBaiduMap;
    LocationClient mLocationClient;//定位客户端
    Trace mTrace;//轨迹服务
    LBSTraceClient mTraceClient;//轨迹服务客户端

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SDKInitializer.initialize(getApplicationContext());//地图SDK初始化
        setContentView(R.layout.activity_main);
        //申请运行时权限
        requestRuntimePermission();
        //请求忽略电池优化
        requestIgnoringBatteryOptimization();

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
//            startLocation();
//            startTrace();
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
    /*    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (null != mTraceClient) {
            //mTraceClient.stopTrace(mTrace, this);//此方法将同时停止 轨迹采集 和 轨迹服务
            mTraceClient.stopGather(this);//此方法将停止轨迹采集，但不停止轨迹服务
            Log.d(TAG, "停止轨迹追踪");
        }
    }*/


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.bt_start://开始定位
                Log.d(TAG, "bt_start:开启");
//                startTime = System.currentTimeMillis() / 1000;//轨迹记录的开始时间
                startLocation();
                startTrace();
                Toast.makeText(this, "启动定位+鹰眼", Toast.LENGTH_SHORT).show();
                break;
            case R.id.bt_pause://暂停定位
                Log.d(TAG, "bt_pause：停止");
                if (null != mLocationClient) {
                    mLocationClient.stop();
                    locationPoints.clear();
                }
                if (null != mTraceClient && null != timer) {
                    timer.cancel();//Timer一旦被cancel之后就废了，要想再次运行，只有重新构造一个
                    timer = null;//所以timer 置空
                    mTraceClient.stopGather(this);
                    mTraceClient.stopTrace(mTrace, this);
                    mTraceClient = null;
                    Toast.makeText(this, "关闭定位、鹰眼", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.bt_location_draw:
                Log.d(TAG, "bt_location_draw：加载");
                Log.d(TAG, "locationPoints.size():" + locationPoints.size());
                Toast.makeText(this, "定位绘制,locationPoints.size():" + locationPoints.size(), Toast.LENGTH_SHORT).show();
                //绘制折线
                drawPolyline(locationPoints);
                break;
            case R.id.bt_trace_draw:
                Log.d(TAG, "bt_trace_draw：加载");
//                endTime = System.currentTimeMillis() / 1000;//轨迹记录的结束时间
                requestTrack();
                break;


/*            case R.id.bt_save://写入文件，并停止定位
//                Log.d(TAG, "bt_save：保存");
                break;
            case R.id.bt_load://地图划线
*//*                Log.d(TAG, "bt_load：加载");
                //绘制折线
                Log.d(TAG, "locationPoints.size():" + locationPoints.size());
                OverlayOptions ooPolyline = new PolylineOptions().width(10).color(0xAAFF0000).points(locationPoints);
                mBaiduMap.addOverlay(ooPolyline);
                Toast.makeText(this, "加载轨迹", Toast.LENGTH_SHORT).show();*//*
                break;*/

        }
    }

    /**
     * 定位初始化，然后开启定位服务
     */
    public void startLocation() {
        //定位客户端（定位管理器）
        mLocationClient = new LocationClient(MainActivity.this);
        //定位配置信息项
        LocationClientOption option = new LocationClientOption();
        //可选，设置定位模式，默认高精度
        //LocationMode.Hight_Accuracy：高精度；
        //LocationMode. Battery_Saving：低功耗；
        //LocationMode. Device_Sensors：仅使用设备；
        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
        //可选，设置返回经纬度坐标类型
        //gcj02：国测局坐标；
        //bd09ll：百度经纬度坐标；
        //bd09mc：百度墨卡托坐标；
        //海外地区定位，无需设置坐标类型，统一返回wgs84类型坐标
        option.setCoorType("bd09ll");
        //可选，设置发起定位请求的间隔，int类型，单位ms
        //如果设置为0，则代表单次定位，即仅定位一次，默认为0
        //如果设置非0，需设置1000ms以上才有效
        option.setScanSpan(5 * 1000);
        //可选，设置是否使用gps，默认false
        //使用高精度和仅用设备两种定位模式的，参数必须设置为true
        option.setOpenGps(true);
        //可选，设置是否当GPS有效时按照1S/1次频率输出GPS结果，默认false
        option.setLocationNotify(false);
        //可选，定位SDK内部是一个service，并放到了独立进程。
        //设置是否在stop的时候杀死这个进程，默认（建议）不杀死，即setIgnoreKillProcess(true)
        option.setIgnoreKillProcess(true);
        //可选，设置是否收集Crash信息，默认收集，即参数为false
        option.SetIgnoreCacheException(false);
        //可选，7.2版本新增能力，如果设置了该接口，首次启动定位时，会先判断当前WiFi是否超出有效期，若超出有效期，会先重新扫描WiFi，然后定位
        //option.setWifiCacheTimeOut(5 * 60 * 1000);
        //可选，设置是否需要过滤GPS仿真结果，默认需要，即参数为false
        option.setEnableSimulateGps(false);

        //是否需要地址信息
        option.setIsNeedAddress(true);
        //是否需要定位点信息描述
        option.setIsNeedLocationDescribe(true);
        // 是否需要手机的方向（设备头的方向），这里的方向信息也可以通过手机陀螺仪获取
        option.setNeedDeviceDirect(true);
        //注册定位监听器
        mLocationClient.registerLocationListener(this);
        //mLocationClient为第二步初始化过的LocationClient对象
        //需将配置好的LocationClientOption对象，通过setLocOption方法传递给LocationClient对象使用
        //更多LocationClientOption的配置，请参照类参考中LocationClientOption类的详细说明
        mLocationClient.setLocOption(option);
        mLocationClient.start();//开始定位
    }

    /**
     * BDLocationListener接口回调：回调定位信息
     */

    //定位点集合
    ArrayList<LatLng> locationPoints = new ArrayList<>();

    @Override
    public void onReceiveLocation(BDLocation bdLocation) {
        String addrStr = bdLocation.getAddrStr();

//        Log.d(TAG, "bdLocation.getLatitude():" + bdLocation.getLatitude());
//        Log.d(TAG, "bdLocation.getLongitude():" + bdLocation.getLongitude());
//        Log.d(TAG, "getAddrStr: " + addrStr);
//        Log.d(TAG, "getLocationDescribe: " + bdLocation.getLocationDescribe());

        //创建地图坐标点
        LatLng latLng = new LatLng(bdLocation.getLatitude(), bdLocation.getLongitude());
        locationPoints.add(latLng);
        tvAddress.setText("纬度：" + latLng.latitude + "\n经度：" + latLng.longitude
                + "\n地址：" + addrStr + "\n描述：" + bdLocation.getLocationDescribe());

        Log.d(TAG, "纬度：" + latLng.latitude + " 经度：" + latLng.longitude);
        Log.d(TAG, "addrStr:" + addrStr + "  LocationDescribe: " + bdLocation.getLocationDescribe());

        //记录log
        addLog(MainActivity.this, "定位信息：\n" + "纬度：" + latLng.latitude + "\n经度：" + latLng.longitude + "\n地址信息：" + addrStr);

        // 开启定位图层
        mBaiduMap.setMyLocationEnabled(true);
        // 构造定位数据
        //Log.d(TAG, "bdLocation.getDirection():" + bdLocation.getDirection());
        MyLocationData locData = new MyLocationData.Builder()
                .latitude(latLng.latitude)
                .longitude(latLng.longitude)
                .direction(bdLocation.getDirection())// 此处设置开发者获取到的方向信息，顺时针0-360
                .accuracy(bdLocation.getRadius())// 获取定位精度
                .build();
        // 设置定位数据
        mBaiduMap.setMyLocationData(locData);

        // 设置定位图层的配置（定位模式，是否允许方向信息，用户自定义定位图标）
/*        MyLocationConfiguration.LocationMode mCurrentMode = MyLocationConfiguration.LocationMode.NORMAL;//默认为NORMAL 普通态
        //int mCurrentMode = MyLocationConfiguration.LocationMode.FOLLOWING;   //定位跟随态
        //int mCurrentMode = MyLocationConfiguration.LocationMode.COMPASS;  //定位罗盘态
        BitmapDescriptor mCurrentMarker = BitmapDescriptorFactory.fromResource(R.drawable.arrow);
        MyLocationConfiguration config = new MyLocationConfiguration(mCurrentMode, true, mCurrentMarker);
        mBaiduMap.setMyLocationConfigeration(config);*/

        //更新地图状态
        //mBaiduMap.setMapStatus(MapStatusUpdateFactory.newLatLngZoom(latLng, 18));
        //mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newLatLng(latLng));
    }


    /**
     * 轨迹监听初始化，然后开启轨迹监听服务
     */

    Timer timer;//计时器，定时请求最新轨迹点
    //鹰眼轨迹0、初始化，tag、ID、标识、轨迹服务、轨迹客户端
    long serviceId = 202353; //轨迹服务ID

    //    int tag = 0; //请求标识
//    String entityName = "yingyan_demo_huaweiP10"; //设备标识
    int tag = 1; //请求标识
    String entityName = "yingyan_demo_dongrp"; //设备标识
//    int tag = 2; //请求标识
//    String entityName = "yingyan_demo_tanbh"; //设备标识
//    int tag = 3; //请求标识
//    String entityName = "yingyan_demo_songjl"; //设备标识
//    int tag = 4; //请求标识
//    String entityName = "yingyan_demo_liuhf"; //设备标识

    public void startTrace() {
//        //0、初始化，tag、ID、标识、轨迹服务、轨迹客户端
//        int tag = 2; //请求标识
//        long serviceId = 202353; //轨迹服务ID
//        String entityName = "yingyan_demo_drp2"; //设备标识
        boolean isNeedObjectStorage = false;//是否需要对象存储服务，默认为：false，关闭对象存储服务。注：鹰眼 Android SDK v3.0以上
        // 版本支持随轨迹上传图像等对象数据，若需使用此功能，该参数需设为 true，且需导入bos-android-sdk-1.0.2.jar。
        mTrace = new Trace(serviceId, entityName, isNeedObjectStorage);//初始化轨迹服务
        mTraceClient = new LBSTraceClient(MainActivity.this);//初始化轨迹服务客户端
        //1、设置定位、打包回传周期
        int gatherInterval = 5;//定位周期(单位:秒)
        int packInterval = 10;//打包回传周期(单位:秒)
        mTraceClient.setInterval(gatherInterval, packInterval);//设置定位和打包周期
        //2、开启轨迹监听服务
        //注：因为startTrace与startGather是异步执行，且startGather依赖startTrace执行开启服务成功，
        // 所以建议startGather在public void onStartTraceCallback(int errorNo, String message)回调返回错误码为0后，
        // 再进行调用执行：mTraceClient.startGather(mTraceListener)，否则会出现服务开启失败12002的错误。
        mTraceClient.startTrace(mTrace, this);// 开启轨迹监听服务，第二个参数是：轨迹服务监听器

        //3、定时请求最新轨迹点
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                requestTrack();
            }
        }, 0, 10 * 1000);
    }

    /**
     * 查询历史轨迹
     */
    //设置轨迹查询起止时间
//    long startTime;// 开始时间(单位：秒)
//    long endTime;// 结束时间(单位：秒)
    public void requestTrack() {
        //设置轨迹查询起止时间
        HistoryTrackRequest historyTrackRequest = new HistoryTrackRequest(tag, serviceId, entityName);
        long startTime = System.currentTimeMillis() / 1000 - 5 * 60 * 60;//// 开始时间(单位：秒)：n个小时前
        long endTime = System.currentTimeMillis() / 1000;// 结束时间(单位：秒)：当前时间戳

//        endTime = System.currentTimeMillis() / 1000;//结束时间为本次查询时的时间，开始时间是点击启动按钮时候的时间
//        String start = timeStamp2Date(startTime + "", null);
//        String end = timeStamp2Date(endTime + "", null);
//        Log.d(TAG, "startTime:" + start);
//        Log.d(TAG, "endTime:" + end);

        historyTrackRequest.setStartTime(startTime);// 设置开始时间
        historyTrackRequest.setEndTime(endTime);// 设置结束时间

        //添加纠偏配置
        historyTrackRequest.setProcessed(true); // 设置需要纠偏
        ProcessOption processOption = new ProcessOption();// 创建纠偏选项实例
        processOption.setNeedDenoise(true);// 设置需要去噪
        processOption.setNeedVacuate(true);// 设置需要抽稀
        processOption.setNeedMapMatch(true);// 设置需要绑路
        processOption.setRadiusThreshold(100);// 设置精度过滤值(定位精度大于50米的过滤掉)
        processOption.setTransportMode(TransportMode.walking); // 设置交通方式
        //historyTrackRequest.setSupplementMode(SupplementMode.driving);// 设置里程填充方式为驾车
        historyTrackRequest.setProcessOption(processOption);// 设置纠偏选项

        //3、 开始查询历史轨迹
        if (null == mTraceClient) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "请先开启服务", Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }
        mTraceClient.queryHistoryTrack(historyTrackRequest, new OnTrackListener() {
            // 历史轨迹回调
            @Override
            public void onHistoryTrackCallback(HistoryTrackResponse response) {
                List<TrackPoint> trackPoints = response.trackPoints;
                //log信息
                if (null != trackPoints) {
                    Log.d(TAG, "trackPoints.size():" + trackPoints.size());
                    Toast.makeText(MainActivity.this, "trackPoints.size():" + trackPoints.size(), Toast.LENGTH_SHORT).show();
                }

                if (null != trackPoints && trackPoints.size() > 2 && trackPoints.size() < 10000) {//绘制折线必须：2< 点数 < 10000
                    List<LatLng> points = new ArrayList<>();
                    for (TrackPoint trackPoint : trackPoints) {
                        com.baidu.trace.model.LatLng latLng = trackPoint.getLocation();
                        //com.baidu.mapapi.model.LatLng 和 com.baidu.mapapi.trace.LatLng 居然是不一样的,注意导包
                        points.add(new LatLng(latLng.latitude, latLng.longitude));
                    }
                    //绘制折线
                    drawPolyline(points);
                } else {
                    Log.d(TAG, "trackPoints 数量 小于  2");
                }
            }
        });

    }


    //绘制折线
    public void drawPolyline(List<LatLng> points) {
        if (points.size() < 2) {
            Toast.makeText(this, "需要两个以上定位信息，才能绘制，请稍后...", Toast.LENGTH_SHORT).show();
            return;
        }
        //绘制折线,注意需要两个以上的点才能绘制，所以我们在前面做了判断
        mBaiduMap.clear();
        OverlayOptions ooPolyline = new PolylineOptions().width(10).color(0xAAFF0000).points(points);
        mBaiduMap.addOverlay(ooPolyline);
        Log.d(TAG, "draw");
    }

    /**
     * 将时间戳转成日期字符串
     */
    public static String timeStamp2Date(String seconds, String format) {
        if (seconds == null || seconds.isEmpty() || seconds.equals("null")) {
            return "";
        }
        if (format == null || format.isEmpty()) format = "yyyy-MM-dd HH:mm:ss";
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        return sdf.format(new Date(Long.valueOf(seconds + "000")));
    }


    //OnTraceListener接口回调：start
    @Override
    public void onBindServiceCallback(int i, String s) {

    }

    @Override
    public void onStartTraceCallback(int i, String s) {
        Log.d(TAG, "onStartTraceCallback: " + s + "  i=" + i);
        if (i == 0) {
//            Toast.makeText(this, "轨迹服务开启成功", Toast.LENGTH_LONG).show();
            if (null != mTraceClient) {
                mTraceClient.startGather(this);
            }
        }
    }

    @Override
    public void onStopTraceCallback(int i, String s) {

    }

    @Override
    public void onStartGatherCallback(int i, String s) {
        Log.d(TAG, "onStartGatherCallback: " + s + "  i=" + i);
        if (i == 0) {
            Toast.makeText(this, "轨迹采集开启成功", Toast.LENGTH_SHORT).show();
        }

    }

    @Override
    public void onStopGatherCallback(int i, String s) {
        Log.d(TAG, "onStopGatherCallback: " + s + "  i=" + i);

    }

    @Override
    public void onPushCallback(byte b, PushMessage pushMessage) {
        Log.d(TAG, "onPushCallback: " + pushMessage.toString());

    }

    @Override
    public void onInitBOSCallback(int i, String s) {
    }
    //OnTraceListener接口回调：end


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
            Log.e(TAG, "Successful");
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