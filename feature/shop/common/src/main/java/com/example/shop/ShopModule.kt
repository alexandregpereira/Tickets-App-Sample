package com.example.shop

import com.example.shop.core.GetEventUseCase
import com.example.shop.list.EventListUiModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val shopModule = module {
    factory { GetEventsUseCase() }
    factory<GetEventUseCase> { DefaultGetEventUseCase(get()) }

    viewModel { EventListUiModel(get()) }
}
