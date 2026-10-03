package com.musicmr.player

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore

object DeleteHelper {
    fun start(ctx: Context, vm: PlayerViewModel, list: List<Song>, launch: (IntentSender) -> Unit) {
        vm.pendingDelete = list
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val pi = MediaStore.createDeleteRequest(ctx.contentResolver, list.map { it.uri })
                launch(pi.intentSender)
            } catch (e: Exception) {
                vm.pendingDelete = emptyList()
            }
        } else {
            legacyStep(ctx, vm, launch)
        }
    }

    fun onResult(ctx: Context, vm: PlayerViewModel, ok: Boolean, launch: (IntentSender) -> Unit) {
        if (!ok) {
            vm.pendingDelete = emptyList()
            return
        }
        if (Build.VERSION.SDK_INT >= 30) {
            vm.finishDelete(vm.pendingDelete)
            vm.pendingDelete = emptyList()
        } else {
            legacyStep(ctx, vm, launch)
        }
    }

    private fun legacyStep(ctx: Context, vm: PlayerViewModel, launch: (IntentSender) -> Unit) {
        val todo = vm.pendingDelete
        val done = mutableListOf<Song>()
        for ((idx, s) in todo.withIndex()) {
            try {
                ctx.contentResolver.delete(s.uri, null, null)
                done.add(s)
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException) {
                    vm.pendingDelete = todo.drop(idx)
                    vm.finishDelete(done)
                    launch(e.userAction.actionIntent.intentSender)
                    return
                }
            } catch (e: Exception) {
            }
        }
        vm.finishDelete(done)
        vm.pendingDelete = emptyList()
    }
}
