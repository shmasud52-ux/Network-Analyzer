package com.networkanalyzer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.telephony.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

private val bg = Color(0xFF080F18)
private val panel = Color(0xFF111E2A)
private val cyan = Color(0xFF45E5D0)
private val muted = Color(0xFF91A5B6)

data class TestRecord(val date:String,val down:String,val up:String,val ping:String)

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var heading by mutableFloatStateOf(0f)
    private var signal by mutableIntStateOf(-120)
    private var signalInfo by mutableStateOf("Signal not available yet")
    private var connection by mutableStateOf("Checking…")
    private var bestHeading by mutableFloatStateOf(0f)
    private var bestSignal by mutableIntStateOf(-120)
    private val samples = mutableStateMapOf<Int,Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        updateConnection()
        setContent { NetworkApp(heading,signal,signalInfo,connection,bestHeading,bestSignal,samples,
            ::readSignal,::saveSample) }
    }
    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sensorManager.registerListener(this,it,SensorManager.SENSOR_DELAY_UI)
        }
    }
    override fun onPause() { super.onPause(); sensorManager.unregisterListener(this) }
    override fun onSensorChanged(event:SensorEvent) {
        if(event.sensor.type==Sensor.TYPE_ROTATION_VECTOR) {
            val matrix=FloatArray(9); val orientation=FloatArray(3)
            SensorManager.getRotationMatrixFromVector(matrix,event.values)
            SensorManager.getOrientation(matrix,orientation)
            heading=(Math.toDegrees(orientation[0].toDouble()).toFloat()+360f)%360f
        }
    }
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int) {}
    private fun updateConnection() {
        val cm=getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps=cm.getNetworkCapabilities(cm.activeNetwork)
        connection=when {
            caps==null -> "No network"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
            else -> "Connected"
        }
    }
    private fun readSignal() {
        updateConnection()
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED) {
            signalInfo="Allow Location and Phone permissions to read signal"; return
        }
        try {
            val tm=getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val cell=tm.allCellInfo?.firstOrNull { it.isRegistered } ?: tm.allCellInfo?.firstOrNull()
            if(cell==null) { signalInfo="Cell signal unavailable. Turn on Location."; return }
            signal=cell.cellSignalStrength.dbm
            signalInfo=when(cell) {
                is CellInfoLte -> "4G LTE • RSRP ${cell.cellSignalStrength.rsrp} dBm • RSRQ ${cell.cellSignalStrength.rsrq} dB"
                is CellInfoNr -> "5G NR • ${signal} dBm"
                is CellInfoWcdma -> "3G • ${signal} dBm"
                is CellInfoGsm -> "2G • ${signal} dBm"
                else -> "Cellular • ${signal} dBm"
            }
        } catch(_:Exception) { signalInfo="Signal information unavailable on this phone" }
    }
    private fun saveSample() {
        readSignal()
        val degree=((heading.roundToInt()+360)%360/15)*15
        samples[degree]=signal
        if(signal>bestSignal) { bestSignal=signal; bestHeading=degree.toFloat() }
    }
}

