package com.example.rakez.pedometerdemo

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.fitness.data.DataPoint
import com.google.android.gms.fitness.request.OnDataPointListener
import com.google.android.gms.fitness.request.SensorRequest
import kotlinx.android.synthetic.main.activity_main.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ResultCallback
import com.google.android.gms.fitness.request.DataReadRequest
import java.util.*
import java.util.concurrent.TimeUnit
import com.google.android.gms.fitness.data.DataSet
import com.google.android.gms.fitness.data.Value
import com.google.android.gms.fitness.result.DataReadResult
import java.text.DateFormat.getTimeInstance
import java.text.SimpleDateFormat


class MainActivity : AppCompatActivity(), GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, OnDataPointListener, ResultCallback<DataReadResult> {

    var mGoogleApiClient: GoogleApiClient? = null
    val RC_FITNESS_SIGNIN = 3
    var stepCounter = 0
    var stepCounterWeek = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setData()
        if(!GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(this))) {
            var gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
            GoogleSignIn.requestPermissions(
                    this, // your activity instance
                    RC_FITNESS_SIGNIN,
                    GoogleSignIn.getLastSignedInAccount(this),
                    Fitness.SCOPE_ACTIVITY_READ_WRITE)

        }else{
            connectToApi()
        }
       /* mGoogleApiClient = builder.build()
        var fitnessOptions =
        if(!GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(this))) {
            GoogleSignIn.requestPermissions(
                    this, // your activity instance
                    GoogleSignInOptionsExtension.FITNESS,
                    GoogleSignIn.getLastSignedInAccount(this),
                    )
        }
        mGoogleApiClient = GoogleApiClient.Builder(this)
                .addApi(Fitness.SENSORS_API)  // Required for SensorsApi calls
                // Optional: specify more APIs used with additional calls to addApi
                .useDefaultAccount()
                .addScope(Scope(Scopes.FITNESS_ACTIVITY_READ_WRITE))
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build()
        mGoogleApiClient!!.connect()
*/
//        val result = Fitness.HistoryApi.readDailyTotal(mGoogleApiClient, DataType.TYPE_STEP_COUNT_DELTA).await(1, TimeUnit.MINUTES)

    }

    fun connectToApi(){
        mGoogleApiClient = GoogleApiClient.Builder(this)
                .addApi(Fitness.RECORDING_API)
                .addApi(Fitness.HISTORY_API)
                .addApi(Fitness.SENSORS_API)  // Required for SensorsApi calls
                // Optional: specify more APIs used with additional calls to addApi
                .useDefaultAccount()
                .addScope(Scope(Scopes.FITNESS_ACTIVITY_READ_WRITE))
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build()
        mGoogleApiClient!!.connect()
    }

    private fun subscribeToRecordAPI() {
        Fitness.getRecordingClient(this, GoogleSignIn.getLastSignedInAccount(this)!!)
                .subscribe(DataType.TYPE_STEP_COUNT_DELTA)
                .addOnSuccessListener { d("test", "Successfully subscribed!") }
                .addOnFailureListener { d("test", "There was a problem subscribing.")
                }
        SettingVariable.isFitnessApiSubscribed = true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == RC_FITNESS_SIGNIN){
            d("test", "data called")
            connectToApi()
        }
    }

    override fun onConnected(p0: Bundle?) {
        d("test","api client connected")
        //only call this if not subscribed already
        if(!SettingVariable.isFitnessApiSubscribed)
            subscribeToRecordAPI()
        readStepsHistory()
    }

    private fun requestLivaDataUpdate(){
        stepCountTextView.text = stepCounter.toString()
        Fitness.SensorsApi.add(
                mGoogleApiClient,
                SensorRequest.Builder()
                        .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
                        .build(),
                this)
    }

    private fun readStepsHistory() {
        var weekInMS: Long = 7 * 24 * 60 * 60 * 1000
        var date = Date()
        var endTime = date.time
        var startTime = endTime - weekInMS
        val readRequest = DataReadRequest.Builder()
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                .bucketByTime(1  , TimeUnit.DAYS)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build()
        //val response = Fitness.getHistoryClient(this, GoogleSignIn.getLastSignedInAccount(this)!!).readData(readRequest)
        val pendingResult = Fitness.HistoryApi.readData(mGoogleApiClient,readRequest)
        pendingResult.setResultCallback(this)
    }

    override fun onResult(dataReadResult: DataReadResult) {
        d("test", "Data returned for Data type: " + dataReadResult.toString())
        if(dataReadResult.buckets.size > 0){
            dataReadResult.buckets.forEach {
                var dataSets = it.dataSets
                dataSets.forEach {
                    processDataSet(it)
                }
            }
        }

    }


    private fun processDataSet(dataSet: DataSet) {
        d("test", "Data returned for Data type: " + dataSet.dataType.name)
        val dateFormat = SimpleDateFormat("YYYY-MM-dd hh:mm:SS aa")

        for (dp in dataSet.dataPoints) {
            d("test", "Data point:")
            d("test", "\tType: " + dp.dataType.name)
            d("test", "\tStart: " + dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)))
            d("test", "\tEnd: " + dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)))
            for (field in dp.dataType.fields) {
                d("test", "\tField: " + field.name + " Value: " + dp.getValue(field))
                setInitialData(dp.getValue(field))
            }
        }
    }

    private fun setInitialData(value: Value) {
        stepCounter = value.asInt()
        d("test", "value $stepCounter")
        requestLivaDataUpdate()
    }


    override fun onConnectionSuspended(p0: Int) {
        d("test","connection suspended")

    }

    override fun onConnectionFailed(p0: ConnectionResult) {
        d("test", "connection failed ${p0}")
    }

    override fun onDataPoint(datapoint: DataPoint?) {
        d("test","data point received")
        d(datapoint.toString())
        stepCounter++
        setData()
    }

    private fun setData() {
        stepCountTextView.text = stepCounter.toString()
    }
}