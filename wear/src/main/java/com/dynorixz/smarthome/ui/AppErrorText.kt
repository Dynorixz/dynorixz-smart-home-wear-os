package com.dynorixz.smarthome.ui

import com.dynorixz.smarthome.domain.AppError
import com.dynorixz.smarthome.domain.AppException

fun Throwable.userMessage(): String {
    val appError = (this as? AppException)?.error
    return when (appError) {
    is AppError.Network -> "Нет подключения"
    is AppError.Unauthorized -> "Нужно снова войти в Яндекс"
    is AppError.Forbidden -> "Недостаточно прав iot:view или iot:control"
    is AppError.DeviceOffline -> "Устройство недоступно"
    is AppError.UnsupportedCapability -> "Эта функция не поддерживается"
    is AppError.RateLimit -> "Слишком много запросов. Попробуйте позже"
    is AppError.Server -> "Сервис Яндекса временно недоступен"
    is AppError.InvalidResponse -> "Получен некорректный ответ"
    is AppError.Timeout -> "Яндекс не ответил вовремя"
    is AppError.OAuth -> appError.description ?: "Ошибка входа"
    null -> "Не удалось выполнить операцию"
    }
}
