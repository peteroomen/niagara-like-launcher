package io.github.peteroomen.launcher.model

/** An entry in the favourites column: a single app, or a folder of apps. Apps are keyed by component name. */
sealed interface Fav {
    data class App(val key: String) : Fav
    data class Folder(val id: String, val name: String, val apps: List<String>) : Fav
}

/** Pure operations on the favourites list. An app sits either at the top level or in exactly one folder. */
object FavOps {

    fun allKeys(favs: List<Fav>): List<String> = favs.flatMap {
        when (it) {
            is Fav.App -> listOf(it.key)
            is Fav.Folder -> it.apps
        }
    }

    fun contains(favs: List<Fav>, key: String) = key in allKeys(favs)

    fun folderOf(favs: List<Fav>, key: String): Fav.Folder? =
        favs.filterIsInstance<Fav.Folder>().firstOrNull { key in it.apps }

    /** Removes [key] wherever it is. Folders left empty disappear. */
    fun detach(favs: List<Fav>, key: String): List<Fav> = favs
        .filterNot { it is Fav.App && it.key == key }
        .map { if (it is Fav.Folder) it.copy(apps = it.apps - key) else it }
        .filterNot { it is Fav.Folder && it.apps.isEmpty() }

    fun add(favs: List<Fav>, key: String): List<Fav> = if (contains(favs, key)) favs else favs + Fav.App(key)

    /** Puts the app back at the top level, just after its old folder. */
    fun moveOutOfFolder(favs: List<Fav>, key: String): List<Fav> {
        val folder = folderOf(favs, key) ?: return favs
        val oldIndex = favs.indexOf(folder)
        val result = detach(favs, key).toMutableList()
        val folderIndex = result.indexOfFirst { it is Fav.Folder && it.id == folder.id }
        result.add(if (folderIndex == -1) oldIndex.coerceAtMost(result.size) else folderIndex + 1, Fav.App(key))
        return result
    }

    fun moveToFolder(favs: List<Fav>, key: String, folderId: String): List<Fav> {
        if (folderOf(favs, key)?.id == folderId) return favs
        if (favs.none { it is Fav.Folder && it.id == folderId }) return favs
        return detach(favs, key).map {
            if (it is Fav.Folder && it.id == folderId) it.copy(apps = it.apps + key) else it
        }
    }

    /** A new folder holding [key], placed where the app was, or at the end if it wasn't a top-level favourite. */
    fun newFolder(favs: List<Fav>, key: String, name: String, id: String = nextFolderId(favs)): List<Fav> {
        val topIndex = favs.indexOfFirst { it is Fav.App && it.key == key }
        val result = detach(favs, key).toMutableList()
        result.add(if (topIndex >= 0) topIndex.coerceAtMost(result.size) else result.size, Fav.Folder(id, name, listOf(key)))
        return result
    }

    fun renameFolder(favs: List<Fav>, id: String, name: String): List<Fav> =
        favs.map { if (it is Fav.Folder && it.id == id) it.copy(name = name) else it }

    /** Replaces the folder with its apps, in place. */
    fun ungroup(favs: List<Fav>, id: String): List<Fav> = favs.flatMap {
        if (it is Fav.Folder && it.id == id) it.apps.map(Fav::App) else listOf(it)
    }

    fun removeFolder(favs: List<Fav>, id: String): List<Fav> = favs.filterNot { it is Fav.Folder && it.id == id }

    /** What to draw: drops apps that aren't installed or are hidden, and folders left empty. Doesn't change what's saved. */
    fun visible(favs: List<Fav>, isShown: (String) -> Boolean): List<Fav> = favs.mapNotNull {
        when (it) {
            is Fav.App -> it.takeIf { app -> isShown(app.key) }
            is Fav.Folder -> it.copy(apps = it.apps.filter(isShown)).takeIf { f -> f.apps.isNotEmpty() }
        }
    }

    fun nextFolderId(favs: List<Fav>): String {
        val highest = favs.filterIsInstance<Fav.Folder>().maxOfOrNull { it.id.removePrefix("f").toIntOrNull() ?: 0 } ?: 0
        return "f${highest + 1}"
    }
}
