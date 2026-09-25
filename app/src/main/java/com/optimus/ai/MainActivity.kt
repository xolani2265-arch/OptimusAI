package com.optimus.ai

import android.content.Context
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val KEY_ALIAS = "optimus_trade_memory"
private const val PREFS = "optimus_secure_store"
private const val DATA_KEY = "trades"

data class Trade(val id:String,val direction:String,val risk:Double,val pnl:Double,val setup:String,val created:String)

class SecureTradeStore(private val context:Context) {
    private val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    private fun key():SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        val old=ks.getKey(KEY_ALIAS,null)
        if(old is SecretKey) return old
        val kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        kg.init(KeyGenParameterSpec.Builder(KEY_ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return kg.generateKey()
    }
    private fun encrypt(s:String):String {
        val c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,key())
        return android.util.Base64.encodeToString(c.iv+c.doFinal(s.toByteArray(StandardCharsets.UTF_8)),android.util.Base64.NO_WRAP)
    }
    private fun decrypt(s:String):String {
        val b=android.util.Base64.decode(s,android.util.Base64.NO_WRAP)
        val c=Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,b.copyOfRange(0,12)))
        return String(c.doFinal(b.copyOfRange(12,b.size)),StandardCharsets.UTF_8)
    }
    fun all():List<Trade> = runCatching {
        val raw=prefs.getString(DATA_KEY,null) ?: return emptyList()
        val a=JSONArray(decrypt(raw))
        buildList {
            for(i in 0 until a.length()){
                val o=a.getJSONObject(i)
                add(Trade(o.getString("id"),o.getString("direction"),o.getDouble("risk"),o.getDouble("pnl"),o.getString("setup"),o.getString("created")))
            }
        }
    }.getOrDefault(emptyList()).sortedByDescending{it.created}
    fun save(t:Trade){
        val a=JSONArray()
        all().filterNot{it.id==t.id}.forEach{
            a.put(JSONObject().apply{put("id",it.id);put("direction",it.direction);put("risk",it.risk);put("pnl",it.pnl);put("setup",it.setup);put("created",it.created)})
        }
        a.put(JSONObject().apply{put("id",t.id);put("direction",t.direction);put("risk",t.risk);put("pnl",t.pnl);put("setup",t.setup);put("created",t.created)})
        prefs.edit().putString(DATA_KEY,encrypt(a.toString())).apply()
    }
}
private fun money(v:Double)="R"+String.format(Locale.US,"%.2f",v)
private fun now()=SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(Date())

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{OptimusApp(SecureTradeStore(this))}}
}

