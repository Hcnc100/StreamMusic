package com.nullpointer.streammusic.core.states

open class Event <out T>(private val data: T){

    var hasBeenHandler=false
    private set

    fun  getContentIfNotHandler(): T?{
        return if(hasBeenHandler){
            null
        }else{
            hasBeenHandler=true
            data
        }
    }
    fun peekContent() = data
}