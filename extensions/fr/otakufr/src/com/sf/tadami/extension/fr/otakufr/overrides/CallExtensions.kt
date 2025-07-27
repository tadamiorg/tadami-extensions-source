package com.sf.tadami.extension.fr.otakufr.overrides

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okio.IOException

fun Call.asCancelableObservable(ignoredErrorCodes : List<Int> = emptyList()): Observable<Response> {

    return Observable.create { emitter ->
        val callback = object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if(!call.isCanceled()){
                    emitter.onError(e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.handleErrors(ignoredErrorCodes)
                    emitter.onNext(response)
                    emitter.onComplete()
                } catch (e: Exception) {
                    if(!call.isCanceled()) {
                        emitter.onError(e)
                    }
                }
            }
        }
        this.enqueue(callback)

        emitter.setDisposable(object : Disposable {
            private var disposed = false
            override fun dispose() {
                disposed = true
                this@asCancelableObservable.cancel()
            }

            override fun isDisposed(): Boolean = disposed
        })
    }
}

sealed class UnknownHttpError : Exception() {
    data class Failure(val statusCode: Int?) : UnknownHttpError()
}

fun Response.handleErrors(ignoredErrorCodes : List<Int>): Response {
    val code = this.code
    if (!this.isSuccessful && !ignoredErrorCodes.contains(code)) {
        throw UnknownHttpError.Failure(code)
    }
    return this
}