package dev.errnolink.tsuzuki.ui.details

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.presentation.manga.EditCoverAction
import eu.kanade.tachiyomi.ui.manga.MangaCoverScreenModel
import eu.kanade.presentation.util.Screen
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
internal fun Screen.MangaCoverContent(mangaId: Long, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val model = rememberScreenModel { MangaCoverScreenModel(mangaId) }
    val manga by model.state.collectAsStateWithLifecycle()
    val current = manga
    if (current == null) {
        LoadingScreen()
        return
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { model.editCover(context, it) }
    }
    val savePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.saveCover(context)
    }
    MangaCoverDialog(
        manga = current,
        isCustomCover = current.hasCustomCover(),
        snackbarHostState = model.snackbarHostState,
        onShareClick = { model.shareCover(context) },
        onSaveClick = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            ) {
                savePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                model.saveCover(context)
            }
        },
        onEditClick = { action ->
            when (action) {
                EditCoverAction.EDIT -> imagePicker.launch("image/*")
                EditCoverAction.DELETE -> model.deleteCustomCover(context)
            }
        },
        onDismissRequest = onDismissRequest,
    )
}
