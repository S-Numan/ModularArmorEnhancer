package mae

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global
import org.magiclib.kotlin.getDHullId
import org.magiclib.util.api.getDHull
import org.magiclib.util.taskscheduler.CombatTaskScheduler.Companion.performOnPlayerBattleStartPersistent

class ModPlugin: BaseModPlugin() {
    override fun onApplicationLoad() {
        performOnPlayerBattleStartPersistent {
            Global.getCombatEngine().addPlugin(AutomaticModuleArmorApplierPlugin())
        }

        val ons = Global.getSettings().getHullSpec("onslaught_mk1")
        ons.addTag("ML_showModulePaperdoll")
        ons.getDHull().addTag("ML_showModulePaperdoll")
    }
}