@Composable
private fun NetworkApp(
    heading:Float,signal:Int,signalInfo:String,connection:String,bestHeading:Float,bestSignal:Int,
    samples:Map<Int,Int>,readSignal:()->Unit,saveSample:()->Unit
) {
    var tab by remember { mutableStateOf("Home") }
    var down by remember { mutableStateOf("—") }
    var up by remember { mutableStateOf("—") }
    var ping by remember { mutableStateOf("—") }
    var status by remember { mutableStateOf("Ready") }
    var running by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(emptyList<TestRecord>()) }
    val context=androidx.compose.ui.platform.LocalContext.current
    val scope= rememberCoroutineScope()
    val permission= rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { readSignal() }
    LaunchedEffect(Unit) {
        history=loadHistory(context)
        while(true) { readSignal(); kotlinx.coroutines.delay(5000) }
    }
    MaterialTheme(colorScheme=darkColorScheme(primary=cyan,secondary=Color(0xFF91F2C3),background=bg,surface=panel)) {
        Column(Modifier.fillMaxSize().background(bg)) {
            Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Default.Wifi,color=cyan,contentDescription=null,modifier=Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("NETWORK ANALYZER",fontWeight=FontWeight.Bold,letterSpacing=1.sp)
                    Text("Speed • Signal • Direction",color=muted,fontSize=11.sp)
                }
                Text("LIVE",color=Color(0xFF91F2C3),fontWeight=FontWeight.Bold)
            }
            NavigationBar(containerColor=bg) {
                listOf("Home","Speed","Signal","History").forEach { name ->
                    NavigationBarItem(selected=tab==name,onClick={tab=name},
                        icon={Icon(when(name) {
                            "Speed" -> Icons.Default.Speed
                            "Signal" -> Icons.Default.Sensors
                            "History" -> Icons.Default.History
                            else -> Icons.Default.Home
                        },contentDescription=name)},
                        label={Text(name,fontSize=10.sp)})
                }
            }
            when(tab) {
                "Home" -> LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    item { Panel {
                        Text("CURRENT CONNECTION",color=muted,fontSize=11.sp)
                        Text(connection,fontSize=25.sp,fontWeight=FontWeight.Bold)
                        Text(signalInfo,color=muted,fontSize=12.sp)
                        Button(onClick={permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.READ_PHONE_STATE))},modifier=Modifier.fillMaxWidth()) { Text("Allow permissions / Refresh") }
                    } }
                    item { Panel {
                        Text("LATEST SPEED TEST",color=muted,fontSize=11.sp)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Value("Download",down,"Mbps"); Value("Upload",up,"Mbps"); Value("Ping",ping,"ms")
                        }
                        Button(onClick={tab="Speed"},modifier=Modifier.fillMaxWidth()) { Text("Open Speed Test") }
                    } }
                    item { Panel {
                        Text("SIGNAL DIRECTION",color=muted,fontSize=11.sp)
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Compass(heading,Modifier.size(120.dp))
                            Column {
                                Text("${heading.roundToInt()}°",fontSize=26.sp,fontWeight=FontWeight.Bold)
                                Text(direction(heading),color=cyan)
                                Text("Best: ${bestHeading.roundToInt()}°",color=muted)
                                Text("Signal: $signal dBm",color=signalTint(signal))
                            }
                        }
                        Button(onClick={tab="Signal"},modifier=Modifier.fillMaxWidth()) { Text("Open Signal Finder") }
                    } }
                }
                "Speed" -> LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp)) {
                    item { Panel {
                        Text("INTERNET SPEED TEST",fontSize=20.sp,fontWeight=FontWeight.Bold)
                        Text("Public Cloudflare test server • no API key",color=muted,fontSize=11.sp)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Value("Download",down,"Mbps"); Value("Upload",up,"Mbps")
                        }
                        Value("Ping",ping,"ms")
                        Button(enabled=!running,onClick={
                            running=true; status="Testing download, upload and latency…"
                            scope.launch {
                                try {
                                    val result=runSpeedTest()
                                    down=result.first; up=result.second; ping=result.third
                                    val rec=TestRecord(SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault()).format(Date()),down,up,ping)
                                    history=listOf(rec)+history.take(49); saveHistory(context,history)
                                    status="Test completed and saved"
                                } catch(_:Exception) { status="Test failed. Check internet and retry." }
                                running=false
                            }
                        },modifier=Modifier.fillMaxWidth()) {
                            if(running) CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp) else Icon(Icons.Default.PlayArrow,null)
                            Spacer(Modifier.width(8.dp)); Text(if(running) "Testing…" else "Start Speed Test")
                        }
                        Text(status,color=muted,fontSize=12.sp)
                        Text("Uses mobile data. Charges may apply.",color=muted,fontSize=10.sp)
                    } }
                }
                "Signal" -> LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    item { Panel {
                        Text("LIVE CELLULAR SIGNAL",color=muted)
                        Text("$signal dBm",fontSize=36.sp,fontWeight=FontWeight.Bold,color=signalTint(signal))
                        Text(signalInfo,color=muted,fontSize=12.sp)
                        Button(onClick={permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.READ_PHONE_STATE))},modifier=Modifier.fillMaxWidth()) { Text("Grant permissions") }
                    } }
                    item { Panel {
                        Text("360° SIGNAL DIRECTION FINDER",fontWeight=FontWeight.Bold)
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Compass(heading,Modifier.size(145.dp))
                            Column {
                                Text("${heading.roundToInt()}°",fontSize=26.sp)
                                Text(direction(heading),color=cyan)
                                Text("Best ${bestHeading.roundToInt()}°",color=muted)
                                Text("$bestSignal dBm",color=signalTint(bestSignal))
                            }
                        }
                        Button(onClick=saveSample,modifier=Modifier.fillMaxWidth()) { Text("Save signal at this direction") }
                        Text("Turn slowly, pause, then save a reading in each direction.",color=muted,fontSize=11.sp)
                    } }
                    item { Panel {
                        Text("SAVED DIRECTION SAMPLES (${samples.size})",fontWeight=FontWeight.Bold)
                        samples.toSortedMap().forEach { (degree,dbm) ->
                            Row(Modifier.fillMaxWidth().padding(5.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                                Text("$degree° • ${direction(degree.toFloat())}",color=muted)
                                Text("$dbm dBm",color=signalTint(dbm))
                            }
                        }
                    } }
                }
                else -> LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    item { Panel { Text("TEST HISTORY",fontSize=20.sp,fontWeight=FontWeight.Bold); Text("Last 50 tests saved on this phone",color=muted) } }
                    items(history) { item -> Panel {
                        Text(item.date,color=muted,fontSize=11.sp)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Value("Download",item.down,"Mbps"); Value("Upload",item.up,"Mbps"); Value("Ping",item.ping,"ms")
                        }
                    } }
                }
            }
        }
    }
}

