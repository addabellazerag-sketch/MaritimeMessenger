package com.maritime.satellitemessenger

import android.Manifest
import android.annotation.SuppressLint
import android.location.Location
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.maritime.satellitemessenger.audio.Codec2Engine
import com.maritime.satellitemessenger.audio.VoiceRecorderPlayer
import com.maritime.satellitemessenger.network.MaritimeMessage
import com.maritime.satellitemessenger.network.PayloadPacker
import com.maritime.satellitemessenger.network.SatelliteManagerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val codecEngine = Codec2Engine()
    private lateinit var voiceTool: VoiceRecorderPlayer
    private lateinit var satHelper: SatelliteManagerHelper
    private val messagesList = mutableStateListOf<MaritimeMessage>()

    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceTool = VoiceRecorderPlayer(codecEngine)
        satHelper = SatelliteManagerHelper(this)

        requestPerms.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        setContent {
            MaritimeAppUI(
                messages = messagesList,
                onStartRecord = { startRecordingFlow() },
                onStopRecord = { stopAndSendFlow() },
                onPlay = { msg ->
                    lifecycleScope.launch { voiceTool.playCompressedAudio(msg.audioData) }
                }
            )
        }
    }

    private fun startRecordingFlow() {
        triggerHaptic()
        lifecycleScope.launch(Dispatchers.IO) {
            voiceTool.recordAndCompress()
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopAndSendFlow() {
        triggerHaptic()
        lifecycleScope.launch {
            val compressedData = withContext(Dispatchers.IO) {
                voiceTool.stopAndGetRecording()
            }

            if (compressedData.isNotEmpty()) {
                val fusedClient = LocationServices.getFusedLocationProviderClient(this@MainActivity)
                fusedClient.lastLocation.addOnSuccessListener { loc: Location? ->
                    val lat = loc?.latitude?.toFloat() ?: 35.93f
                    val lon = loc?.longitude?.toFloat() ?: 0.08f

                    val fullPayload = PayloadPacker.pack(lat, lon, compressedData)
                    satHelper.transmit(fullPayload) { success ->
                        if (success) {
                            val parsed = PayloadPacker.unpack(fullPayload)
                            messagesList.add(0, parsed)
                        }
                    }
                }
            }
        }
    }

    private fun triggerHaptic() {
        val vibrator = ContextCompat.getSystemService(this, Vibrator::class.java)
        vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

@Composable
fun MaritimeAppUI(
    messages: List<MaritimeMessage>,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onPlay: (MaritimeMessage) -> Unit
) {
    var isPressing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F141C))
    ) {
        Surface(
            color = Color(0xFF1B232F),
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "اللاسلكي الفضائي (أوفلاين)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "● جاهز للبث",
                    color = Color(0xFF00E676),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { msg ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF222C3C)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onPlay(msg) },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFF00E676), shape = CircleShape)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "تشغيل", tint = Color.Black)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text("نداء بحري صوتي", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${"%.3f".format(msg.latitude)}°N , ${"%.3f".format(msg.longitude)}°E",
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Text(
                            text = "${msg.audioData.size} B",
                            color = Color(0xFF80CBC4),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp, top = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(if (isPressing) Color(0xFFD32F2F) else Color(0xFF0288D1))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isPressing = true
                                onStartRecord()
                                tryAwaitRelease()
                                isPressing = false
                                onStopRecord()
                            }
                        )
                    }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Mic",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isPressing) "يتم البث..." else "اضغط مطولاً\nللتحدث",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
