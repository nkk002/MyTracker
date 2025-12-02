package com.mmu.mytracker.data.remote.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {

    private const val BASE_URL = "https://maps.googleapis.com/"

    // lazy 初始化确保首次使用时才创建实例
    val api: DirectionsApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            // 添加 Gson 转换器，用于将 JSON 自动解析为 Data Class
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DirectionsApiService::class.java)
    }
}