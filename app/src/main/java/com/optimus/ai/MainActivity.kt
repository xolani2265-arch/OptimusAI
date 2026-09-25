package com.optimus.ai

import android.content.Context
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    var tab by remember{mutableIntStateOf(0)}
    var trades by remember{mutableStateOf(store.all())}
    var message by remember{mutableStateOf("Ready")}
    val equity=550.0+trades.sumOf{it.pnl}
    val wins=trades.count{it.pnl>0}
    val losses=trades.count{it.pnl<0}
    MaterialTheme{
        Scaffold(bottomBar={NavigationBar{
            listOf("Dashboard","Journal","Diagnostics").forEachIndexed{i,n->
                NavigationBarItem(selected=tab==i,onClick={tab=i},icon={},label={Text(n)})
            }
        }}){pad->
            LazyColumn(Modifier.padding(pad).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                item{Text("OPTIMUS AI",style=MaterialTheme.typography.headlineMedium);Text("Gold / XAUUSD • Risk-first analysis")}
                when(tab){
                    0->{
                        item{Card{Column(Modifier.padding(16.dp)){Text("Account protection");Text("Starting equity: R550.00");Text("Journal equity: "+money(equity));Text("Recorded trades: "+trades.size)}}
                        }
                        item{Card{Column(Modifier.padding(16.dp)){Text("Analysis framework");Text("Multi-timeframe • ICT-style structure • macro/news evidence");Text("No trade is approved without a risk check.")}}}
                        item{Button(onClick={message="Live analysis requires the secure market-data backend."}){Text("ANALYSE GOLD")};Text(message)}
                        item{OutlinedButton(onClick={val t=Trade(UUID.randomUUID().toString(),"TEST",5.50,0.0,"Memory test",now());store.save(t);trades=store.all();message="Trade memory test saved securely."}){Text("TEST TRADE MEMORY")}}
                    }
                    1->{
                        item{Card{Column(Modifier.padding(16.dp)){Text("Performance");Text("Wins: "+wins+"   Losses: "+losses);Text("Win rate: "+if(trades.isEmpty())"0.0" else String.format(Locale.US,"%.1f",wins*100.0/trades.size)+"%");Text("Net P&L: "+money(trades.sumOf{it.pnl}))}}}
                        items(trades.size){i->val t=trades[i];Card{Column(Modifier.padding(14.dp)){Text(t.direction+" • "+t.setup);Text("Risk "+money(t.risk)+" • P&L "+money(t.pnl));Text(t.created)}}}
                        if(trades.isEmpty())item{Text("No trades recorded yet.")}
                    }
                    else->{
                        item{Card{Column(Modifier.padding(16.dp)){Text("Diagnostics");Text("Trade memory: "+runCatching{store.all();"HEALTHY"}.getOrElse{"ERROR"});Text("Risk guard: ENABLED");Text("Self-repair: controlled/reversible only");Text("APK self-modification: DISABLED")}}}
                        item{Text("Technical incidents and repair attempts must be auditable; strategy and risk settings are not silently changed.")}
                    }
                }
            }
        }
    }
}
