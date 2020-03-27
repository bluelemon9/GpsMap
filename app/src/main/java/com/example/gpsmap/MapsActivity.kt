package com.example.gpsmap

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import org.jetbrains.anko.alert
import org.jetbrains.anko.noButton
import org.jetbrains.anko.toast
import org.jetbrains.anko.yesButton

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private val polylineOptions = PolylineOptions().width(5f).color(Color.RED)  // 이동 경로 표시선

    // 실행 중 권한 요청 메서드
    private val REQUEST_ACCESS_FINE_LOCATION = 1000

    private fun permissionCheck(cancel: () -> Unit, ok: () -> Unit){
        // 위치 권한이 있는지 검사
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            // 권한이 허용되지 않음
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)){
                // 이전에 권한을 한 번 거부한 적이 있는 경우
                cancel()
            } else{
                // 권한 요청
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_ACCESS_FINE_LOCATION)
            }
        } else{     // 권한을 수락했을 때
            ok()
        }
    }

    // 권한이 필요한 이유를 설명하는 다이러로그 표시 메서드
    private fun showPermissionInfoDialog() {
        alert("현재 위치 정보를 얻으려면 위치 권한이 필수로 필요합니다.", "권한이 필요한 이유"){
            yesButton {     // 권한 요청
                ActivityCompat.requestPermissions(this@MapsActivity,
                    arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_ACCESS_FINE_LOCATION)
            }
            noButton { }
        }.show()
    }

    // 권한 요청 처리
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode){
            REQUEST_ACCESS_FINE_LOCATION -> {  // grantResults[] 에는 요청한 권한들의 결과가 전달됨
                if((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)){
                    // 권한 허용됨
                    addLocationListener()
                } else{
                    // 권한 거부
                    toast("권한 거부 됨")
                }
                return
            }
        }
    }


    // 위치 정보를 주기적으로 얻는 데 필요한 객체
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: MyLocationCallBack

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)     // 화면이 꺼지지 않게 하기

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT     // 세로 모드로 화면 고정
        setContentView(R.layout.activity_maps
        )

        // SupportMapFragment를 가져와서 지도가 준비되면 알림 받음
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        locationInit()
    }

    // 위치 정보를 얻기 위한 각종 초기화
    private fun locationInit(){
        fusedLocationProviderClient = FusedLocationProviderClient(this)
        locationCallback = MyLocationCallBack()
        locationRequest = LocationRequest()     // 위치 정보를 요청하는 시간 주기를 설정하는 객체

        // GPS 우선
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY   // 가장 정확한 위치 요청
        // 업데이트 인터벌
        // 위치 정보가 없을 때는 업데이트 안 함
        // 상황에 따라 짧아질 수 있음, 정확하지 않음
        // 다른 앱에서 짧은 인터벌로 위치 정보를 요청하면 짧아질 수 있음
        locationRequest.interval = 10000    // 10초마다 위치 정보 갱신

        locationRequest.fastestInterval = 5000  // 정확함. 다른 앱에서 위치를 갱신했다면 5초마다 확인
    }


    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Add a marker in Sydney and move the camera
        val sydney = LatLng(-34.0, 151.0)
        mMap.addMarker(MarkerOptions().position(sydney).title("Marker in Sydney"))
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney))
    }

    override fun onResume(){    // 위치 정보 요청 수행
        super.onResume()

        // 권한 요청
        permissionCheck(cancel = {
            showPermissionInfoDialog()  // 위치 정보가 필요한 이유 다이얼로그 표시
        }, ok = {
            addLocationListener()       // 현재 위치를 주기적으로 요청 (권한이 필요한 부분)
        })
    }

    // 위치 정보 요청 삭제
    override fun onPause() {
        super.onPause()
        removeLocationListener()    // 위치 요청 삭제
    }

    private fun removeLocationListener(){
        fusedLocationProviderClient.removeLocationUpdates(locationCallback) // 현재 위치 요청 삭제
    }

    private fun addLocationListener(){
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }

    inner class MyLocationCallBack : LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult?){
            super.onLocationResult(locationResult)

            val location = locationResult?.lastLocation     // lastLocation 프로퍼티로 최근 현재 위치에 대한 Location 객체 얻음(위도, 경도 정보)

            location?.run{
                // 14 level로 확대하고 현재 위치로 카메라 이동
                val latLng = LatLng(latitude, longitude)
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))

                Log.d("MapsActivity", "위도: $latitude, 경도: $longitude")  // 위도, 경도값 로그로 출력

                polylineOptions.add(latLng)            // PolyLine에 좌표 추가
                mMap.addPolyline(polylineOptions)     // 선 그리기
            }
        }
    }
}
