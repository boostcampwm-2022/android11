package com.woory.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.woory.database.entity.PromiseAlarmEntity

@Dao
interface PromiseAlarmDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPromiseAlarm(gameTimeEntity: PromiseAlarmEntity)

    @Query("SELECT * FROM promise_alarm")
    suspend fun getAll(): List<PromiseAlarmEntity>

    @Query("SELECT * From promise_alarm ORDER BY datetime(start_time)")
    suspend fun getPromiseAlarmSortedByStartTime(): List<PromiseAlarmEntity>

    @Query("SELECT * From promise_alarm ORDER BY datetime(end_time)")
    suspend fun getPromiseAlarmSortedByEndTime(): List<PromiseAlarmEntity>

    @Query("SELECT * FROM promise_alarm WHERE promiseCode=:promiseCode")
    suspend fun getPromiseAlarmWhereCode(promiseCode: String): PromiseAlarmEntity
}