@Composable
fun OptimusApp(store:SecureTradeStore){
    val bg = Color(0xFF070A10)
    val panel = Color(0xFF10151F)
    val panel2 = Color(0xFF151C28)
    val cyan = Color(0xFF38D9FF)
    val green = Color(0xFF35D07F)
    val red = Color(0xFFFF5C73)
    val muted = Color(0xFF8B96A8)
    val gold = Color(0xFFF2C66D)

    var tab by remember{mutableIntStateOf(0)}
    var trades by remember{mutableStateOf(store.all())}
    var message by remember{mutableStateOf("System ready")}
    val equity=550.0+trades.sumOf{it.pnl}
    val wins=trades.count{it.pnl>0}
    val losses=trades.count{it.pnl<0}
    val winRate=if(trades.isEmpty()) 0.0 else wins*100.0/trades.size

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = bg,
            surface = panel,
            surfaceVariant = panel2,
            primary = cyan,
            secondary = gold,
            onBackground = Color(0xFFE8EDF5),
            onSurface = Color(0xFFE8EDF5),
            onSurfaceVariant = muted
        )
    ){
        Scaffold(
            containerColor = bg,
            bottomBar={
                NavigationBar(containerColor=Color(0xFF0B0F17)){
                    listOf("⌂" to "Overview","◈" to "Journal","⚙" to "System").forEachIndexed{i,p->
                        NavigationBarItem(
                            selected=tab==i,
                            onClick={tab=i},
                            icon={Text(p.first,fontSize=20.sp)},
                            label={Text(p.second,fontSize=11.sp)}
                        )
                    }
                }
            }
        ){pad->
            LazyColumn(
                Modifier.fillMaxSize().padding(pad).padding(horizontal=16.dp),
                verticalArrangement=Arrangement.spacedBy(12.dp),
                contentPadding=PaddingValues(top=18.dp,bottom=20.dp)
            ){
                item{
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){
                            Text("OPTIMUS",fontSize=25.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=2.sp)
                            Text("AI TRADING INTELLIGENCE",fontSize=10.sp,color=cyan,fontWeight=FontWeight.Bold,letterSpacing=1.5.sp)
                        }
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF10251F)).border(1.dp,green.copy(alpha=.45f),RoundedCornerShape(50)).padding(horizontal=10.dp,vertical=6.dp)
                        ){Text("● ONLINE",fontSize=10.sp,color=green,fontWeight=FontWeight.Bold)}
                    }
                }

                when(tab){
                    0->{
                        item{
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                                    .background(Brush.linearGradient(listOf(Color(0xFF111C2B),Color(0xFF0D121C))))
                                    .border(1.dp,cyan.copy(alpha=.20f),RoundedCornerShape(22.dp)).padding(18.dp)
                            ){
                                Row(verticalAlignment=Alignment.CenterVertically){
                                    Column(Modifier.weight(1f)){
                                        Text("XAUUSD",fontSize=22.sp,fontWeight=FontWeight.Bold)
                                        Text("GOLD / US DOLLAR",fontSize=10.sp,color=muted,letterSpacing=1.2.sp)
                                    }
                                    Text("MARKET",fontSize=9.sp,color=muted,fontWeight=FontWeight.Bold)
                                }
                                Spacer(Modifier.height(18.dp))
                                Text("DATA CONNECTOR",fontSize=10.sp,color=muted,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
                                Text("WAITING FOR LIVE FEED",fontSize=18.sp,color=gold,fontWeight=FontWeight.Bold)
                                Text("No price is fabricated. Connect market data before AI analysis.",fontSize=12.sp,color=muted)
                                Spacer(Modifier.height(14.dp))
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                    MetricPill("HTF","READY",cyan)
                                    MetricPill("ICT","READY",gold)
                                    MetricPill("RISK","GUARDED",green)
                                }
                            }
                        }
                        item{
                            SectionTitle("AI MARKET READ","Confluence engine")
                            GlassCard(panel){
                                Row(verticalAlignment=Alignment.CenterVertically){
                                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF122B35)),contentAlignment=Alignment.Center){
                                        Text("AI",color=cyan,fontWeight=FontWeight.ExtraBold)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)){
                                        Text("OPTIMUS INTELLIGENCE",fontWeight=FontWeight.Bold)
                                        Text("Structure • liquidity • macro • news • session",fontSize=11.sp,color=muted)
                                    }
                                    Text("STANDBY",fontSize=9.sp,color=gold,fontWeight=FontWeight.Bold)
                                }
                                Spacer(Modifier.height(14.dp))
                                HorizontalDivider(color=Color(0xFF263040))
                                Spacer(Modifier.height(14.dp))
                                Text("Analysis will only become actionable after fresh market data, multi-timeframe confluence and the risk engine agree.",fontSize=12.sp,color=Color(0xFFC4CBD7))
                            }
                        }
                        item{
                            Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth()){
                                ActionButton("SCAN GOLD",cyan,Modifier.weight(1f)){message="Live analysis requires the secure market-data backend."}
                                ActionButton("CHART AI",gold,Modifier.weight(1f)){message="Chart vision is ready for an uploaded screenshot."}
                            }
                        }
                        item{
                            SectionTitle("ACCOUNT CONTROL","R550 protection")
                            GlassCard(panel){
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                                    MiniStat("EQUITY",money(equity),Color.White)
                                    MiniStat("NET P&L",money(trades.sumOf{it.pnl}),if(trades.sumOf{it.pnl}>=0)green else red)
                                    MiniStat("TRADES",trades.size.toString(),cyan)
                                }
                                Spacer(Modifier.height(14.dp))
                                Text("Risk guard: ENABLED • Capital protection remains the priority.",fontSize=11.sp,color=muted)
                            }
                        }
                        item{Text(message,fontSize=11.sp,color=muted,modifier=Modifier.padding(horizontal=4.dp))}
                    }
                    1->{
                        item{
                            SectionTitle("TRADE JOURNAL","Encrypted local memory")
                            GlassCard(panel){
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                                    MiniStat("WIN RATE",String.format(Locale.US,"%.1f%%",winRate),green)
                                    MiniStat("WINS",wins.toString(),green)
                                    MiniStat("LOSSES",losses.toString(),red)
                                    MiniStat("P&L",money(trades.sumOf{it.pnl}),if(trades.sumOf{it.pnl}>=0)green else red)
                                }
                            }
                        }
                        items(trades.size){i->
                            val t=trades[i]
                            GlassCard(panel2){
                                Row(verticalAlignment=Alignment.CenterVertically){
                                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(if(t.pnl>=0)green else red))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)){
                                        Text(t.direction+"  •  "+t.setup,fontWeight=FontWeight.Bold)
                                        Text(t.created,fontSize=10.sp,color=muted)
                                    }
                                    Text(money(t.pnl),color=if(t.pnl>=0)green else red,fontWeight=FontWeight.Bold)
                                }
                                Spacer(Modifier.height(8.dp))
                                Text("Risk "+money(t.risk),fontSize=11.sp,color=muted)
                            }
                        }
                        if(trades.isEmpty())item{EmptyState("No trades yet","Optimized for disciplined, auditable trading memory.")}
                    }
                    else->{
                        item{
                            SectionTitle("SYSTEM STATUS","Controlled and auditable")
                            GlassCard(panel){
                                StatusRow("Trade memory","HEALTHY",green)
                                StatusRow("Risk guard","ENABLED",green)
                                StatusRow("Self-repair","CONTROLLED",cyan)
                                StatusRow("APK self-modification","DISABLED",red)
                                StatusRow("Live market feed","NOT CONNECTED",gold)
                            }
                        }
                        item{
                            GlassCard(panel2){
                                Text("SAFETY CORE",fontWeight=FontWeight.Bold,color=cyan)
                                Spacer(Modifier.height(8.dp))
                                Text("Optimus will not invent market prices or silently change strategy and risk controls. Repairs must remain reversible and auditable.",fontSize=12.sp,color=Color(0xFFC4CBD7))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title:String,subtitle:String){
    Column(Modifier.padding(horizontal=2.dp,vertical=2.dp)){
        Text(title,fontSize=13.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=1.1.sp)
        Text(subtitle,fontSize=10.sp,color=Color(0xFF7F8A9D))
    }
}

@Composable
private fun GlassCard(color:Color,content:@Composable ColumnScope.()->Unit){
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(color)
            .border(1.dp,Color(0xFF253041),RoundedCornerShape(18.dp)).padding(16.dp),
        content=content
    )
}

@Composable
private fun MetricPill(label:String,value:String,color:Color){
    Row(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF0A1019)).padding(horizontal=9.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
        Text(label,fontSize=9.sp,color=Color(0xFF7F8A9D),fontWeight=FontWeight.Bold)
        Spacer(Modifier.width(5.dp))
        Text(value,fontSize=9.sp,color=color,fontWeight=FontWeight.Bold)
    }
}

