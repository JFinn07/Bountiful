package io.ejekta.bountiful.forge

import io.ejekta.bountiful.Bountiful
import io.ejekta.bountiful.bridge.Bountybridge
import io.ejekta.bountiful.config.BountifulIO
import io.ejekta.bountiful.config.BountifulIO.doContentReload
import io.ejekta.bountiful.content.BountifulCommands
import io.ejekta.bountiful.content.BountifulContent
import io.ejekta.kambrik.registration.KambrikRegistrar
import io.ejekta.percale.Percale
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.event.AddReloadListenerEvent
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.event.village.WandererTradesEvent
import net.neoforged.neoforge.registries.RegisterEvent
import thedarkcolour.kotlinforforge.neoforge.forge.FORGE_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_CONTEXT
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist
import java.util.concurrent.CompletableFuture
import java.util.logging.Level


@Mod("bountiful")
class BountifulModForge {
    init {

        // Disable verbose Percale decode logging to avoid UI stalls during reload.
        Percale.shouldSyslog = false

        Bountiful.LOGGER.info("Registering Network Messages..")

        Bountybridge.registerServerMessages()
        Bountybridge.registerClientMessages()

        FORGE_BUS.addListener(this::registerCommands)
        FORGE_BUS.addListener(this::onGameReload)
        FORGE_BUS.addListener(this::onEntityKilled)
        FORGE_BUS.addListener(this::onServerStarting)
        FORGE_BUS.addListener(this::changeTrades)

        val content = BountifulContent // trigger init

        MOD_CONTEXT.getKEventBus().register(Companion)

        runForDist(
            clientTarget = {
                MOD_CONTEXT.getKEventBus().register(BountifulForgeClient::class.java)
            },
            serverTarget = {
                // no-op, nothing specific here
            }
        )
        Bountybridge.registerCriterionStuff()
    }

    private fun onEntityKilled(evt: LivingDeathEvent) {
        evt.source.entity?.let { attacker ->
            (evt.entity.level() as? ServerLevel)?.let { serverWorld ->
                Bountybridge.handleEntityKills(serverWorld, attacker, evt.entity)
            }
        }
    }

    private fun onServerStarting(evt: ServerStartingEvent) {
        Bountybridge.registerJigsawPieces(evt.server)
    }

    private fun onGameReload(evt: AddReloadListenerEvent) {
        evt.addListener(PreparableReloadListener { prepBarrier, resourceManager, prepProfiler, reloadProfiler, backgroundExecutor, gameExecutor ->
            return@PreparableReloadListener CompletableFuture
                .runAsync({
                    Percale.shouldSyslog = false
                    doContentReload(resourceManager)
                }, backgroundExecutor)
                .thenCompose(prepBarrier::wait)
                .whenComplete { _, _ ->
                    Bountiful.LOGGER.info("Bountiful reload listener completed")
                }
        })
    }

    private fun registerCommands(evt: RegisterCommandsEvent) {
        BountifulCommands.register(evt.dispatcher, evt.buildContext, evt.commandSelection)
    }

    private fun changeTrades(evt: WandererTradesEvent) {
        Bountybridge.modifyTradeList(evt.rareTrades)
    }

    companion object {
        @JvmStatic
        @SubscribeEvent
        fun registerRegistryContent(evt: RegisterEvent) {
            KambrikRegistrar[BountifulContent].content.forEach { entry ->
                evt.register(entry.registry.key() as ResourceKey<out Registry<Any>>) {
                    it.register(ResourceLocation.fromNamespaceAndPath(BountifulContent.getId(), entry.itemId), entry.item.value!!)
                }
            }
        }

        @JvmStatic
        @SubscribeEvent
        private fun commonSetup(evt: FMLCommonSetupEvent) {
            evt.enqueueWork {
                Bountybridge.registerCompostables()
                BountifulContent.registerVillagerPoiMemory()
            }
        }

    }

}