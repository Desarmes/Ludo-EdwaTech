package com.edwatech.ludo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

private val trackCells = buildList {
    for (column in 1..5) add(6 to column)
    for (row in 5 downTo 0) add(row to 6)
    add(0 to 7)
    add(0 to 8)
    for (row in 1..5) add(row to 8)
    for (column in 9..14) add(6 to column)
    add(7 to 14)
    add(8 to 14)
    for (column in 13 downTo 9) add(8 to column)
    for (row in 9..14) add(row to 8)
    add(14 to 7)
    add(14 to 6)
    for (row in 13 downTo 9) add(row to 6)
    for (column in 5 downTo 0) add(8 to column)
    add(7 to 0)
    add(6 to 0)
}

private val homeLanes = listOf(
    listOf(7 to 1, 7 to 2, 7 to 3, 7 to 4, 7 to 5),
    listOf(1 to 7, 2 to 7, 3 to 7, 4 to 7, 5 to 7),
    listOf(7 to 13, 7 to 12, 7 to 11, 7 to 10, 7 to 9),
    listOf(13 to 7, 12 to 7, 11 to 7, 10 to 7, 9 to 7)
)
private val startIndices = listOf(0, 13, 26, 39)
private val safeTrackIndices = setOf(0, 13, 26, 39)
private val boardPlayerColors = listOf(
    Color(0xFFE85D52),
    Color(0xFF2978A8),
    Color(0xFF278A82),
    Color(0xFFE4B33F)
)
private val playerColors = listOf(boardPlayerColors[0], boardPlayerColors[2])
private val playerNames = listOf("Rouge", "Vert")
private val boardColor = Color(0xFFF2F0E8)
private val inkColor = Color(0xFF17312B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                LudoMainScreen()
            }
        }
    }
}

