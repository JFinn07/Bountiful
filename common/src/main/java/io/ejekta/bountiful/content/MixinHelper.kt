package io.ejekta.bountiful.content

import com.google.common.collect.ImmutableList
import io.ejekta.bountiful.content.item.DecreeItem
import io.ejekta.bountiful.content.villager.FindBoardTask
import io.ejekta.bountiful.content.villager.WalkToBoardTask
import io.ejekta.bountiful.util.ensureMemoryModules
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.npc.Villager
import net.minecraft.world.entity.npc.VillagerProfession
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AnvilMenu
import net.minecraft.world.item.ItemStack
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import com.mojang.datafixers.util.Pair as MojangPair

object MixinHelper {

    fun injectNewTasks(
        profession: VillagerProfession,
        speed: Float,
        cir: CallbackInfoReturnable<ImmutableList<MojangPair<Int, out BehaviorControl<in Villager?>?>>>
    ) {
        val options = cir.returnValue.toMutableList()
        options.add(
            MojangPair(
                2,
                WalkToBoardTask(0.7f)
            )
        )
        cir.setReturnValue(ImmutableList.copyOf(options))
    }

    fun injectCoreTasks(
        profession: VillagerProfession,
        speed: Float,
        cir: CallbackInfoReturnable<ImmutableList<MojangPair<Int, out BehaviorControl<in Villager?>?>>>
    ) {
        val options = cir.returnValue.toMutableList()
        @Suppress("UNCHECKED_CAST")
        val task = FindBoardTask.create() as BehaviorControl<in Villager?>
        options.add(
            MojangPair.of<Int, BehaviorControl<in Villager?>?>(
                10,
                task
            )
        )
        cir.setReturnValue(ImmutableList.copyOf(options))
    }

    fun ensureBoardMemoryModule(villager: Villager) {
        villager.brain.ensureMemoryModules(listOf(
            BountifulContent.MEM_MODULE_NEAREST_BOARD
        ))
    }

    fun modifyAnvilResults(handler: AnvilMenu) {
        val inA = handler.inputSlots.getItem(0)
        val inB = handler.inputSlots.getItem(1)
        if (!inA.isEmpty && inA.item is DecreeItem && !inB.isEmpty && inB.item is DecreeItem) {
            val dataA = inA[BountifulContent.DECREE_DATA] ?: return
            val dataB = inB[BountifulContent.DECREE_DATA] ?: return

            if (dataA.ids == dataB.ids) {
                return
            }

            val combined = dataA.copy(ids = dataA.ids + dataB.ids)
            val decreeProto = ItemStack(BountifulContent.DECREE_ITEM)
            decreeProto[BountifulContent.DECREE_DATA] = combined
            handler.cost.set(combined.ids.size * 3 - 1)
            handler.resultSlots.setItem(0, decreeProto)
        }
    }

    fun takeAnvilResults(playerEntity: Player, stack: ItemStack, handler: AnvilMenu) {
        // This is really hacky; stack enters as the decree but count of 0, so getItem returns air unless we
        // temporarily increment and then reset. Anvil never seems to use this variable, but we reset it just to be safe.
        val currCount = stack.count
        stack.grow(1)
        if (stack.item is DecreeItem) {
            (playerEntity as? ServerPlayer)?.run { BountifulContent.Triggers.PRINTING_PRESS.trigger(this) }
        }
        stack.count = currCount
    }

}