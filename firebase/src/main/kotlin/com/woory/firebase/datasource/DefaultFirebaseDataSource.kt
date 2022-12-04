package com.woory.firebase.datasource

import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.woory.data.model.AddedUserHpModel
import com.woory.data.model.MagneticInfoModel
import com.woory.data.model.PromiseDataModel
import com.woory.data.model.PromiseModel
import com.woory.data.model.UserHpModel
import com.woory.data.model.UserLocationModel
import com.woory.data.model.UserModel
import com.woory.data.source.FirebaseDataSource
import com.woory.firebase.mapper.asDomain
import com.woory.firebase.mapper.asModel
import com.woory.firebase.mapper.asPromiseParticipant
import com.woory.firebase.mapper.extractMagnetic
import com.woory.firebase.model.AddedUserHpDocument
import com.woory.firebase.model.MagneticInfoDocument
import com.woory.firebase.model.PromiseDocument
import com.woory.firebase.model.UserHpDocument
import com.woory.firebase.model.UserLocationDocument
import com.woory.firebase.util.InviteCodeUtil
import com.woory.firebase.util.TimeConverter.asMillis
import com.woory.firebase.util.TimeConverter.asTimeStamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DefaultFirebaseDataSource @Inject constructor(
    private val fireStore: FirebaseFirestore,
    private val scope: CoroutineScope
) : FirebaseDataSource {

    override suspend fun getPromiseByCode(code: String): Result<PromiseModel> =
        withContext(scope.coroutineContext) {
            val result = runCatching {
                val task = fireStore
                    .collection(PROMISE_COLLECTION_NAME)
                    .document(code)
                    .get()
                Tasks.await(task)
                val res = task.result
                    .toObject(PromiseDocument::class.java)
                    ?.asDomain()
                    ?: throw UNMATCHED_STATE_EXCEPTION
                res
            }

            when (val exception = result.exceptionOrNull()) {
                null -> result
                else -> Result.failure(exception)
            }
        }

    override suspend fun getPromiseByCodeAndListen(code: String): Flow<Result<PromiseModel>> =
        callbackFlow {
            var documentReference: DocumentReference? = null

            runCatching {
                documentReference = fireStore.collection(PROMISE_COLLECTION_NAME).document(code)
            }.onFailure {
                trySend(Result.failure(it))
            }

            val subscription = documentReference?.addSnapshotListener { value, _ ->
                if (value == null) {
                    return@addSnapshotListener
                }

                runCatching {
                    val result = value.toObject(PromiseDocument::class.java)
                    result?.let {
                        trySend(Result.success(it.asDomain()))
                    } ?: throw UNMATCHED_STATE_EXCEPTION
                }.onFailure {
                    trySend(Result.failure(it))
                }
            }

            awaitClose { subscription?.remove() }
        }

    override suspend fun setPromise(promiseDataModel: PromiseDataModel): Result<String> =
        withContext(scope.coroutineContext) {
            var generatedCode: String? = null
            var isDone = false
            while (isDone.not()) {
                generatedCode = InviteCodeUtil.getRandomInviteCode()
                val task = fireStore
                    .collection(PROMISE_COLLECTION_NAME)
                    .document(generatedCode)
                    .get()
                Tasks.await(task)
                if (task.result.data == null) {
                    isDone = true
                }
            }
            requireNotNull(generatedCode)

            val result = runCatching {

                val promiseCollection =
                    fireStore.collection(PROMISE_COLLECTION_NAME).document(generatedCode)
                val magneticCollection = fireStore
                    .collection(PROMISE_COLLECTION_NAME)
                    .document(generatedCode)
                    .collection(MAGNETIC_COLLECTION_NAME)
                    .document(generatedCode)

                fireStore.runBatch { batch ->
                    batch.set(promiseCollection, promiseDataModel.asModel(generatedCode))
                    batch.set(magneticCollection, promiseDataModel.extractMagnetic(generatedCode))
                }
            }

            when (val exception = result.exceptionOrNull()) {
                null -> Result.success(generatedCode)
                else -> Result.failure(exception)
            }
        }

    override suspend fun getUserLocationById(id: String): Flow<Result<UserLocationModel>> =
        callbackFlow {
            var documentReference: DocumentReference? = null

            runCatching {
                documentReference = fireStore.collection(LOCATION_COLLECTION_NAME).document(id)
            }.onFailure {
                trySend(Result.failure(it))
            }

            val subscription = documentReference?.addSnapshotListener { value, _ ->
                if (value == null) {
                    return@addSnapshotListener
                }

                runCatching {
                    val result = value.toObject(UserLocationDocument::class.java)
                    result?.let {
                        trySend(Result.success(it.asDomain()))
                    } ?: throw UNMATCHED_STATE_EXCEPTION
                }.onFailure {
                    trySend(Result.failure(it))
                }
            }

            awaitClose { subscription?.remove() }
        }

    override suspend fun setUserLocation(userLocationModel: UserLocationModel): Result<Unit> =
        withContext(scope.coroutineContext) {
            val result = runCatching {
                val res = fireStore
                    .collection(LOCATION_COLLECTION_NAME)
                    .document(userLocationModel.id)
                    .set(userLocationModel.asModel())
            }

            when (val exception = result.exceptionOrNull()) {
                null -> result
                else -> Result.failure(exception)
            }
        }

    override suspend fun getUserHpById(id: String, gameToken: String): Flow<Result<UserHpModel>> =
        callbackFlow {
            var documentReference: DocumentReference? = null

            runCatching {
                documentReference = fireStore
                    .collection(LOCATION_COLLECTION_NAME)
                    .document(gameToken)
                    .collection(HP_COLLECTION_NAME)
                    .document(id)
            }.onFailure {
                trySend(Result.failure(it))
            }

            val subscription = documentReference?.addSnapshotListener { value, error ->
                if (value == null) {
                    return@addSnapshotListener
                }

                runCatching {
                    val result = value.toObject(UserHpDocument::class.java)
                    result?.let {
                        trySend(Result.success(it.asDomain()))
                    } ?: UNMATCHED_STATE_EXCEPTION
                }.onFailure {
                    trySend(Result.failure(it))
                }
            }

            awaitClose { subscription?.remove() }
        }

    override suspend fun setUserHp(gameToken: String, userHpModel: UserHpModel): Result<Unit> =
        withContext(scope.coroutineContext) {
            val result = runCatching {
                val res = fireStore
                    .collection(PROMISE_COLLECTION_NAME)
                    .document(gameToken)
                    .collection(HP_COLLECTION_NAME)
                    .document(userHpModel.id)
                    .set(userHpModel.asModel())
            }

            when (val exception = result.exceptionOrNull()) {
                null -> result
                else -> Result.failure(exception)
            }
        }

    override suspend fun addPlayer(code: String, user: UserModel): Result<Unit> =
        withContext(scope.coroutineContext) {
            val result = runCatching {
                val res = fireStore
                    .collection(PROMISE_COLLECTION_NAME)
                    .document(code)
                    .update("users", FieldValue.arrayUnion(user.asPromiseParticipant()))
                    .await()
            }

            when (val exception = result.exceptionOrNull()) {
                null -> result
                else -> Result.failure(exception)
            }
        }

    /**
     * 자기장 반지름과 중심 좌표를 가져오는 함수
     */
    override suspend fun getMagneticInfoByCodeAndListen(code: String): Flow<Result<MagneticInfoModel>> =
        callbackFlow {
            var documentReference: DocumentReference? = null

            runCatching {
                documentReference = fireStore
                    .collection(PROMISE_COLLECTION_NAME)
                    .document(code)
                    .collection(MAGNETIC_COLLECTION_NAME)
                    .document(code)
            }.onFailure {
                trySend(Result.failure(it))
            }

            val subscription = documentReference?.addSnapshotListener { value, _ ->
                if (value == null) {
                    return@addSnapshotListener
                }

                runCatching {
                    val result = value.toObject(MagneticInfoDocument::class.java)
                    result?.let {
                        trySend(Result.success(it.asDomain()))
                    } ?: throw UNMATCHED_STATE_EXCEPTION
                }.onFailure {
                    trySend(Result.failure(it))
                }
            }

            awaitClose { subscription?.remove() }
        }

    override suspend fun getMagneticInfoByCode(code: String): Result<MagneticInfoModel> =
        withContext(scope.coroutineContext) {
            val result = runCatching {
                val task = fireStore
                    .collection(PROMISE_COLLECTION_NAME)
                    .document(code)
                    .collection(MAGNETIC_COLLECTION_NAME)
                    .document(code)
                    .get()
                Tasks.await(task)
                val res = task
                    .result
                    .toObject(MagneticInfoDocument::class.java)
                    ?.asDomain()
                    ?: throw UNMATCHED_STATE_EXCEPTION

                res
            }

            when (val exception = result.exceptionOrNull()) {
                null -> result
                else -> Result.failure(exception)
            }
        }

    override suspend fun updateMagneticRadius(gameCode: String, radius: Double): Result<Unit> =
        withContext(scope.coroutineContext) {
            val result = runCatching {
                val reference = fireStore
                    .collection(PROMISE_COLLECTION_NAME)
                    .document(gameCode)
                    .collection(MAGNETIC_COLLECTION_NAME)
                    .document(gameCode)

                fireStore.runTransaction { transaction ->
                    val snapshot = transaction.get(reference)
                    val serverRadius = snapshot.getLong(RADIUS_KEY) ?: return@runTransaction

                    val maxValue = maxOf(serverRadius, radius.toLong())
                    transaction.update(reference, mapOf(RADIUS_KEY to maxValue))
                }.addOnFailureListener {
                    throw it
                }
            }
            when (val exception = result.exceptionOrNull()) {
                null -> Result.success(Unit)
                else -> Result.failure(exception)
            }
        }

    override suspend fun decreaseMagneticRadius(
        gameCode: String,
        minusValue: Double
    ): Result<Unit> =
        withContext(scope.coroutineContext) {
            val result = runCatching {
                val reference = fireStore
                    .collection(PROMISE_COLLECTION_NAME)
                    .document(gameCode)
                    .collection(MAGNETIC_COLLECTION_NAME)
                    .document(gameCode)

                fireStore.runTransaction { transaction ->
                    val snapShot = transaction.get(reference)
                    val serverRadius = snapShot.getLong(RADIUS_KEY) ?: return@runTransaction
                    val updateTime = snapShot.getTimestamp(TIMESTAMP_KEY) ?: return@runTransaction

                    if (isFirstAccess(updateTime)) {
                        transaction.update(
                            reference, mapOf(
                                RADIUS_KEY to serverRadius - minusValue,
                                TIMESTAMP_KEY to System.currentTimeMillis().asTimeStamp()
                            )
                        )
                    }
                }
            }
            when (val exception = result.exceptionOrNull()) {
                null -> Result.success(Unit)
                else -> Result.failure(exception)
            }
        }

    override suspend fun checkReEntryOfGame(gameCode: String, token: String): Result<Boolean> =
        withContext(scope.coroutineContext) {
            val result = kotlin.runCatching {
                val task = fireStore.collection(PROMISE_COLLECTION_NAME)
                    .document(gameCode)
                    .collection(GAME_INFO_COLLECTION_NAME)
                    .document(token)
                    .get()

                Tasks.await(task)

                task.result.data.isNullOrEmpty()
            }
            when (val exception = result.exceptionOrNull()) {
                null -> result
                else -> Result.failure(exception)
            }
        }

    override suspend fun sendOutUser(gameCode: String, token: String): Result<Unit> =
        withContext(scope.coroutineContext) {
            val result = kotlin.runCatching {
                val reference = fireStore.collection(PROMISE_COLLECTION_NAME)
                    .document(gameCode)
                    .collection(GAME_INFO_COLLECTION_NAME)
                    .document(token)

                fireStore.runTransaction {
                    it.update(reference, mapOf(LOST_KEY to true, HP_KEY to 0))
                }
            }
            when (val exception = result.exceptionOrNull()) {
                null -> Result.success(Unit)
                else -> Result.failure(exception)
            }
        }

    override suspend fun setUserInitialHpData(gameCode: String, token: String): Result<Unit> =
        withContext(scope.coroutineContext) {
            val result = kotlin.runCatching {
                fireStore.collection(PROMISE_COLLECTION_NAME)
                    .document(gameCode)
                    .collection(GAME_INFO_COLLECTION_NAME)
                    .document(token)
                    .set(
                        AddedUserHpDocument(
                            userId = token,
                            updatedAt = System.currentTimeMillis().asTimeStamp()
                        )
                    )
            }
            when (val exception = result.exceptionOrNull()) {
                null -> Result.success(Unit)
                else -> Result.failure(exception)
            }
        }

    override suspend fun decreaseUserHp(gameCode: String, token: String): Result<Long> =
        withContext(scope.coroutineContext) {
            var userHp: Long = -1
            val result = runCatching {
                val reference = fireStore
                    .collection(PROMISE_COLLECTION_NAME)
                    .document(gameCode)
                    .collection(GAME_INFO_COLLECTION_NAME)
                    .document(token)

                fireStore.runTransaction { transaction ->
                    val snapShot = transaction.get(reference)
                    userHp = snapShot.getLong(HP_KEY) ?: return@runTransaction

                    transaction.update(reference, mapOf(HP_KEY to userHp - 1))
                }
            }

            when (val exception = result.exceptionOrNull()) {
                null -> Result.success(userHp - 1)
                else -> Result.failure(exception)
            }
        }

    override suspend fun getUserHpAndListen(
        gameCode: String,
        token: String
    ): Flow<Result<AddedUserHpModel>> =
        callbackFlow {
            var documentReference: DocumentReference? = null

            runCatching {
                documentReference = fireStore
                    .collection(PROMISE_COLLECTION_NAME)
                    .document(gameCode)
                    .collection(GAME_INFO_COLLECTION_NAME)
                    .document(token)
            }.onFailure {
                trySend(Result.failure(it))
            }

            val subscription = documentReference?.addSnapshotListener { value, _ ->
                if (value == null) {
                    return@addSnapshotListener
                }

                runCatching {
                    val result = value.toObject(AddedUserHpDocument::class.java)
                    result?.let {
                        trySend(Result.success(it.asDomain()))
                    } ?: throw UNMATCHED_STATE_EXCEPTION
                }.onFailure {
                    trySend(Result.failure(it))
                }
            }
            awaitClose { subscription?.remove() }
        }

    override suspend fun setPlayerArrived(gameCode: String, token: String): Result<Unit> =
        withContext(scope.coroutineContext) {
            val result = runCatching {
                fireStore
                    .collection(PROMISE_COLLECTION_NAME)
                    .document(gameCode)
                    .collection(GAME_INFO_COLLECTION_NAME)
                    .document(token)
                    .update("arrived", true)
            }

            when (val exception = result.exceptionOrNull()) {
                null -> Result.success(Unit)
                else -> Result.failure(exception)
            }
        }

    override suspend fun getPlayerArrived(gameCode: String, token: String): Flow<Result<Boolean>> =
        callbackFlow {
            var documentReference: DocumentReference? = null

            runCatching {
                documentReference = fireStore
                    .collection(PROMISE_COLLECTION_NAME)
                    .document(gameCode)
                    .collection(GAME_INFO_COLLECTION_NAME)
                    .document(token)
            }.onFailure {
                trySend(Result.failure(it))
            }

            val subscription = documentReference?.addSnapshotListener { value, _ ->
                value ?: return@addSnapshotListener

                val result = runCatching {
                    value.getBoolean("arrived") ?: throw UNMATCHED_STATE_EXCEPTION
                }
                trySend(result)
            }
            awaitClose { subscription?.remove() }
        }

    override suspend fun getGameRealtimeRanking(gameCode: String): Flow<Result<List<AddedUserHpModel>>> =
        callbackFlow {
            var documentReference: Query? = null

            runCatching {
                documentReference = fireStore
                    .collection(PROMISE_COLLECTION_NAME)
                    .document(gameCode)
                    .collection(GAME_INFO_COLLECTION_NAME)
                    .orderBy(HP_KEY)
            }.onFailure {
                trySend(Result.failure(it))
            }

            val subscription = documentReference?.addSnapshotListener { value, _ ->
                if (value == null) {
                    return@addSnapshotListener
                }

                val result = runCatching {
                    val changedResult =
                        value.documents.map { it.toObject(AddedUserHpDocument::class.java) }
                    changedResult.map {
                        it?.asDomain() ?: throw UNMATCHED_STATE_EXCEPTION
                    }
                }
                trySend(result)
            }

            awaitClose { subscription?.remove() }
        }

    private fun isFirstAccess(prevTime: Timestamp): Boolean =
        System.currentTimeMillis() - prevTime.asMillis() >= 1000 * (MAGNETIC_FIELD_UPDATE_TERM_SECOND - 1)

    companion object {
        private const val PROMISE_COLLECTION_NAME = "Promises"
        private const val LOCATION_COLLECTION_NAME = "UserLocation"
        private const val MAGNETIC_COLLECTION_NAME = "Magnetic"
        private const val HP_COLLECTION_NAME = "Hp"
        private const val GAME_INFO_COLLECTION_NAME = "GameInfo"
        private const val HP_KEY = "hp"
        private const val RADIUS_KEY = "radius"
        private const val LOST_KEY = "lost"
        private const val TIMESTAMP_KEY = "timeStamp"
        private const val MAGNETIC_FIELD_UPDATE_TERM_SECOND = 30
        private val UNMATCHED_STATE_EXCEPTION = IllegalStateException("Unmatched State with Server")
    }
}
