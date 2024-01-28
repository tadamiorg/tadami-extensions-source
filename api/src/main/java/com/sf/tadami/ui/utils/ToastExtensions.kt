package com.sf.tadami.ui.utils

import android.widget.Toast

object UiToasts {
    private var currentToast: Toast? = null

    fun showToast(msg : String,duration : Int = Toast.LENGTH_SHORT){
       throw Exception("Stub")

    }
    fun showToast(stringRes : Int,duration : Int = Toast.LENGTH_SHORT,vararg args : Any) {
        throw Exception("Stub")
    }
}