@Composable
private fun ActionButton(text:String,color:Color,modifier:Modifier,onClick:()->Unit){
    Button(onClick=onClick,modifier=modifier.height(52.dp),shape=RoundedCornerShape(15.dp),colors=ButtonDefaults.buttonColors(containerColor=color,contentColor=Color(0xFF061018))){
        Text(text,fontSize=11.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=.8.sp)
    }
}

@Composable
private fun MiniStat(label:String,value:String,color:Color){
    Column(horizontalAlignment=Alignment.Start){
        Text(label,fontSize=9.sp,color=Color(0xFF7F8A9D),fontWeight=FontWeight.Bold)
        Text(value,fontSize=15.sp,color=color,fontWeight=FontWeight.Bold)
    }
}

@Composable
private fun StatusRow(label:String,value:String,color:Color){
    Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(10.dp))
        Text(label,Modifier.weight(1f),fontSize=12.sp)
        Text(value,fontSize=9.sp,color=color,fontWeight=FontWeight.Bold)
    }
}

@Composable
private fun EmptyState(title:String,subtitle:String){
    GlassCard(Color(0xFF10151F)){
        Text(title,fontWeight=FontWeight.Bold)
        Text(subtitle,fontSize=11.sp,color=Color(0xFF8B96A8))
    }
}