@Composable
fun LudoMainScreen() {
    var page by remember { mutableStateOf("home") }
    if (page == "home") {
        LudoHomeMenu(
            onLocal = { page = "local" },
            onRooms = { page = "online" },
            onMatchmaking = { page = "match" },
            onLeaderboard = { page = "rank" },
            onAdmin = { page = "admin" }
        )
        return
    }
    if (page == "online") {
        OnlineLobbyScreen(onBack = { page = "home" })
        return
    }
    if (page == "match") {
        MatchmakingScreen(onBack = { page = "home" })
        return
    }
    if (page == "rank") {
        LeaderboardScreen(onBack = { page = "home" })
        return
    }
    if (page == "admin") {
        AdminPanelScreen(onBack = { page = "home" })
        return
    }

    var pawns by remember { mutableStateOf(listOf(List(4) { -1 }, List(4) { -1 })) }
    var activePlayer by remember { mutableIntStateOf(0) }
    var dice by remember { mutableIntStateOf(0) }
    var awaitingPawn by remember { mutableStateOf(false) }
    var winner by remember { mutableIntStateOf(-1) }
    var status by remember { mutableStateOf("Lance le dé pour commencer.") }

    val movablePawns = if (awaitingPawn) {
        pawns[activePlayer].indices.filter { canMove(pawns[activePlayer][it], dice) }.toSet()
    } else {
        emptySet()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(boardColor)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = { page = "home" }) { Text("← MENU", color = inkColor) }
        HaitiFlagPanel(Modifier.height(84.dp))
        Text(
            text = "LUDO EXPRESS",
            color = inkColor,
            fontSize = 27.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "PARTIE LOCALE  /  2 JOUEURS",
            color = Color(0xFF66756D),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        LudoBoard(
            pawns = pawns,
            activePlayer = activePlayer,
            movablePawns = movablePawns,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (winner >= 0) "${playerNames[winner]} gagne !" else "Tour de ${playerNames[activePlayer]}",
                color = if (winner >= 0) playerColors[winner] else playerColors[activePlayer],
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (dice == 0) "Dé : -" else "Dé : $dice",
                color = inkColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(text = status, color = Color(0xFF52645C), fontSize = 14.sp)

        if (winner >= 0) {
            Button(
                onClick = {
                    pawns = listOf(List(4) { -1 }, List(4) { -1 })
                    activePlayer = 0
                    dice = 0
                    awaitingPawn = false
                    winner = -1
                    status = "Lance le dé pour commencer."
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = inkColor),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Nouvelle partie", fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = {
                    val result = Random.nextInt(1, 7)
                    dice = result
                    val available = pawns[activePlayer].indices.filter {
                        canMove(pawns[activePlayer][it], result)
                    }
                    if (available.isEmpty()) {
                        status = "Aucun coup possible. Au tour de ${playerNames[1 - activePlayer]}."
                        activePlayer = 1 - activePlayer
                    } else {
                        awaitingPawn = true
                        status = if (available.size == 1) "Un seul pion peut avancer." else "Choisis le pion à déplacer."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !awaitingPawn,
                colors = ButtonDefaults.buttonColors(containerColor = inkColor),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Lancer le dé", fontWeight = FontWeight.Bold)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pawns[activePlayer].forEachIndexed { index, progress ->
                    val legal = index in movablePawns
                    Button(
                        onClick = {
                            if (!legal) return@Button
                            val movedTo = if (progress < 0) 0 else progress + dice
                            val updated = pawns.map { it.toMutableList() }
                            updated[activePlayer][index] = movedTo

                            var captured = false
                            if (movedTo in 0..51) {
                                val globalIndex = (startIndices[activePlayer] + movedTo) % trackCells.size
                                if (globalIndex !in safeTrackIndices) {
                                    val opponent = 1 - activePlayer
                                    updated[opponent].indices.forEach { opponentPawn ->
                                        val opponentProgress = updated[opponent][opponentPawn]
                                        if (opponentProgress in 0..51 &&
                                            (startIndices[opponent] + opponentProgress) % trackCells.size == globalIndex
                                        ) {
                                            updated[opponent][opponentPawn] = -1
                                            captured = true
                                        }
                                    }
                                }
                            }

                            pawns = updated.map { it.toList() }
                            awaitingPawn = false
                            if (pawns[activePlayer].all { it == 57 }) {
                                winner = activePlayer
                                status = "Les quatre pions sont arrivés."
                            } else if (dice == 6) {
                                status = if (captured) "Pion capturé. Tu rejoues."
                                else "Six obtenu. Tu rejoues."
                            } else {
                                val nextPlayer = 1 - activePlayer
                                status = if (captured) "Pion capturé. Au tour de ${playerNames[nextPlayer]}."
                                else "Au tour de ${playerNames[nextPlayer]}."
                                activePlayer = nextPlayer
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = legal,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (legal) playerColors[activePlayer] else Color(0xFFD8D8D0),
                            disabledContainerColor = Color(0xFFD8D8D0)
                        ),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        val label = when (progress) {
                            -1 -> "Maison"
                            57 -> "Fini"
                            else -> "${progress + 1}/57"
                        }
                        Text(
                            text = "P${index + 1}\n$label",
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LudoBoard(
    pawns: List<List<Int>>,
    activePlayer: Int,
    movablePawns: Set<Int>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val cell = size.minDimension / 15f
        val left = (size.width - cell * 15f) / 2f
        val top = (size.height - cell * 15f) / 2f
        fun center(row: Int, column: Int) = Offset(
            left + (column + 0.5f) * cell,
            top + (row + 0.5f) * cell
        )

        drawRect(boardPlayerColors[0].copy(alpha = 0.16f), Offset(left, top + cell * 9), Size(cell * 6, cell * 6))
        drawRect(boardPlayerColors[1].copy(alpha = 0.16f), Offset(left, top), Size(cell * 6, cell * 6))
        drawRect(boardPlayerColors[2].copy(alpha = 0.16f), Offset(left + cell * 9, top), Size(cell * 6, cell * 6))
        drawRect(boardPlayerColors[3].copy(alpha = 0.16f), Offset(left + cell * 9, top + cell * 9), Size(cell * 6, cell * 6))
        drawRect(Color(0xFFE5E2D8), Offset(left + cell * 6, top + cell * 6), Size(cell * 3, cell * 3))

        trackCells.forEachIndexed { index, (row, column) ->
            val cellColor = when (index) {
                0 -> boardPlayerColors[0]
                13 -> boardPlayerColors[1]
                26 -> boardPlayerColors[2]
                39 -> boardPlayerColors[3]
                in safeTrackIndices -> Color(0xFFFFD77A)
                else -> Color.White
            }
            drawRoundRect(
                color = cellColor,
                topLeft = Offset(left + column * cell + cell * 0.06f, top + row * cell + cell * 0.06f),
                size = Size(cell * 0.88f, cell * 0.88f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cell * 0.12f)
            )
        }

        homeLanes.forEachIndexed { player, lane ->
            lane.forEach { (row, column) ->
                drawRoundRect(
                    color = boardPlayerColors[player].copy(alpha = 0.58f),
                    topLeft = Offset(left + column * cell + cell * 0.06f, top + row * cell + cell * 0.06f),
                    size = Size(cell * 0.88f, cell * 0.88f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cell * 0.12f)
                )
            }
        }
        drawCircle(Color(0xFFE5B84F), radius = cell * 0.42f, center = center(7, 7))

        val baseCells = listOf(
            listOf(10 to 2, 10 to 4, 12 to 2, 12 to 4),
            listOf(2 to 2, 2 to 4, 4 to 2, 4 to 4),
            listOf(2 to 10, 2 to 12, 4 to 10, 4 to 12),
            listOf(10 to 10, 10 to 12, 12 to 10, 12 to 12)
        )
        pawns.forEachIndexed { player, playerPawns ->
            val seat = if (pawns.size == 2) listOf(0, 2)[player] else player
            playerPawns.forEachIndexed { pawn, progress ->
                val position = when {
                    progress < 0 -> baseCells[seat][pawn]
                    progress == 57 -> 7 to 7
                    progress >= 52 -> homeLanes[seat][progress - 52]
                    else -> trackCells[(startIndices[seat] + progress) % trackCells.size]
                }
                val stackOffset = Offset(
                    ((pawn % 2) - 0.5f) * cell * 0.18f,
                    ((pawn / 2) - 0.5f) * cell * 0.18f
                )
                val isMovable = player == activePlayer && pawn in movablePawns
                val pawnCenter = center(position.first, position.second) + stackOffset
                if (isMovable) {
                    drawCircle(Color(0xFFFFC94A), radius = cell * 0.36f, center = pawnCenter)
                }
                drawCircle(Color.White, radius = cell * 0.28f, center = pawnCenter)
                drawCircle(boardPlayerColors[seat], radius = cell * 0.2f, center = pawnCenter)
                drawCircle(
                    color = Color(0xFF17312B),
                    radius = cell * 0.2f,
                    center = pawnCenter,
                    style = Stroke(width = cell * 0.035f)
                )
            }
        }
    }
}

private fun canMove(progress: Int, dice: Int): Boolean = when {
    dice == 0 -> false
    progress < 0 -> dice == 6
    else -> progress + dice <= 57
}