@Composable private fun Panel(content:@Composable ColumnScope.()->Unit) {
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=panel)) {
        Column(Modifier.padding(17.dp),content=content)
    }
}
@Composable private fun Value(label:String,value:String,unit:String) {
    Column(Modifier.padding(vertical=8.dp)) {
        Text(label.uppercase(),fontSize=9.sp,color=muted,fontWeight=FontWeight.Bold)
        Row(verticalAlignment=Alignment.Bottom) { Text(value,fontSize=22.sp,fontWeight=FontWeight.Bold); Text(" $unit",fontSize=10.sp,color=muted) }
    }
}
@Composable private fun Compass(degree:Float,modifier:Modifier=Modifier) {
    Canvas(modifier) {
        val c=Offset(size.width/2,size.height/2); val r=min(size.width,size.height)*.42f
        drawCircle(Color(0xFF1B3340),r,style=Stroke(3.dp.toPx()))
        for(i in 0 until 36) {
            val a=Math.toRadians((i*10-90).toDouble()); val inner=r*if(i%3==0).82f else .9f
            drawLine(muted,Offset(c.x+cos(a).toFloat()*inner,c.y+sin(a).toFloat()*inner),Offset(c.x+cos(a).toFloat()*r,c.y+sin(a).toFloat()*r),1.5.dp.toPx())
        }
        val a=Math.toRadians((degree-90).toDouble())
        drawLine(cyan,c,Offset(c.x+cos(a).toFloat()*r*.72f,c.y+sin(a).toFloat()*r*.72f),5.dp.toPx(),StrokeCap.Round)
        drawCircle(Color(0xFF91F2C3),5.dp.toPx(),c)
    }
}
private fun direction(d:Float)=when(((d+22.5f)%360f).toInt()/45) {
    0->"North";1->"North-East";2->"East";3->"South-East";4->"South";5->"South-West";6->"West";else->"North-West"
}
private fun signalTint(d:Int)=when { d>=-85->Color(0xFF91F2C3); d>=-100->cyan; else->Color(0xFFFFB86B) }

private suspend fun runSpeedTest():Triple<String,String,String> = withContext(Dispatchers.IO) {
    val base="https://speed.cloudflare.com"
    val times=mutableListOf<Long>()
    repeat(5) {
        val start=System.nanoTime()
        val c=URL("$base/__down?bytes=1000&cachebust=${System.nanoTime()}").openConnection() as HttpURLConnection
        c.connectTimeout=8000; c.readTimeout=8000
        c.inputStream.use { it.readBytes() }; c.disconnect()
        times.add((System.nanoTime()-start)/1_000_000)
    }
    val startDown=System.nanoTime()
    val dc=URL("$base/__down?bytes=8000000&cachebust=${System.nanoTime()}").openConnection() as HttpURLConnection
    dc.connectTimeout=15000; dc.readTimeout=30000
    val bytes=dc.inputStream.use { input ->
        val buffer=ByteArray(65536); var total=0L
        while(true) { val n=input.read(buffer); if(n<0) break; total+=n }; total
    }
    dc.disconnect()
    val d=bytes*8.0/((System.nanoTime()-startDown)/1e9)/1_000_000.0
    val payload=ByteArray(1_000_000); val startUp=System.nanoTime()
    val uc=URL("$base/__up").openConnection() as HttpURLConnection
    uc.requestMethod="POST"; uc.doOutput=true; uc.connectTimeout=15000; uc.readTimeout=30000
    uc.setRequestProperty("Content-Type","application/octet-stream")
    uc.setFixedLengthStreamingMode(payload.size); uc.outputStream.use { it.write(payload) }
    val code=uc.responseCode
    if(code !in 200..299) error("Upload failed: $code")
    uc.inputStream?.close(); uc.disconnect()
    val u=payload.size*8.0/((System.nanoTime()-startUp)/1e9)/1_000_000.0
    Triple("%.1f".format(Locale.US,d),"%.1f".format(Locale.US,u),"%.0f".format(Locale.US,times.average()))
}
private fun loadHistory(ctx:Context):List<TestRecord> = try {
    val a=org.json.JSONArray(ctx.getSharedPreferences("network_analyzer",0).getString("history","[]"))
    (0 until a.length()).map { i -> val o=a.getJSONObject(i); TestRecord(o.getString("date"),o.getString("down"),o.getString("up"),o.getString("ping")) }
} catch(_:Exception) { emptyList() }
private fun saveHistory(ctx:Context,list:List<TestRecord>) {
    val a=org.json.JSONArray()
    list.forEach { r -> a.put(org.json.JSONObject().put("date",r.date).put("down",r.down).put("up",r.up).put("ping",r.ping)) }
    ctx.getSharedPreferences("network_analyzer",0).edit().putString("history",a.toString()).apply()
}
