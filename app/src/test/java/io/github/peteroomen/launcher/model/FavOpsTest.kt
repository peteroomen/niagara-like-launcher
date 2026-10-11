package io.github.peteroomen.launcher.model

import io.github.peteroomen.launcher.data.FavJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavOpsTest {

    private val travel = Fav.Folder("f1", "Travel", listOf("maps", "uber"))
    private val favs = listOf(Fav.App("phone"), travel, Fav.App("camera"))

    @Test fun addSkipsAppsAlreadyInAFolder() {
        assertEquals(favs, FavOps.add(favs, "maps"))
        assertEquals(favs + Fav.App("spotify"), FavOps.add(favs, "spotify"))
    }

    @Test fun detachRemovesFromFoldersAndDropsEmptyOnes() {
        val oneApp = listOf(Fav.Folder("f1", "Solo", listOf("maps")), Fav.App("phone"))
        assertEquals(listOf(Fav.App("phone")), FavOps.detach(oneApp, "maps"))
        assertEquals(
            listOf(Fav.App("phone"), travel.copy(apps = listOf("uber")), Fav.App("camera")),
            FavOps.detach(favs, "maps"),
        )
    }

    @Test fun moveOutOfFolderLandsRightAfterTheFolder() {
        assertEquals(
            listOf(Fav.App("phone"), travel.copy(apps = listOf("uber")), Fav.App("maps"), Fav.App("camera")),
            FavOps.moveOutOfFolder(favs, "maps"),
        )
    }

    @Test fun movingTheLastAppOutReplacesTheFolder() {
        val solo = listOf(Fav.App("phone"), Fav.Folder("f1", "Solo", listOf("maps")), Fav.App("camera"))
        assertEquals(
            listOf(Fav.App("phone"), Fav.App("maps"), Fav.App("camera")),
            FavOps.moveOutOfFolder(solo, "maps"),
        )
    }

    @Test fun moveToFolderTakesAppFromTopLevel() {
        assertEquals(
            listOf(Fav.App("phone"), travel.copy(apps = listOf("maps", "uber", "camera"))),
            FavOps.moveToFolder(favs, "camera", "f1"),
        )
    }

    @Test fun moveToMissingFolderChangesNothing() {
        assertEquals(favs, FavOps.moveToFolder(favs, "camera", "nope"))
    }

    @Test fun newFolderTakesTheAppsPlace() {
        val result = FavOps.newFolder(favs, "camera", "Media")
        assertEquals(listOf(Fav.App("phone"), travel, Fav.Folder("f2", "Media", listOf("camera"))), result)
        val first = FavOps.newFolder(favs, "phone", "Calls")
        assertEquals(Fav.Folder("f2", "Calls", listOf("phone")), first[0])
    }

    @Test fun newFolderFromInsideAFolderGoesAtTheEnd() {
        val result = FavOps.newFolder(favs, "maps", "Rides")
        assertEquals(Fav.Folder("f2", "Rides", listOf("maps")), result.last())
        assertEquals(listOf("uber"), (result[1] as Fav.Folder).apps)
    }

    @Test fun ungroupKeepsPosition() {
        assertEquals(
            listOf(Fav.App("phone"), Fav.App("maps"), Fav.App("uber"), Fav.App("camera")),
            FavOps.ungroup(favs, "f1"),
        )
    }

    @Test fun visibleHidesMissingAppsAndEmptyFolders() {
        val shown = setOf("phone", "uber")
        assertEquals(
            listOf(Fav.App("phone"), travel.copy(apps = listOf("uber"))),
            FavOps.visible(favs) { it in shown },
        )
        assertEquals(listOf(Fav.App("phone")), FavOps.visible(favs) { it == "phone" })
    }

    @Test fun folderOfFindsTheFolder() {
        assertEquals(travel, FavOps.folderOf(favs, "uber"))
        assertNull(FavOps.folderOf(favs, "phone"))
    }

    @Test fun reorderMovesShownEntries() {
        val order = FavOps.move(favs.map(FavOps::idOf), 2, 0)
        assertEquals(listOf("camera", "phone", "folder:f1"), order)
        assertEquals(listOf(Fav.App("camera"), Fav.App("phone"), travel), FavOps.reorder(favs, order))
    }

    @Test fun reorderKeepsHiddenEntriesInPlace() {
        val saved = listOf(Fav.App("phone"), Fav.App("secret"), travel, Fav.App("camera"))
        // "secret" is hidden, so the screen shows phone, travel, camera; drag camera to the top
        val result = FavOps.reorder(saved, listOf("camera", "phone", "folder:f1"))
        assertEquals(listOf(Fav.App("camera"), Fav.App("secret"), Fav.App("phone"), travel), result)
    }

    @Test fun createFolderTakesAppsFromWhereverTheyWere() {
        val result = FavOps.createFolder(favs, "Mixed", listOf("camera", "maps"))
        assertEquals(listOf(Fav.App("phone"), travel.copy(apps = listOf("uber")), Fav.Folder("f2", "Mixed", listOf("camera", "maps"))), result)
        assertEquals(favs, FavOps.createFolder(favs, "Empty", emptyList()))
    }

    @Test fun droppingAnAppOnAnotherMakesAFolderInItsPlace() {
        val result = FavOps.mergeIntoNewFolder(favs, "camera", "phone", "Folder")
        assertEquals(listOf(Fav.Folder("f2", "Folder", listOf("phone", "camera")), travel), result)
    }

    @Test fun jsonRoundTrip() {
        assertEquals(favs, FavJson.decode(FavJson.encode(favs)))
        assertEquals(emptyList<Fav>(), FavJson.decode("not json"))
        val renames = mapOf("a/.B" to "Bee")
        assertEquals(renames, FavJson.decodeMap(FavJson.encodeMap(renames)))
    }
}
