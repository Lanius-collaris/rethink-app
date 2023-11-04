/*
 * Copyright 2022 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.celzero.bravedns.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.liveData
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.database.ConnectionTrackerDAO
import com.celzero.bravedns.database.DnsLogDAO
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.ui.fragment.SummaryStatisticsFragment
import com.celzero.bravedns.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailedStatisticsViewModel(
    private val connectionTrackerDAO: ConnectionTrackerDAO,
    private val dnsLogDAO: DnsLogDAO,
    appConfig: AppConfig
) : ViewModel() {
    private var allowedNetworkActivity: MutableLiveData<String> = MutableLiveData()
    private var blockedNetworkActivity: MutableLiveData<String> = MutableLiveData()
    private var allowedDomains: MutableLiveData<String> = MutableLiveData()
    private var blockedDomains: MutableLiveData<String> = MutableLiveData()
    private var allowedIps: MutableLiveData<String> = MutableLiveData()
    private var blockedIps: MutableLiveData<String> = MutableLiveData()
    private var allowedCountries: MutableLiveData<String> = MutableLiveData()
    private var blockedCountries: MutableLiveData<String> = MutableLiveData()
    private var fromTime: MutableLiveData<Long> = MutableLiveData()
    private var toTime: MutableLiveData<Long> = MutableLiveData()

    companion object {
        private const val TIME_1_HOUR = 1 * 60 * 60 * 1000L
        private const val TIME_24_HOUR = 24 * 60 * 60 * 1000L
        private const val TIME_7_DAYS = 7 * 24 * 60 * 60 * 1000L
        private const val IS_APP_BYPASSED = "true"
    }

    fun setData(type: SummaryStatisticsFragment.SummaryStatisticsType) {
        when (type) {
            SummaryStatisticsFragment.SummaryStatisticsType.MOST_CONNECTED_APPS -> {
                allowedNetworkActivity.value = ""
            }
            SummaryStatisticsFragment.SummaryStatisticsType.MOST_BLOCKED_APPS -> {
                blockedNetworkActivity.value = ""
            }
            SummaryStatisticsFragment.SummaryStatisticsType.MOST_CONTACTED_DOMAINS -> {
                allowedDomains.value = ""
            }
            SummaryStatisticsFragment.SummaryStatisticsType.MOST_BLOCKED_DOMAINS -> {
                io {
                    val isAppBypassed = FirewallManager.isAnyAppBypassesDns()
                    if (isAppBypassed) {
                        blockedDomains.postValue(IS_APP_BYPASSED)
                    } else {
                        blockedDomains.postValue("")
                    }
                }
            }
            SummaryStatisticsFragment.SummaryStatisticsType.MOST_CONTACTED_IPS -> {
                allowedIps.value = ""
            }
            SummaryStatisticsFragment.SummaryStatisticsType.MOST_BLOCKED_IPS -> {
                blockedIps.value = ""
            }
            SummaryStatisticsFragment.SummaryStatisticsType.MOST_CONTACTED_COUNTRIES -> {
                allowedCountries.value = ""
            }
            SummaryStatisticsFragment.SummaryStatisticsType.MOST_BLOCKED_COUNTRIES -> {
                blockedCountries.value = ""
            }
        }
    }

    fun timeCategoryChanged(timeCategory: SummaryStatisticsViewModel.TimeCategory) {
        when (timeCategory) {
            SummaryStatisticsViewModel.TimeCategory.ONE_HOUR -> {
                fromTime.value = System.currentTimeMillis() - TIME_1_HOUR
                toTime.value = System.currentTimeMillis()
            }
            SummaryStatisticsViewModel.TimeCategory.TWENTY_FOUR_HOUR -> {
                fromTime.value =
                    System.currentTimeMillis() - TIME_24_HOUR
                toTime.value = System.currentTimeMillis()
            }
            SummaryStatisticsViewModel.TimeCategory.SEVEN_DAYS -> {
                fromTime.value = System.currentTimeMillis() - TIME_7_DAYS
                toTime.value = System.currentTimeMillis()
            }
        }
    }

    val getAllAllowedAppNetworkActivity =
        allowedNetworkActivity.switchMap { _ ->
            Pager(PagingConfig(Constants.LIVEDATA_PAGE_SIZE)) {
                    val from = fromTime.value ?: 0L
                    val to = toTime.value ?: 0L
                    connectionTrackerDAO.getAllAllowedAppNetworkActivity(from, to)
                }
                .liveData
                .cachedIn(viewModelScope)
        }

    val getAllBlockedAppNetworkActivity =
        blockedNetworkActivity.switchMap { _ ->
            Pager(PagingConfig(Constants.LIVEDATA_PAGE_SIZE)) {
                    val from = fromTime.value ?: 0L
                    val to = toTime.value ?: 0L
                    connectionTrackerDAO.getAllBlockedAppNetworkActivity(from, to)
                }
                .liveData
                .cachedIn(viewModelScope)
        }

    val getAllContactedDomains =
        allowedDomains.switchMap { _ ->
            Pager(PagingConfig(Constants.LIVEDATA_PAGE_SIZE)) {
                    val from = fromTime.value ?: 0L
                    val to = toTime.value ?: 0L
                    if (appConfig.getBraveMode().isDnsMode()) {
                        dnsLogDAO.getAllContactedDomains(from, to)
                    } else {
                        connectionTrackerDAO.getAllContactedDomains(from, to)
                    }
                }
                .liveData
                .cachedIn(viewModelScope)
        }

    val getAllBlockedDomains =
        blockedDomains.switchMap { isAppBypassed ->
            Pager(PagingConfig(Constants.LIVEDATA_PAGE_SIZE)) {
                    val from = fromTime.value ?: 0L
                    val to = toTime.value ?: 0L
                    if (appConfig.getBraveMode().isDnsMode()) {
                        dnsLogDAO.getAllBlockedDomains(from, to)
                    } else {
                        // if any app bypasses the dns, then the decision made in flow() call
                        if (isAppBypassed.isNotEmpty()) {
                            connectionTrackerDAO.getAllBlockedDomains(from, to)
                        } else {
                            dnsLogDAO.getAllBlockedDomains(from, to)
                        }
                    }
                }
                .liveData
                .cachedIn(viewModelScope)
        }

    val getAllContactedIps =
        allowedIps.switchMap { _ ->
            Pager(PagingConfig(Constants.LIVEDATA_PAGE_SIZE)) {
                    val from = fromTime.value ?: 0L
                    val to = toTime.value ?: 0L
                    connectionTrackerDAO.getAllContactedIps(from, to)
                }
                .liveData
                .cachedIn(viewModelScope)
        }

    val getAllBlockedIps =
        blockedIps.switchMap { _ ->
            Pager(PagingConfig(Constants.LIVEDATA_PAGE_SIZE)) {
                    val from = fromTime.value ?: 0L
                    val to = toTime.value ?: 0L
                    connectionTrackerDAO.getAllBlockedIps(from, to)
                }
                .liveData
                .cachedIn(viewModelScope)
        }

    val getAllContactedCountries =
        allowedCountries.switchMap { _ ->
            Pager(PagingConfig(Constants.LIVEDATA_PAGE_SIZE)) {
                    val from = fromTime.value ?: 0L
                    val to = toTime.value ?: 0L
                    connectionTrackerDAO.getAllContactedCountries(from, to)
                }
                .liveData
                .cachedIn(viewModelScope)
        }

    val getAllBlockedCountries =
        blockedCountries.switchMap { _ ->
            Pager(PagingConfig(Constants.LIVEDATA_PAGE_SIZE)) {
                    val from = fromTime.value ?: 0L
                    val to = toTime.value ?: 0L
                    connectionTrackerDAO.getAllBlockedCountries(from, to)
                }
                .liveData
                .cachedIn(viewModelScope)
        }

    private fun io(f: suspend () -> Unit) {
        viewModelScope.launch { withContext(Dispatchers.IO) { f() } }
    }
}
