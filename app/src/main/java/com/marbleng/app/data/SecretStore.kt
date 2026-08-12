package com.marbleng.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecretStore(private val context: Context) {
    private val alias="marbleng-secrets"
    private val prefs=context.getSharedPreferences("marbleng-secrets",Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (ks.getKey(alias,null) as? SecretKey)?.let{return it}
        val kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        kg.init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return kg.generateKey()
    }
    fun put(name:String,value:String){
        if(value.isBlank()){prefs.edit().remove(name).apply();return}
        val c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,key())
        val blob=c.iv+c.doFinal(value.toByteArray())
        prefs.edit().putString(name,Base64.encodeToString(blob,Base64.NO_WRAP)).apply()
    }
    fun get(name:String):String{
        val raw=prefs.getString(name,null)?:return ""; return runCatching{
            val b=Base64.decode(raw,Base64.NO_WRAP); val iv=b.copyOfRange(0,12); val enc=b.copyOfRange(12,b.size)
            val c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,iv)); String(c.doFinal(enc))
        }.getOrDefault("")
    }
}
