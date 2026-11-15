package mae

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.input.InputEventAPI
import org.magiclib.hullmods.enhancedmodule.EnhancedModuleArmor
import org.magiclib.util.taskscheduler.CombatTaskScheduler
import java.util.*

internal class AutomaticModuleArmorApplierPlugin: BaseEveryFrameCombatPlugin() {
    private val seen: MutableSet<ShipAPI> = Collections.newSetFromMap(IdentityHashMap<ShipAPI, Boolean>())

    override fun advance(amount: Float, events: List<InputEventAPI>) {
        val engine = Global.getCombatEngine() ?: return

        for (ship in engine.ships) {
            if (ship in seen) continue

            if (ship.isStationModule || ship.isFighter || ship.isDrone || ship.isShuttlePod || ship.isPiece) {
                seen.add(ship)
                continue
            }

            if (ship.isHulk) continue // isHulk is a state the ship can leave, so keep it separate.

            if (!engine.isEntityInPlay(ship)) continue // Should be the most expensive check, keep last

            seen.add(ship)

            onShipSpawned(ship)
        }
    }

    private fun onShipSpawned(ship: ShipAPI) {
        try {
            // Give each ship a single tick of delay after being spawned in so they can load their stuff first.
            CombatTaskScheduler.performLater { // In testing without this, it didn't appear to be necessary, but it shouldn't cause much issue to wait a tick just in case.
                val engine = Global.getCombatEngine() ?: return@performLater

                if (ship.isHulk || !engine.isEntityInPlay(ship)) return@performLater // Check again in case it changed.

                onShipSpawnedWithTickDelay(ship)
            }
        } catch (e: Exception) {
            Global.getLogger(this.javaClass).error("Failed to automatically enhance armor modules.", e)
        }
    }

    private fun onShipSpawnedWithTickDelay(ship: ShipAPI) {
        if (ship.childModulesCopy.isEmpty()) return

        // Is a similar effect already applied to the ship?
        if (ship.getListeners(Any::class.java).any {
                val className = it.javaClass.simpleName.lowercase()
                className.contains("occlusion") || className.contains("raycast")
        })
            return

        // Is a similar effect already applied to the modules?
        for (module in ship.childModulesCopy) {
            if (module.isHulk)
                return
            if (module.getListeners(Any::class.java).any {
                val className = it.javaClass.simpleName.lowercase()
                    className.contains("occlusion") || className.contains("raycast") || className.contains("armor")
            })
                return
        }

        var apply = false

        for (module in ship.childModulesCopy) {
            val hull = module.variant.hullSpec

            // Engage paranoia

            if (!hull.hints.contains(ShipHullSpecAPI.ShipTypeHints.MODULE))
                continue
            if (hull.fluxCapacity != 0f) // (armor modules have flux dissipation for some reason, so only check capacity)
                continue
            if (!hull.shipSystemId.isNullOrEmpty())
                continue
            if (hull.allWeaponSlotsCopy.isNotEmpty())
                continue
            if (hull.fighterBays != 0)
                continue
            if (!hull.hasTag(Tags.MODULE_HULL_BAR_ONLY))
                continue
            if (hull.builtInMods.size != 2 || !hull.builtInMods.contains("always_detaches") || !hull.builtInMods.contains("reduced_explosion"))
                continue

            if (!module.variant.hasHullMod(EnhancedModuleArmor.HULL_MOD_ID) && !module.hasTag(EnhancedModuleArmor.HULL_MOD_ID)) {
                module.addTag(EnhancedModuleArmor.HULL_MOD_ID)
                apply = true
            }
        }

        if (apply)
            EnhancedModuleArmor.apply(ship)
    }
}