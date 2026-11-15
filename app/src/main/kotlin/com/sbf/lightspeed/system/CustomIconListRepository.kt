package com.sbf.lightspeed.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class CustomIconEntry(
    val id: String,
    val label: String
)

data class CustomIconList(
    val id: String,
    val name: String,
    val items: List<CustomIconEntry>
)

/**
 * Manages user-created Custom Icon Lists (e.g., "Favorite Icons", "Tech Icons") for the Icon Picker.
 *
 * - List structure and searchable icon labels are stored in SharedPreferences under
 *   [LightspeedPreferences.KEY_CUSTOM_ICON_LISTS_JSON].
 * - Individual icon bitmaps are saved as PNG files under `filesDir/shortcut_icons/custom_list_<id>.png`
 *   so [LightspeedBackupEngine] automatically exports and restores them as Base64 strings under
 *   `"shortcut_icons"` alongside all other custom icons.
 */
object CustomIconListRepository {

    private const val FILE_PREFIX = "custom_list_"

    fun getCustomLists(context: Context): List<CustomIconList> {
        val raw = context.defaultPrefs().getString(LightspeedPreferences.KEY_CUSTOM_ICON_LISTS_JSON, null)
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val result = ArrayList<CustomIconList>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val listId = obj.optString("id", "").trim()
                val listName = obj.optString("name", "").trim()
                if (listId.isEmpty() || listName.isEmpty()) continue
                val itemsArr = obj.optJSONArray("items") ?: JSONArray()
                val entries = ArrayList<CustomIconEntry>(itemsArr.length())
                for (j in 0 until itemsArr.length()) {
                    val itemObj = itemsArr.optJSONObject(j) ?: continue
                    val itemId = itemObj.optString("id", "").trim()
                    val itemLabel = itemObj.optString("label", "").trim()
                    if (itemId.isNotEmpty()) {
                        entries.add(CustomIconEntry(id = itemId, label = itemLabel.ifEmpty { "icon" }))
                    }
                }
                result.add(CustomIconList(id = listId, name = listName, items = entries))
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveCustomLists(context: Context, lists: List<CustomIconList>) {
        val arr = JSONArray()
        for (list in lists) {
            val obj = JSONObject().apply {
                put("id", list.id)
                put("name", list.name)
                val itemsArr = JSONArray()
                for (entry in list.items) {
                    itemsArr.put(
                        JSONObject().apply {
                            put("id", entry.id)
                            put("label", entry.label)
                        }
                    )
                }
                put("items", itemsArr)
            }
            arr.put(obj)
        }
        context.defaultPrefs().edit()
            .putString(LightspeedPreferences.KEY_CUSTOM_ICON_LISTS_JSON, arr.toString())
            .apply()
    }

    fun createCustomList(context: Context, name: String): CustomIconList {
        val cleanName = name.trim().ifEmpty { "Favorite Icons" }
        val current = getCustomLists(context).toMutableList()
        val existing = current.firstOrNull { it.name.equals(cleanName, ignoreCase = true) }
        if (existing != null) return existing
        val newList = CustomIconList(
            id = "list_" + UUID.randomUUID().toString().replace("-", "").take(10),
            name = cleanName,
            items = emptyList()
        )
        current.add(newList)
        saveCustomLists(context, current)
        return newList
    }

    fun deleteCustomList(context: Context, listId: String) {
        val current = getCustomLists(context).toMutableList()
        val target = current.firstOrNull { it.id == listId } ?: return
        val dir = File(context.filesDir, "shortcut_icons")
        for (entry in target.items) {
            try {
                File(dir, "$FILE_PREFIX${entry.id}.png").delete()
            } catch (_: Exception) {}
        }
        current.removeAll { it.id == listId }
        saveCustomLists(context, current)
    }

    fun addIconToCustomList(
        context: Context,
        listId: String,
        label: String,
        bitmap: Bitmap
    ): Boolean {
        val lists = getCustomLists(context).toMutableList()
        val idx = lists.indexOfFirst { it.id == listId }
        if (idx < 0) return false

        val entryId = UUID.randomUUID().toString().replace("-", "").take(12)
        val cleanLabel = label.trim().ifEmpty { "icon" }
        try {
            val dir = File(context.filesDir, "shortcut_icons")
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, "$FILE_PREFIX$entryId.png")
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
        } catch (_: Exception) {
            return false
        }

        val updatedItems = lists[idx].items.toMutableList().apply {
            add(CustomIconEntry(id = entryId, label = cleanLabel))
        }
        lists[idx] = lists[idx].copy(items = updatedItems)
        saveCustomLists(context, lists)
        return true
    }

    fun removeIconFromCustomList(context: Context, listId: String, entryId: String) {
        val lists = getCustomLists(context).toMutableList()
        val idx = lists.indexOfFirst { it.id == listId }
        if (idx < 0) return
        try {
            File(context.filesDir, "shortcut_icons/$FILE_PREFIX$entryId.png").delete()
        } catch (_: Exception) {}
        val updated = lists[idx].items.filterNot { it.id == entryId }
        lists[idx] = lists[idx].copy(items = updated)
        saveCustomLists(context, lists)
    }

    fun loadCustomEntryBitmap(context: Context, entryId: String): Bitmap? {
        return try {
            val file = File(context.filesDir, "shortcut_icons/$FILE_PREFIX$entryId.png")
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        } catch (_: Exception) {
            null
        }
    }
}
