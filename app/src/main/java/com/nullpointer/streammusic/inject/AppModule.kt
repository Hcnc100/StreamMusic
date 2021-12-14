package com.nullpointer.streammusic.inject

import com.nullpointer.streammusic.data.remote.MusicDataSource
import com.nullpointer.streammusic.domain.MusicRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideMusicDataSource(): MusicDataSource =
        MusicDataSource()

    @Provides
    @Singleton
    fun provideMusicRepository(
        musicDataSource: MusicDataSource
    ): MusicRepositoryImpl=
        MusicRepositoryImpl(musicDataSource)
}