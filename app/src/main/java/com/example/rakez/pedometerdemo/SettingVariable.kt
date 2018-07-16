package com.example.rakez.pedometerdemo

import com.chibatching.kotpref.KotprefModel

object SettingVariable: KotprefModel() {
    var isFitnessApiSubscribed by booleanPref(default = false)
}