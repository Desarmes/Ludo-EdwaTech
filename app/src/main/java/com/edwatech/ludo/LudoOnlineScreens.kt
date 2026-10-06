package com.edwatech.ludo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.text.input.ImeAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val screenBackground = Color(0xFFF2F0E8)
private val screenInk = Color(0xFF17312B)
private val screenMuted = Color(0xFF65736D)
private val rankColors = mapOf(
    "Bronze" to Color(0xFF8C5937),
    "Silver" to Color(0xFF6F7C86),
    "Gold" to Color(0xFFB17B11),
    "Platinum" to Color(0xFF278A82),
    "Diamond" to Color(0xFF2978A8),
    "Master" to Color(0xFF8A5AA5),
    "Legend" to Color(0xFFB5483D),
    "Mythic" to Color(0xFF38304E)
)

@Composable
fun LudoHomeMenu(
    onLocal: () -> Unit,
    onOnline: () -> Unit,
    onLeaderboard: () -> Unit,
    onAdmin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBackground)
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(18.dp))
        Text("LUDO EXPRESS", color = screenInk, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text("MATCHES • RANKED • SAISON DE 60 JOURS", color = screenMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        MenuButton("Jouer en ligne", "Créer ou rejoindre une salle", onOnline, Color(0xFF17312B))
        MenuButton("Classement régional", "Top 200 • badges de saison", onLeaderboard, Color(0xFF278A82))
        MenuButton("Partie locale", "Deux joueurs sur cet appareil", onLocal, Color(0xFFE85D52))
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onAdmin) { Text("Administration", color = screenMuted) }
    }
}

@Composable
private fun MenuButton(title: String, subtitle: String, onClick: () -> Unit, color: Color) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
fun OnlineLobbyScreen(onBack: () -> Unit) {
    val repository = remember { FirebaseLudoRepository() }
    var hasAccount by remember { mutableStateOf(repository.hasPlayerAccount) }
    var uid by remember { mutableStateOf(if (repository.hasPlayerAccount) repository.currentUid else null) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("HT") }
    var roomCode by remember { mutableStateOf("") }
    var activeRoomId by remember { mutableStateOf<String?>(null) }
    var createdCode by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(if (hasAccount) "Compte joueur prêt." else "Connecte-toi pour jouer en ranked.") }

    val activeId = activeRoomId
    if (activeId != null && uid != null) {
        OnlineRoomScreen(
            repository = repository,
            roomId = activeId,
            uid = uid!!,
            createdCode = createdCode,
            onBack = {
                activeRoomId = null
                createdCode = null
                status = "Tu as quitté la vue de cette salle."
            }
        )
        return
    }

    fun prepareProfile(action: () -> Unit) {
        val userId = uid ?: repository.currentUid
        if (!hasAccount || userId == null) {
            status = "Crée ou connecte un compte joueur pour participer au ranked."
            return
        }
        val cleanName = displayName.trim()
        val cleanRegion = region.trim().uppercase()
        if (cleanName.length !in 2..24 || !cleanRegion.matches(Regex("[A-Z]{2}"))) {
            status = "Entre un pseudo (2–24 caractères) et une région ISO à 2 lettres, par exemple HT."
            return
        }
        busy = true
        repository.setProfile(cleanName, cleanRegion) { profileError ->
            if (profileError != null) {
                busy = false
                status = profileError
            } else {
                action()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(screenBackground).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Menu") }
        Text("JOUER EN LIGNE", color = screenInk, fontSize = 25.sp, fontWeight = FontWeight.Black)
        Text("Un joueur crée une salle et partage son code à six caractères.", color = screenMuted, fontSize = 14.sp)
        if (!hasAccount) {
            Text("Un compte e-mail est requis pour protéger le classement et les bannissements.", color = screenMuted, fontSize = 13.sp)
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("E-mail") },
                singleLine = true
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Mot de passe") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        busy = true
                        repository.createAccount(email, password) { error ->
                            busy = false
                            if (error == null) {
                                uid = repository.currentUid
                                hasAccount = repository.hasPlayerAccount
                                password = ""
                                status = "Compte créé. Choisis ton pseudo et ta région."
                            } else status = error
                        }
                    },
                    enabled = !busy
                ) { Text("Créer un compte") }
                Button(
                    onClick = {
                        busy = true
                        repository.signIn(email, password) { error ->
                            busy = false
                            if (error == null) {
                                uid = repository.currentUid
                                hasAccount = repository.hasPlayerAccount
                                password = ""
                                status = "Connexion réussie."
                            } else status = error
                        }
                    },
                    enabled = !busy,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = screenInk)
                ) { Text("Connexion") }
            }
        }
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it.take(24) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Pseudo") },
            singleLine = true
        )
        OutlinedTextField(
            value = region,
            onValueChange = { region = it.filter(Char::isLetter).take(2).uppercase() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Région (code à 2 lettres)") },
            supportingText = { Text("Le choix est conservé avec ton profil joueur.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done)
        )
        Button(
            onClick = {
                prepareProfile {
                    repository.createRoom { roomId, code, error ->
                        busy = false
                        if (error != null) status = error else {
                            createdCode = code
                            activeRoomId = roomId
                            status = "Salle créée."
                        }
                    }
                }
            },
            enabled = hasAccount && !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = screenInk)
        ) { Text(if (busy) "Création…" else "Créer une salle") }
        Divider(color = Color(0xFFD7D9D0))
        OutlinedTextField(
            value = roomCode,
            onValueChange = { roomCode = it.filter(Char::isLetterOrDigit).take(6).uppercase() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Code de salle") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done)
        )
        Button(
            onClick = {
                prepareProfile {
                    repository.joinRoom(roomCode) { roomId, error ->
                        busy = false
                        if (error != null) status = error else activeRoomId = roomId
                    }
                }
            },
            enabled = hasAccount && !busy && roomCode.length == 6,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF278A82))
        ) { Text(if (busy) "Connexion…" else "Rejoindre la salle") }
        Text(status, color = screenMuted, fontSize = 13.sp)
    }
}

