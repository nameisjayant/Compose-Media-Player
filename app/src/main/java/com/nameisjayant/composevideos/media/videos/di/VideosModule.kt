package com.nameisjayant.composevideos.media.videos.di

import com.nameisjayant.composevideos.media.videos.data.SeekPreviewSource
import com.nameisjayant.composevideos.media.videos.data.SeekPreviewSourceImpl
import com.nameisjayant.composevideos.media.videos.data.VideosRepository
import com.nameisjayant.composevideos.media.videos.data.VideosRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VideosModule {

    @Binds
    @Singleton
    abstract fun bindVideosRepository(impl: VideosRepositoryImpl): VideosRepository

    @Binds
    @Singleton
    abstract fun bindSeekPreviewSource(impl: SeekPreviewSourceImpl): SeekPreviewSource
}
