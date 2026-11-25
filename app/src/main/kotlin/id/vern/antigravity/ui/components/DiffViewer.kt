package id.vern.antigravity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.vern.antigravity.R
import id.vern.antigravity.ui.theme.AntigravityGreen
import id.vern.antigravity.ui.theme.AntigravityRed

enum class DiffLineType {
    UNCHANGED,
    ADDED,
    REMOVED
}

data class DiffLine(
    val type: DiffLineType,
    val oldLineNum: Int?,
    val newLineNum: Int?,
    val text: String
)

@Composable
fun DiffViewer(
    fileName: String,
    originalText: String,
    modifiedText: String,
    onApplyChanges: () -> Unit,
    onDiscardChanges: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val diffLines = remember(originalText, modifiedText) {
        computeDiff(originalText, modifiedText)
    }

    val additions = diffLines.count { it.type == DiffLineType.ADDED }
    val deletions = diffLines.count { it.type == DiffLineType.REMOVED }

    val hScroll = rememberScrollState()

    Column(modifier = modifier.fillMaxSize()) {

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Diff: $fileName",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "+$additions", color = AntigravityGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "-$deletions", color = AntigravityRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onDiscardChanges,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.diff_btn_discard), fontSize = 12.sp)
                    }

                    Button(
                        onClick = onApplyChanges,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AntigravityGreen),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.diff_btn_apply), fontSize = 12.sp)
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D1117))
                .horizontalScroll(hScroll)
        ) {
            items(diffLines) { line ->
                val bg = when (line.type) {
                    DiffLineType.ADDED -> Color(0xFF1B3C2B)
                    DiffLineType.REMOVED -> Color(0xFF3D1E24)
                    DiffLineType.UNCHANGED -> Color.Transparent
                }

                val textColor = when (line.type) {
                    DiffLineType.ADDED -> Color(0xFF7EE787)
                    DiffLineType.REMOVED -> Color(0xFFFFA198)
                    DiffLineType.UNCHANGED -> Color(0xFFC9D1D9)
                }

                val marker = when (line.type) {
                    DiffLineType.ADDED -> "+"
                    DiffLineType.REMOVED -> "-"
                    DiffLineType.UNCHANGED -> " "
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bg)
                        .padding(vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        text = line.oldLineNum?.toString() ?: "",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF6E7681),
                        modifier = Modifier.width(36.dp)
                    )
                    Text(
                        text = line.newLineNum?.toString() ?: "",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF6E7681),
                        modifier = Modifier.width(36.dp)
                    )
                    Text(
                        text = marker,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        modifier = Modifier.width(16.dp)
                    )
                    Text(
                        text = line.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = textColor
                    )
                }
            }
        }
    }
}

private fun computeDiff(oldText: String, newText: String): List<DiffLine> {
    val oldLines = oldText.split("\n")
    val newLines = newText.split("\n")
    val result = mutableListOf<DiffLine>()

    var oIdx = 0
    var nIdx = 0

    while (oIdx < oldLines.size || nIdx < newLines.size) {
        if (oIdx < oldLines.size && nIdx < newLines.size) {
            if (oldLines[oIdx] == newLines[nIdx]) {
                result.add(
                    DiffLine(
                        type = DiffLineType.UNCHANGED,
                        oldLineNum = oIdx + 1,
                        newLineNum = nIdx + 1,
                        text = oldLines[oIdx]
                    )
                )
                oIdx++
                nIdx++
            } else {
                result.add(
                    DiffLine(
                        type = DiffLineType.REMOVED,
                        oldLineNum = oIdx + 1,
                        newLineNum = null,
                        text = oldLines[oIdx]
                    )
                )
                oIdx++
                result.add(
                    DiffLine(
                        type = DiffLineType.ADDED,
                        oldLineNum = null,
                        newLineNum = nIdx + 1,
                        text = newLines[nIdx]
                    )
                )
                nIdx++
            }
        } else if (oIdx < oldLines.size) {
            result.add(
                DiffLine(
                    type = DiffLineType.REMOVED,
                    oldLineNum = oIdx + 1,
                    newLineNum = null,
                    text = oldLines[oIdx]
                )
            )
            oIdx++
        } else if (nIdx < newLines.size) {
            result.add(
                DiffLine(
                    type = DiffLineType.ADDED,
                    oldLineNum = null,
                    newLineNum = nIdx + 1,
                    text = newLines[nIdx]
                )
            )
            nIdx++
        }
    }
    return result
}