@Composable
private fun OnlineRoomScreen(
    repository: FirebaseLudoRepository,
    roomId: String,
    uid: String,
    createdCode: String?,
    onBack: () -> Unit
) {
    var room by remember(roomId) { mutableStateOf<OnlineRoom?>(null) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    DisposableEffect(roomId) {
        val listener = repository.observeRoom(
            roomId,
            onRoom = { room = it },
            onError = { message = it }
        )
        onDispose { repository.removeRoomListener(roomId, listener) }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(screenBackground).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Quitter la vue") }
        Text("SALLE EN LIGNE", color = screenInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
        val code = room?.code ?: createdCode.orEmpty()
        if (code.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CODE  $code", color = screenInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { clipboard.setText(AnnotatedString(code)); message = "Code copié." }) {
                    Text("Copier")
                }
            }
        }
        val currentRoom = room
        if (currentRoom == null) {
            Text("Chargement de la salle…", color = screenMuted)
        } else if (currentRoom.status == "waiting") {
            Text("En attente du deuxième joueur.", color = screenMuted)
            currentRoom.players.forEach { Text("${it.displayName} • ${it.uid.take(6)}", color = screenInk) }
        } else {
            OnlineBoard(
                repository = repository,
                room = currentRoom,
                uid = uid,
                busy = busy,
                onBusy = { busy = it },
                onMessage = { message = it }
            )
        }
        if (message.isNotBlank()) Text(message, color = screenMuted, fontSize = 13.sp)
    }
}

