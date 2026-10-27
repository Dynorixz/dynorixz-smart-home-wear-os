package com.dynorixz.smarthome.complication

import com.dynorixz.smarthome.data.local.FavoriteKind
import com.dynorixz.smarthome.domain.AppError
import com.dynorixz.smarthome.domain.AppException
import com.dynorixz.smarthome.domain.Capability
import com.dynorixz.smarthome.domain.CapabilityActionFactory
import com.dynorixz.smarthome.domain.SmartHomeRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

class ShortcutExecutor @Inject constructor(private val repository: SmartHomeRepository) {
    // OPEN is handled by the Activity, without sending a command.
    suspend fun execute(choice: ComplicationChoice): String {
        val (ref, action) = choice
        require(action != ShortcutAction.OPEN)
        if (repository.observeHome().first() == null) repository.refreshHome()
        return when (ref.kind) {
            FavoriteKind.SCENARIO -> {
                require(action == ShortcutAction.SCENARIO)
                repository.executeScenario(ref.id)
                try { repository.refreshHome() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* The scenario succeeded even if its new state cannot be loaded. */ }
                "Сценарий запущен"
            }
            FavoriteKind.DEVICE -> {
                val device = repository.refreshDevice(ref.id)
                if (!device.reachable) throw AppException(AppError.DeviceOffline())
                val power = device.capabilities.filterIsInstance<Capability.OnOff>().firstOrNull()
                    ?: throw AppException(AppError.UnsupportedCapability())
                val target = targetPower(action, power)
                repository.executeDeviceAction(ref.id, CapabilityActionFactory.boolean(power, target))
                if (target) "Устройство включено" else "Устройство выключено"
            }
            FavoriteKind.GROUP -> {
                val group = repository.refreshGroup(ref.id)
                val power = group.capabilities.filterIsInstance<Capability.OnOff>().firstOrNull()
                    ?: throw AppException(AppError.UnsupportedCapability())
                val target = targetPower(action, power)
                repository.executeGroupAction(ref.id, CapabilityActionFactory.boolean(power, target))
                if (target) "Группа включена" else "Группа выключена"
            }
        }
    }

    private fun targetPower(action: ShortcutAction, power: Capability.OnOff): Boolean = when (action) {
        ShortcutAction.ON -> true
        ShortcutAction.OFF -> false
        ShortcutAction.TOGGLE -> power.value?.jsonPrimitive?.booleanOrNull?.not()
            ?: throw AppException(AppError.InvalidResponse())
        else -> throw AppException(AppError.UnsupportedCapability())
    }
}
