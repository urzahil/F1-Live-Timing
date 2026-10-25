package rocks.urzahil.f1livetiming.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.urzahil.f1livetiming.data.model.FiaDocument
import rocks.urzahil.f1livetiming.data.model.SeasonRaceItem
import rocks.urzahil.f1livetiming.ui.components.PdfViewer
import rocks.urzahil.f1livetiming.ui.theme.F1CardBorder
import rocks.urzahil.f1livetiming.ui.theme.F1CardSurface
import rocks.urzahil.f1livetiming.ui.theme.F1Red
import rocks.urzahil.f1livetiming.ui.theme.F1TextMuted
import rocks.urzahil.f1livetiming.ui.theme.F1TextPrimary
import rocks.urzahil.f1livetiming.ui.theme.F1TextSecondary
import rocks.urzahil.f1livetiming.ui.viewmodel.FiaDocumentRaces
import rocks.urzahil.f1livetiming.ui.viewmodel.FiaDocumentsState
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Page title of the FIA documents tool: the open document, else the selected race, else null. */
internal fun FiaDocumentsState.fiaDocumentsTitle(): String? =
    document?.title?.takeIf { it.isNotBlank() } ?: race?.raceName?.takeIf { it.isNotBlank() }

/**
 * "FIA Documents" tool. Shows the season's races, then the documents of the selected race, then the
 * selected document in a PDF viewer; which level is showing follows [state].
 */
@Composable
fun FiaDocumentsScreen(
    races: List<SeasonRaceItem>,
    upcomingRace: SeasonRaceItem?,
    isCalendarLoading: Boolean,
    calendarError: String?,
    onRetryCalendar: () -> Unit,
    state: FiaDocumentsState,
    onSelectRace: (SeasonRaceItem) -> Unit,
    onRetryDocuments: () -> Unit,
    onOpenDocument: (FiaDocument) -> Unit,
    onRetryDocument: () -> Unit,
    contentPadding: PaddingValues
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        val document = state.document
        when {
            document != null -> DocumentViewer(document = document, state = state, onRetry = onRetryDocument)
            state.race != null -> DocumentList(state = state, onRetry = onRetryDocuments, onOpen = onOpenDocument)
            else -> RaceList(
                races = races,
                upcomingRace = upcomingRace,
                isLoading = isCalendarLoading,
                error = calendarError,
                onRetry = onRetryCalendar,
                onSelect = onSelectRace
            )
        }
    }
}

@Composable
private fun RaceList(
    races: List<SeasonRaceItem>,
    upcomingRace: SeasonRaceItem?,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onSelect: (SeasonRaceItem) -> Unit
) {
    // distinctBy: the upcoming race also counts as started once its weekend begins while the tool is open
    val listed = remember(races, upcomingRace) {
        (listOfNotNull(upcomingRace) + FiaDocumentRaces.started(races, System.currentTimeMillis()))
            .distinctBy { it.round }
    }
    if (listed.isEmpty()) {
        when {
            isLoading -> Loading()
            error != null -> Message(title = "Unable to load the races", message = error, onRetry = onRetry)
            else -> Message(title = "No races yet", message = "Documents appear here once the FIA publishes the first ones of the season.")
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(listed, key = { it.round.orEmpty() }) { race ->
            val (date, _) = formatUtcToLocal(race.date, null)
            ListCard(
                badge = "R${race.round.orEmpty()}",
                title = race.raceName.orEmpty(),
                subtitle = listOfNotNull(race.circuit?.circuitName, date.takeIf { it != "--" }).joinToString(" · "),
                onClick = { onSelect(race) },
                testTag = "fia_race_${race.round.orEmpty()}"
            )
        }
    }
}

@Composable
private fun DocumentList(state: FiaDocumentsState, onRetry: () -> Unit, onOpen: (FiaDocument) -> Unit) {
    if (state.documents.isEmpty()) {
        when {
            state.isLoading -> Loading()
            state.error != null -> Message(title = "Unable to load the documents", message = state.error, onRetry = onRetry)
            else -> Message(
                title = "No documents yet",
                message = "The FIA has not published any document for this race so far.",
                onRetry = onRetry
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(state.documents, key = { it.id ?: it.fileUrl.orEmpty().hashCode().toLong() }) { document ->
            val (number, title) = splitDocumentTitle(document.title.orEmpty())
            ListCard(
                badge = number,
                title = title,
                subtitle = listOfNotNull(
                    formatPublishedAt(document.publishedAt),
                    document.category?.joinToString(", ")?.takeIf { it.isNotBlank() }
                ).joinToString(" · "),
                onClick = { onOpen(document) },
                testTag = "fia_document_${document.id ?: 0}"
            )
        }
    }
}

@Composable
private fun DocumentViewer(document: FiaDocument, state: FiaDocumentsState, onRetry: () -> Unit) {
    val context = LocalContext.current
    val openInBrowser: () -> Unit = {
        document.fileUrl?.let { url ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: ActivityNotFoundException) {
            }
        }
    }
    // Set when the downloaded file turns out not to be renderable, so the same fallback as a failed download shows.
    var renderFailed by remember(state.pdfFile) { mutableStateOf(false) }
    val file = state.pdfFile

    when {
        file != null && !renderFailed -> PdfViewer(file = file, onError = { renderFailed = true })
        state.isPdfLoading -> Loading()
        else -> Message(
            title = "Unable to show this document",
            message = if (renderFailed) "The PDF could not be opened on this device." else state.pdfError
                ?: "The document is not available.",
            onRetry = onRetry,
            onOpenInBrowser = openInBrowser
        )
    }
}

@Composable
private fun ListCard(badge: String?, title: String, subtitle: String, onClick: () -> Unit, testTag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(F1CardSurface, RoundedCornerShape(8.dp))
            .border(1.dp, F1CardBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (badge != null) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .background(F1Red.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badge,
                    color = F1Red,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        } else {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = F1Red,
                modifier = Modifier.width(44.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = F1TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, color = F1TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = F1TextMuted
        )
    }
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = F1Red)
    }
}

@Composable
private fun Message(
    title: String,
    message: String,
    onRetry: (() -> Unit)? = null,
    onOpenInBrowser: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, color = F1TextPrimary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = message, color = F1TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
            if (onRetry != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = F1Red)) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry")
                }
            }
            if (onOpenInBrowser != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenInBrowser) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp), tint = F1TextPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open in browser", color = F1TextPrimary)
                }
            }
        }
    }
}

private val DocumentNumber = Regex("""^Doc\s+(\d+)\s*[-–]\s*(.+)$""", RegexOption.IGNORE_CASE)

/** Splits "Doc 44 - Provisional Starting Grid" into ("#44", "Provisional Starting Grid"). */
internal fun splitDocumentTitle(title: String): Pair<String?, String> {
    val match = DocumentNumber.find(title.trim()) ?: return null to title
    return "#${match.groupValues[1]}" to match.groupValues[2]
}

/** Formats an ISO timestamp such as "2026-10-25T00:31:00.000Z" as local date and time. */
private fun formatPublishedAt(value: String?): String? {
    if (value.isNullOrBlank() || value.length < 19) return null
    return try {
        val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(value.take(19)) ?: return null
        SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(parsed)
    } catch (_: Exception) {
        null
    }
}