@Composable
private fun OnlineBoard(
    repository: FirebaseLudoRepository,
    room: OnlineRoom,
    uid: String,
    busy: Boolean,
    onBusy: (Boolean) -> Unit,
    onMessage: (String) -> Unit
) {
    val self = room.players.firstOrNull { it.uid == uid }
    val activeColor = room.players.firstOrNull { it.uid == room.activeUid }?.color ?: -1
    val myTurn = room.status == "playing" && self?.uid == room.activeUid
    val redPawns = room.players.firstOrNull { it.color == 0 }?.pawns ?: List(4) { -1 }
    val tealPawns = room.players.firstOrNull { it.color == 1 }?.pawns ?: List(4) { -1 }
    val playerPawns = if (self?.color == 1) tealPawns else redPawns
    val movable = if (myTurn && room.dice > 0) {
        playerPawns.indices.filter { onlineCanMove(playerPawns[it], room.dice) }.toSet()
    } else emptySet()

    Text("${room.players.firstOrNull { it.color == 0 }?.displayName ?: "Rouge"}  VS  ${room.players.firstOrNull { it.color == 1 }?.displayName ?: "Turquoise"}", color = screenInk, fontWeight = FontWeight.Bold)
    LudoBoard(
        pawns = listOf(redPawns, tealPawns),
        activePlayer = activeColor.coerceIn(0, 1),
        movablePawns = movable,
        modifier = Modifier.fillMaxWidth()
    )

    when (room.status) {
        "finished" -> Text("${room.players.firstOrNull { it.uid == room.winnerUid }?.displayName ?: "Joueur"} gagne • classement mis à jour", color = Color(0xFFB17B11), fontWeight = FontWeight.Bold)
        else -> Text(if (myTurn) "À toi de jouer" else "Tour de ${room.players.firstOrNull { it.uid == room.activeUid }?.displayName ?: "l’adversaire"}", color = screenInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
    Text("Dé : ${if (room.dice == 0) room.lastDice.takeIf { it > 0 } ?: "–" else room.dice}", color = screenInk, fontSize = 18.sp)
    Button(
        onClick = {
            onBusy(true)
            repository.rollDice(room.id) { error -> onBusy(false); onMessage(error ?: "") }
        },
        enabled = myTurn && room.dice == 0 && !busy,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = screenInk)
    ) { Text(if (busy) "Envoi…" else "Lancer le dé") }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        playerPawns.forEachIndexed { index, progress ->
            val legal = index in movable
            Button(
                onClick = {
                    onBusy(true)
                    repository.movePawn(room.id, index) { error -> onBusy(false); onMessage(error ?: "") }
                },
                enabled = legal && !busy,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (self?.color == 1) Color(0xFF278A82) else Color(0xFFE85D52),
                    disabledContainerColor = Color(0xFFD8D8D0)
                )
            ) {
                val position = when (progress) {
                    -1 -> "Maison"
                    57 -> "Fini"
                    else -> "${progress + 1}/57"
                }
                Text("P${index + 1}\n$position", fontSize = 10.sp, lineHeight = 12.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun LeaderboardScreen(onBack: () -> Unit) {
    val repository = remember { FirebaseLudoRepository() }
    var season by remember { mutableStateOf<SeasonInfo?>(null) }
    var region by remember { mutableStateOf("HT") }
    var entries by remember { mutableStateOf<List<RankEntry>>(emptyList()) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        repository.ensureGuest { _, authError ->
            if (authError != null) error = authError
            else repository.getSeason { value, seasonError -> season = value; if (seasonError != null) error = seasonError }
        }
    }
    DisposableEffect(season?.id, region) {
        val currentSeason = season
        if (currentSeason == null || !region.matches(Regex("[A-Za-z]{2}"))) {
            onDispose { }
        } else {
            val (query, listener) = repository.observeLeaderboard(
                currentSeason.id,
                region,
                onEntries = { entries = it; error = "" },
                onError = { error = it }
            )
            onDispose { repository.removeLeaderboardListener(query, listener) }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(screenBackground).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Menu") }
        Text("CLASSEMENT RÉGIONAL", color = screenInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
        OutlinedTextField(
            value = region,
            onValueChange = { region = it.filter(Char::isLetter).take(2).uppercase() },
            label = { Text("Région ISO (ex. HT)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
        )
        season?.let {
            val daysLeft = ((it.endsAt - System.currentTimeMillis()).coerceAtLeast(0) / 86_400_000L).toInt()
            Text("SAISON ${it.number}  •  FIN DANS ${daysLeft} JOURS", color = screenMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        } ?: Text("Chargement de la saison…", color = screenMuted)
        if (error.isNotBlank()) Text(error, color = Color(0xFFB63831), fontSize = 13.sp)
        if (entries.isEmpty() && error.isBlank()) Text("Pas encore de joueurs classés pour cette région.", color = screenMuted)
        entries.forEachIndexed { index, entry ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("${index + 1}", color = screenMuted, modifier = Modifier.width(28.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
                Column(Modifier.weight(1f)) {
                    Text(entry.displayName, color = screenInk, fontWeight = FontWeight.SemiBold)
                    Text("${entry.points} pts  •  ${entry.wins} victoires / ${entry.games} parties", color = screenMuted, fontSize = 12.sp)
                }
                RankBadge(entry.tier)
            }
            Divider(color = Color(0xFFD7D9D0))
        }
    }
}

@Composable
private fun RankBadge(tier: String) {
    Surface(color = rankColors[tier] ?: screenInk, shape = RoundedCornerShape(6.dp)) {
        Text(
            text = tier.uppercase(),
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun AdminPanelScreen(onBack: () -> Unit) {
    val repository = remember { FirebaseLudoRepository() }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var adminCode by remember { mutableStateOf("") }
    var targetUid by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var isAdmin by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var bans by remember { mutableStateOf<List<BannedPlayer>>(emptyList()) }

    fun refreshBans() {
        repository.listBans { value, error ->
            if (error != null) status = error else bans = value.orEmpty()
        }
    }
    LaunchedEffect(Unit) {
        repository.checkAdminAccess { allowed, _ ->
            isAdmin = allowed
            if (allowed) refreshBans()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(screenBackground).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Menu") }
        Text("ADMINISTRATION", color = screenInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
        if (!isAdmin) {
            Text("Utilise ton compte Firebase. Le code admin est vérifié côté serveur.", color = screenMuted, fontSize = 13.sp)
            OutlinedTextField(email, { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("E-mail") }, singleLine = true)
            OutlinedTextField(
                password,
                { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Mot de passe du compte") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        busy = true
                        repository.createAccount(email, password) { error ->
                            busy = false
                            status = error ?: "Compte créé. Connecte-toi ensuite avec ce compte."
                        }
                    },
                    enabled = !busy
                ) { Text("Créer un compte") }
                Button(
                    onClick = {
                        busy = true
                        repository.signIn(email, password) { error ->
                            busy = false
                            if (error != null) status = error else repository.checkAdminAccess { allowed, checkError ->
                                isAdmin = allowed
                                status = checkError ?: if (allowed) "Accès admin confirmé." else "Compte connecté; entre le code d’activation privé." 
                                if (allowed) refreshBans()
                            }
                        }
                    },
                    enabled = !busy,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = screenInk)
                ) { Text("Connexion") }
            }
            if (!isAdmin) {
                OutlinedTextField(
                    adminCode,
                    { adminCode = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Code d’activation admin") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Button(
                    onClick = {
                        busy = true
                        repository.claimAdmin(adminCode) { allowed, error ->
                            busy = false
                            isAdmin = allowed
                            status = error ?: if (allowed) "Accès admin confirmé." else "Ce compte n’a pas les droits admin."
                            if (allowed) { adminCode = ""; refreshBans() }
                        }
                    },
                    enabled = !busy && adminCode.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB5483D))
                ) { Text(if (busy) "Vérification…" else "Activer l’accès admin") }
            }
        } else {
            Text("Compte admin vérifié côté serveur.", color = Color(0xFF278A82), fontWeight = FontWeight.Bold)
            OutlinedTextField(targetUid, { targetUid = it }, modifier = Modifier.fillMaxWidth(), label = { Text("UID joueur") }, singleLine = true)
            OutlinedTextField(reason, { reason = it.take(160) }, modifier = Modifier.fillMaxWidth(), label = { Text("Motif du bannissement") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        busy = true
                        repository.banPlayer(targetUid.trim(), reason.trim()) { error ->
                            busy = false
                            status = error ?: "Joueur banni."
                            if (error == null) refreshBans()
                        }
                    },
                    enabled = !busy && targetUid.isNotBlank(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB5483D))
                ) { Text("Bannir") }
                TextButton(onClick = { refreshBans() }, enabled = !busy) { Text("Actualiser") }
            }
            Text("COMPTES BANNIS", color = screenMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            bans.forEach { banned ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(banned.uid, color = screenInk, fontSize = 12.sp)
                        if (banned.reason.isNotBlank()) Text(banned.reason, color = screenMuted, fontSize = 11.sp)
                    }
                    TextButton(
                        onClick = {
                            repository.unbanPlayer(banned.uid) { error ->
                                status = error ?: "Joueur débanni."
                                if (error == null) refreshBans()
                            }
                        }
                    ) { Text("Débannir") }
                }
                Divider(color = Color(0xFFD7D9D0))
            }
        }
        if (status.isNotBlank()) Text(status, color = screenMuted, fontSize = 13.sp)
    }
}

private fun onlineCanMove(progress: Int, dice: Int): Boolean = when {
    dice !in 1..6 -> false
    progress < 0 -> dice == 6
    else -> progress + dice <= 57
}
