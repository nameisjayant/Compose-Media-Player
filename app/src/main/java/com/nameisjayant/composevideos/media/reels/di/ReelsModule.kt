package com.nameisjayant.composevideos.media.reels.di

import com.nameisjayant.composevideos.media.reels.data.ReelsRepository
import com.nameisjayant.composevideos.media.reels.data.ReelsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReelsModule {

    @Binds
    @Singleton
    abstract fun bindReelsRepository(impl: ReelsRepositoryImpl): ReelsRepository
}
