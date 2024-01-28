package com.sf.tadami.network

interface Callback<T> {
    fun onData(data: T?){
        throw Exception("Stub!")
    }
    fun onError(message : String?, errorCode : Int? = null){
        throw Exception("Stub!")
    }
}