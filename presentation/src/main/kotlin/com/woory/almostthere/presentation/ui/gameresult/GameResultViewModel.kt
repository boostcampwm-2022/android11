package com.woory.almostthere.presentation.ui.gameresult

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.woory.almostthere.data.repository.PromiseRepository
import com.woory.almostthere.data.repository.UserRepository
import com.woory.almostthere.presentation.model.mapper.user.asUiModel
import com.woory.almostthere.presentation.model.user.gameresult.UserRanking
import com.woory.almostthere.presentation.model.user.gameresult.UserSplitMoneyItem
import com.woory.almostthere.presentation.util.flow.EventFlow
import com.woory.almostthere.presentation.util.flow.MutableEventFlow
import com.woory.almostthere.presentation.util.flow.asEventFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel
class GameResultViewModel @Inject constructor(
    userRepository: UserRepository,
    private val promiseRepository: PromiseRepository
) : ViewModel() {

    private val _gameCode: MutableStateFlow<String?> = MutableStateFlow(null)
    val gameCode: StateFlow<String?> = _gameCode.asStateFlow()

    private val _userRankingList: MutableStateFlow<List<UserRanking>?> = MutableStateFlow(null)

    val userRankingList: StateFlow<List<UserRanking>?> = _userRankingList.asStateFlow()

    private val userPreferences = userRepository.userPreferences

    private val _myRankingNumber: MutableStateFlow<Int?> = MutableStateFlow(null)
    val myRankingNumber: StateFlow<Int?> = _myRankingNumber.asStateFlow()

    private val _mySplitMoney: MutableStateFlow<Int?> = MutableStateFlow(null)
    val mySplitMoney: StateFlow<Int?> = _mySplitMoney.asStateFlow()

    private val _userSplitMoneyItems: MutableStateFlow<List<UserSplitMoneyItem>?> =
        MutableStateFlow(null)

    val userSplitMoneyItems: StateFlow<List<UserSplitMoneyItem>?> =
        _userSplitMoneyItems.asStateFlow()

    private val _showDialogEvent: MutableEventFlow<Unit> = MutableEventFlow()
    val showDialogEvent: EventFlow<Unit> = _showDialogEvent.asEventFlow()

    private val _dataLoading: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val dataLoading: StateFlow<Boolean> = _dataLoading.asStateFlow()

    private val _errorEvent: MutableSharedFlow<Throwable> = MutableSharedFlow()
    val errorEvent: SharedFlow<Throwable> = _errorEvent.asSharedFlow()

    fun setGameCode(gameCode: String?) {
        viewModelScope.launch {
            _gameCode.emit(gameCode)
        }
    }

    fun fetchUserRankingList() {
        viewModelScope.launch {

            val gameCode = _gameCode.value ?: run {
                _errorEvent.emit(IllegalArgumentException("참여 코드가 없습니다."))
                return@launch
            }

            _dataLoading.emit(true)
            promiseRepository.getUserRankings(gameCode).onSuccess {
                val rankings =
                    it.map { userRankingModel -> userRankingModel.asUiModel() }

                val user = runBlocking { userPreferences.first() }
                _myRankingNumber.emit(rankings.find { it.userId == user.userID }?.rankingNumber)
                _userRankingList.emit(rankings)

                _dataLoading.emit(false)
            }.onFailure {
                _errorEvent.emit(IllegalArgumentException("참여 코드에 해당하는 결과가 없습니다."))
                _dataLoading.emit(false)
            }
        }
    }

    fun loadUserPaymentList(value: Int) {
        viewModelScope.launch {
            userPreferences.collectLatest { userPreferences ->
                val userPayments =
                    SplitMoneyLogic.calculatePayment(value, _userRankingList.value ?: emptyList())
                val remain =
                    UserSplitMoneyItem.Balance(value - userPayments.sumOf { it.moneyToPay })
                _userSplitMoneyItems.emit(userPayments + remain)
                _mySplitMoney.emit(
                    userPayments.find { it.userId == userPreferences.userID }?.moneyToPay
                )
            }
        }
    }

    fun setShowDialogEvent() {
        viewModelScope.launch {
            _showDialogEvent.emit(Unit)
        }
    }
}