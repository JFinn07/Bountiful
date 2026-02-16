package io.ejekta.bountiful.content.villager

import io.ejekta.bountiful.content.BountifulContent
import io.ejekta.bountiful.content.board.BoardBlockEntity
import net.minecraft.core.GlobalPos
import net.minecraft.core.Holder
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.entity.ai.village.poi.PoiManager
import net.minecraft.world.entity.ai.village.poi.PoiType
import net.minecraft.world.entity.npc.Villager
import java.util.function.Predicate

/**
 * A lightweight POI scanning behavior that finds nearby bounty boards
 * and sets [BountifulContent.MEM_MODULE_NEAREST_BOARD] memory.
 *
 * Unlike vanilla's AcquirePoi, this does NOT claim/take the POI slot,
 * so multiple villagers can discover and visit the same board.
 */
object FindBoardTask {

    private const val SCAN_COOLDOWN_TICKS = 200L // ~10 seconds between scans
    private const val SCAN_RANGE = 48

    fun create(): BehaviorControl<Villager> {
        val lastScanTime = org.apache.commons.lang3.mutable.MutableLong(0L)

        return BehaviorBuilder.create { instance ->
            instance.group(
                instance.absent(BountifulContent.MEM_MODULE_NEAREST_BOARD)
            ).apply(instance) { nearestBoardAccessor ->
                Trigger { serverLevel, villager, gameTime ->
                    // Cooldown between scans
                    if (gameTime < lastScanTime.value + SCAN_COOLDOWN_TICKS) {
                        return@Trigger false
                    }
                    lastScanTime.value = gameTime

                    val poiPredicate = Predicate<Holder<PoiType>> { it.`is`(BountifulContent.POI_BOUNTY_BOARD_KEY) }

                    val closest = serverLevel.poiManager.findClosest(
                        poiPredicate,
                        villager.blockPosition(),
                        SCAN_RANGE,
                        PoiManager.Occupancy.ANY // Don't require available space - we're not claiming
                    )

                    if (closest.isPresent) {
                        val boardPos = closest.get()
                        // Only set memory if the board has pending pickups from a completed bounty
                        val boardEntity = serverLevel.getBlockEntity(boardPos) as? BoardBlockEntity
                        if (boardEntity != null && boardEntity.hasPendingPickups()) {
                            nearestBoardAccessor.set(GlobalPos.of(serverLevel.dimension(), boardPos))
                            // Clear the flag immediately so other villagers don't also claim this visit
                            boardEntity.clearNeedsVillagerVisit()
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
            }
        }
    }
}
