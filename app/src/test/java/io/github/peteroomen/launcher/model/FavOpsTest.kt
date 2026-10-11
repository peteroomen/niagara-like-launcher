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

    @Test fun jsonRoundTrip() {
        assertEquals(favs, FavJson.decode(FavJson.encode(favs)))
        assertEquals(emptyList<Fav>(), FavJson.decode("not json"))
        val renames = mapOf("a/.B" to "Bee")
        assertEquals(renames, FavJson.decodeMap(FavJson.encodeMap(renames)))
    }
}
