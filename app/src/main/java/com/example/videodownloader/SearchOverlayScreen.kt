package com.example.videodownloader

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray

object SearchHistoryManager {
    private const val PREFS_NAME = "search_history_prefs"
    private const val KEY_HISTORY = "history_list"

    fun getHistory(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_HISTORY, null) ?: return listOf("https://youtu.be/NTHBf...")
        return try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
            if (list.isEmpty()) listOf("https://youtu.be/NTHBf...") else list
        } catch (_: Exception) {
            listOf("https://youtu.be/NTHBf...")
        }
    }

    fun addHistory(context: Context, item: String) {
        if (item.isBlank()) return
        val current = getHistory(context).toMutableList()
        current.remove(item)
        current.add(0, item)
        val trimmed = current.take(10)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray(trimmed)
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    fun clearHistory(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_HISTORY).apply()
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchOverlayScreen(
    initialInput: String,
    onSearchExecute: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    var searchText by remember { mutableStateOf(initialInput) }
    var historyList by remember { mutableStateOf(SearchHistoryManager.getHistory(context)) }

    val hotSearches = listOf(
        "🔥 bhojpuri song",
        "🔥 hindi song",
        "south movies",
        "ringtone",
        "sad song",
        "song"
    )

    BackHandler {
        onBack()
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // ── Top Search Header Bar ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Text("⟨", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("Search or enter url", fontSize = 14.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        if (searchText.isNotBlank()) {
                            SearchHistoryManager.addHistory(context, searchText)
                            onSearchExecute(searchText)
                        }
                    }
                ),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .focusRequester(focusRequester)
            )

            Spacer(modifier = Modifier.width(8.dp))

            TextButton(
                onClick = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    if (searchText.isNotBlank()) {
                        SearchHistoryManager.addHistory(context, searchText)
                        onSearchExecute(searchText)
                    }
                }
            ) {
                Text("ENTER", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Search History Section ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Search History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = {
                    SearchHistoryManager.clearHistory(context)
                    historyList = emptyList()
                }
            ) {
                Text("🗑️", fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (historyList.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(historyList) { historyItem ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.clickable {
                            searchText = historyItem
                            SearchHistoryManager.addHistory(context, historyItem)
                            onSearchExecute(historyItem)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🌐 ", fontSize = 12.sp)
                            Text(
                                text = if (historyItem.length > 25) historyItem.substring(0, 25) + "..." else historyItem,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Hot Searches Section ──
        Text(
            text = "Hot Searches",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Wrapped Flow Row of Hot Search Tag Chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            hotSearches.forEach { tag ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clickable {
                        val cleanQuery = tag.replace("🔥 ", "").trim()
                        searchText = cleanQuery
                        SearchHistoryManager.addHistory(context, cleanQuery)
                        onSearchExecute(cleanQuery)
                    }
                ) {
                    Text(
                        text = tag,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
