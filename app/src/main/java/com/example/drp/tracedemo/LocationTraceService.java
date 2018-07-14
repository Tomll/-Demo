package com.example.drp.tracedemo;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.utils.DistanceUtil;
import com.baidu.trace.LBSTraceClient;
import com.baidu.trace.Trace;
import com.baidu.trace.api.track.HistoryTrackRequest;
import com.baidu.trace.api.track.HistoryTrackResponse;
import com.baidu.trace.api.track.OnTrackListener;
import com.baidu.trace.api.track.SupplementMode;
import com.baidu.trace.api.track.TrackPoint;
import com.baidu.trace.model.OnTraceListener;
import com.baidu.trace.model.ProcessOption;
import com.baidu.trace.model.PushMessage;
import com.baidu.trace.model.TransportMode;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class LocationTraceService extends Service implements BDLocationListener, OnTraceListener {
    public static final String TAG = "LocationTraceService";
    LocationClient mLocationClient;//定位客户端
    Trace mTrace;//轨迹服务
    LBSTraceClient mTraceClient;//轨迹服务客户端
    int transportMode;
    String stampToDate;//本次服务启动的时间

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        //出行方式  0 汽车 1 骑行 2 步行
        transportMode = intent.getIntExtra("transportMode", 2);
        startForeground();//开启前台服务
        startLocation();//定位服务在各种出行方式下都需要开启
//        if (transportMode == 0) {//如果是驾车方式，还需要开启鹰眼轨迹
//            startTrace(transportMode);
//        }
        stampToDate = stampToDate(System.currentTimeMillis() + "");
        return new MyBinder();
    }

    //自定义Binder用于进程内、或跨进程传输LocationTraceService对象
    public class MyBinder extends Binder {
        public LocationTraceService getLocationTraceService() {
            return LocationTraceService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
    }

    //开启前台服务的方法
    public void startForeground() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Intent intent1 = new Intent(this, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(this, 0, intent1, 0);
            Notification notification = new NotificationCompat.Builder(this)
                    .setContentTitle("智邦国际")
                    .setContentText("行动轨迹服务正在运行，点击查看详情")
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
                    .setContentIntent(pi)
                    .build();
            startForeground(22, notification);
            Log.d(TAG, "开启前台服务");
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        if (null != mLocationClient && mLocationClient.isStarted()) {
            mLocationClient.stop();
            Log.d(TAG, "关闭定位");
        }
        if (null != mTraceClient && null != mTrace) {
            mTraceClient.stopTrace(mTrace, this);
            Log.d(TAG, "关闭轨迹");

        }
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }


    //定位初始化，然后开启定位服务
    public void startLocation() {
        //定位客户端（定位管理器）
        mLocationClient = new LocationClient(LocationTraceService.this);
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

    //定位点集合
    ArrayList<BDLocation> bdLocations = new ArrayList<>();

    //BDLocationListener接口回调：回调定位信息
    @Override
    public void onReceiveLocation(BDLocation bdLocation) {
        MyLocationPoint myLocationPoint = new MyLocationPoint(bdLocation.getLatitude(), bdLocation.getLongitude(), bdLocation.getAddrStr(), bdLocation.getLocationDescribe());
        addLocationLog(myLocationPoint.toString()+"\n");

        //定位点有效性判断
        locationPointValidityJudgment(bdLocation);
        //回调定位数据给监听者
        myLocationDataListener.onLocationDataChange(bdLocation, bdLocations);

//        String addrStr = bdLocation.getAddrStr();
//        LatLng latLng = new LatLng(bdLocation.getLatitude(), bdLocation.getLongitude());
//        locationPoints.add(latLng);
//        tvAddress.setText("纬度：" + latLng.latitude + "\n经度：" + latLng.longitude + "\n地址：" + addrStr + "\n描述：" + bdLocation.getLocationDescribe());
//        Log.d(TAG, "纬度：" + latLng.latitude + " 经度：" + latLng.longitude);
//        Log.d(TAG, "addrStr:" + addrStr + "  LocationDescribe: " + bdLocation.getLocationDescribe());
//        addLog(LocationTraceService.this, "定位信息：\n" + "纬度：" + latLng.latitude + "\n经度：" + latLng.longitude + "\n地址信息：" + addrStr);

/*        // 开启定位图层
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
        mBaiduMap.setMyLocationData(locData);*/

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

    //记录定位日志
    public void addLocationLog(String content) {
        try {
            File fs = new File(Environment.getExternalStorageDirectory() + "/locLog" +stampToDate + ".txt");
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

    }

    private MyLocationDataListener myLocationDataListener;

    public interface MyLocationDataListener {
        //参数1：本次定位的实时位置 ，参数2：历史定位信息集合
        void onLocationDataChange(BDLocation bdLocation, ArrayList<BDLocation> hisBDLocations);
    }

    public void setMyLocationDataListener(MyLocationDataListener listener) {
        myLocationDataListener = listener;
    }

    /**
     * 各种运动模式下：定位点有效性判断
     */
    public void locationPointValidityJudgment(BDLocation bdLocation) {
        //当前时间戳
        long nowTime = System.currentTimeMillis();
        if (bdLocations.size() == 0) {
            bdLocations.add(bdLocation);//添加起点
            return;
        }
        //当前定位点
        LatLng latLng = new LatLng(bdLocation.getLatitude(), bdLocation.getLongitude());
        //历史最新点
        LatLng historyLatestLatLng = new LatLng(bdLocations.get(bdLocations.size() - 1).getLatitude(), bdLocations.get(bdLocations.size() - 1).getLongitude());
        //历史最新点的定位时间
        long historyLatestTime = Long.valueOf(dateToStamp(bdLocations.get(bdLocations.size() - 1).getTime()));
        //两次定位 时间差（单位 s）
        long locTime = (nowTime - historyLatestTime) / 1000;
        //以下逻辑用于对定位点进行去噪、抽稀
        //Log.d(TAG, "Distance = " + DistanceUtil.getDistance(latLng, historyLatestLatLng));
        //Toast.makeText(this, "Distance = " + DistanceUtil.getDistance(latLng, historyLatestLatLng), Toast.LENGTH_SHORT).show();
        if (DistanceUtil.getDistance(latLng, historyLatestLatLng) < 1) {//两次定位点间距小于2m,抽稀：降低点密度
            Log.d(TAG, "静止不动，无效点，舍弃本次定位点");
            return;
        }
        if (transportMode == 2 && DistanceUtil.getDistance(latLng, historyLatestLatLng) <= locTime * 3) {//步行极速 3m/s 10.8km/h
            bdLocations.add(bdLocation);
            //Toast.makeText(this, "步行记录", Toast.LENGTH_SHORT).show();
        } else if (transportMode == 1 && DistanceUtil.getDistance(latLng, historyLatestLatLng) <= locTime * 10) {//骑行极速 10/ms  36km/h
            bdLocations.add(bdLocation);
            //Toast.makeText(this, "骑行记录", Toast.LENGTH_SHORT).show();
        } else if (transportMode == 0 && DistanceUtil.getDistance(latLng, historyLatestLatLng) <= locTime * 36) {//驾车 36m/s  130km/h
            bdLocations.add(bdLocation);
            //Toast.makeText(this, "驾车记录", Toast.LENGTH_SHORT).show();
        }
    }


    //将日期转换为时间戳
    public static String dateToStamp(String s) {
        String res;
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = null;
        try {
            date = simpleDateFormat.parse(s);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        long ts = date.getTime();
        res = String.valueOf(ts);
        return res;
    }

    //将时间戳转换为日期
    public static String stampToDate(String s) {
        String res;
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        long lt = new Long(s);
        Date date = new Date(lt);
        res = simpleDateFormat.format(date);
        return res;
    }


    /**
     * 轨迹监听初始化，然后开启轨迹监听服务
     */
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
//    int tag = 5; //请求标识
//    String entityName = "yingyan_demo_5"; //设备标识
//    int tag = 6; //请求标识
//    String entityName = "yingyan_demo_xiaomi3c"; //设备标识
//    int tag = 7; //请求标识
//    String entityName = "yingyan_demo_7"; //设备标识
//    int tag = 8; //请求标识
//    String entityName = "yingyan_demo_8"; //设备标识
//    int tag = 9; //请求标识
//    String entityName = "yingyan_demo_9"; //设备标识
//    int tag = 10; //请求标识 书俊
//    String entityName = "yingyan_demo_10"; //设备标识
//    int tag = 11; //请求标识 高峰
//    String entityName = "yingyan_demo_11"; //设备标识
//    int tag = 12; //请求标识
//    String entityName = "yingyan_demo_12"; //设备标识
//    int tag = 13; //请求标识
//    String entityName = "yingyan_demo_13"; //设备标识
//    int tag = 14; //请求标识
//    String entityName = "yingyan_demo_14"; //设备标识

    public void startTrace(final int transportMode) {
        boolean isNeedObjectStorage = false;//是否需要对象存储服务，默认为：false，关闭对象存储服务。注：鹰眼 Android SDK v3.0以上
        // 版本支持随轨迹上传图像等对象数据，若需使用此功能，该参数需设为 true，且需导入bos-android-sdk-1.0.2.jar。
        mTrace = new Trace(serviceId, entityName, isNeedObjectStorage);//初始化轨迹服务
        mTraceClient = new LBSTraceClient(LocationTraceService.this);//初始化轨迹服务客户端
        //1、设置定位、打包回传周期
        int gatherInterval = 5;//定位周期(单位:秒)
        int packInterval = 10;//打包回传周期(单位:秒)
        mTraceClient.setInterval(gatherInterval, packInterval);//设置定位和打包周期
        //2、开启轨迹监听服务
        //注：因为startTrace与startGather是异步执行，且startGather依赖startTrace执行开启服务成功，
        // 所以建议startGather在public void onStartTraceCallback(int errorNo, String message)回调返回错误码为0后，
        // 再进行调用执行：mTraceClient.startGather(mTraceListener)，否则会出现服务开启失败12002的错误。
        mTraceClient.startTrace(mTrace, this);// 开启轨迹监听服务，第二个参数是：轨迹服务监听器
    }

    /**
     * 查询历史鹰眼轨迹
     */
    public ArrayList<LatLng> requestTrack(int transportMode) {
        //结果集合
        final ArrayList<LatLng> points = new ArrayList<>();
        //设置轨迹查询起止时间
        HistoryTrackRequest historyTrackRequest = new HistoryTrackRequest(tag, serviceId, entityName);
        long startTime = System.currentTimeMillis() / 1000 - 5 * 60 * 60;//// 开始时间(单位：秒)：n个小时前
        long endTime = System.currentTimeMillis() / 1000;// 结束时间(单位：秒)：当前时间戳
        historyTrackRequest.setStartTime(startTime);// 设置开始时间
        historyTrackRequest.setEndTime(endTime);// 设置结束时间
        //添加纠偏配置
        historyTrackRequest.setProcessed(true); // 设置需要纠偏
        ProcessOption processOption = new ProcessOption();// 创建纠偏选项实例
        processOption.setNeedDenoise(true);// 设置需要去噪
        processOption.setNeedVacuate(true);// 设置需要抽稀
        processOption.setRadiusThreshold(20);// 设置精度过滤值(定位精度大于50米的过滤掉)
        switch (transportMode) {
            case 0:
                Log.d(TAG, "查询驾车记录");
                processOption.setNeedMapMatch(true);// 设置需要绑路
                processOption.setTransportMode(TransportMode.driving); // 设置交通方式为 驾车
                historyTrackRequest.setSupplementMode(SupplementMode.driving);// 设置里程填充方式为驾车
                break;
            case 1:
                Log.d(TAG, "查询骑行记录");
                processOption.setTransportMode(TransportMode.riding); // 设置交通方式为 骑行
                historyTrackRequest.setSupplementMode(SupplementMode.riding);// 设置里程填充方式为骑行
                break;
            case 2:
                Log.d(TAG, "查询步行记录");
                processOption.setTransportMode(TransportMode.walking); // 设置交通方式为 步行
                historyTrackRequest.setSupplementMode(SupplementMode.walking);// 设置里程填充方式为步行

                break;
        }
        historyTrackRequest.setProcessOption(processOption);// 设置纠偏选项
        //开始查询历史轨迹
        mTraceClient.queryHistoryTrack(historyTrackRequest, new OnTrackListener() {
            @Override
            public void onHistoryTrackCallback(HistoryTrackResponse response) {
                List<TrackPoint> trackPoints = response.trackPoints;
                if (null != trackPoints && trackPoints.size() > 2 && trackPoints.size() < 10000) {//绘制折线必须：2< 点数 < 10000
                    for (TrackPoint trackPoint : trackPoints) {
                        com.baidu.trace.model.LatLng latLng = trackPoint.getLocation();
                        //com.baidu.mapapi.model.LatLng 和 com.baidu.mapapi.trace.LatLng 居然是不一样的,注意导包
                        points.add(new LatLng(latLng.latitude, latLng.longitude));
                    }
                }
            }
        });
        return points;
    }

    //OnTraceListener接口回调：start
    @Override
    public void onBindServiceCallback(int i, String s) {

    }

    @Override
    public void onStartTraceCallback(int i, String s) {
        Log.d(TAG, "onStartTraceCallback: " + s + "  i=" + i);
        if (i == 0) {
            mTraceClient.startGather(this);
        }
    }

    @Override
    public void onStopTraceCallback(int i, String s) {

    }

    @Override
    public void onStartGatherCallback(int i, String s) {
        Log.d(TAG, "onStartGatherCallback: " + s + "  i=" + i);
        /*if (i == 0) {
            Toast.makeText(this, "轨迹采集开启成功", Toast.LENGTH_LONG).show();
        }*/
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


}

