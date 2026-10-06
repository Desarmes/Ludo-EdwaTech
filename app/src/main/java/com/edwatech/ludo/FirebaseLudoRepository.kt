package com.edwatech.ludo

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import com.google.firebase.functions.FirebaseFunctions

 data class OnlinePlayer(
    val uid: String,
    val displayName: String,
    val color: Int,
    val pawns: List<Int>
)

data class OnlineRoom(
    val id: String,
    val code: String,
    val mode: String,
    val ranked: Boolean,
    val status: String,
    val players: List<OnlinePlayer>,
    val activeUid: String,
    val dice: Int,
    val lastDice: Int,
    val winnerUid: String?,
    val winningTeam: Int?,
    val message: String
)

data class QueueTicket(
    val status: String,
    val roomId: String?
)

data class SeasonInfo(
    val id: String,
    val number: Int,
    val startsAt: Long,
    val endsAt: Long
)

data class RankEntry(
    val uid: String,
    val displayName: String,
    val region: String,
    val points: Int,
    val wins: Int,
    val games: Int,
    val tier: String
)

data class BannedPlayer(
    val uid: String,
    val reason: String,
    val bannedAt: Long
)

class FirebaseLudoRepository {
    private val auth = FirebaseAuth.getInstance()
    private val functions = FirebaseFunctions.getInstance("us-central1")
    private val database = FirebaseDatabase.getInstance()

    val currentUid: String?
        get() = auth.currentUser?.uid

    val hasPlayerAccount: Boolean
        get() = auth.currentUser?.isAnonymous == false

    fun ensureGuest(onComplete: (String?, String?) -> Unit) {
        val current = auth.currentUser
        if (current != null) {
            onComplete(current.uid, null)
            return
        }
        auth.signInAnonymously()
            .addOnSuccessListener { onComplete(it.user?.uid, null) }
            .addOnFailureListener { onComplete(null, it.message ?: "Guest sign-in failed.") }
    }

    fun createAccount(email: String, password: String, onComplete: (String?) -> Unit) {
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { onComplete(null) }
            .addOnFailureListener { onComplete(it.message ?: "Account creation failed.") }
    }

    fun signIn(email: String, password: String, onComplete: (String?) -> Unit) {
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { onComplete(null) }
            .addOnFailureListener { onComplete(it.message ?: "Sign-in failed.") }
    }

    fun setProfile(displayName: String, region: String, onComplete: (String?) -> Unit) {
        call(
            "setPlayerProfile",
            mapOf("displayName" to displayName, "region" to region.uppercase()),
            onComplete = { _, error -> onComplete(error) }
        )
    }

    fun getSeason(onComplete: (SeasonInfo?, String?) -> Unit) {
        call("getSeasonInfo", emptyMap()) { result, error ->
            if (error != null || result == null) {
                onComplete(null, error ?: "Season information is unavailable.")
                return@call
            }
            onComplete(
                SeasonInfo(
                    id = result.string("id"),
                    number = result.int("number"),
                    startsAt = result.long("startsAt"),
                    endsAt = result.long("endsAt")
                ),
                null
            )
        }
    }

    fun createRoom(onComplete: (String?, String?, String?) -> Unit) {
        call("createRoom", emptyMap()) { result, error ->
            if (error != null || result == null) {
                onComplete(null, null, error ?: "Could not create a room.")
            } else {
                onComplete(result.string("roomId"), result.string("code"), null)
            }
        }
    }

    fun joinRoom(code: String, onComplete: (String?, String?) -> Unit) {
        call("joinRoom", mapOf("code" to code)) { result, error ->
            if (error != null || result == null) {
                onComplete(null, error ?: "Could not join the room.")
            } else {
                onComplete(result.string("roomId"), null)
            }
        }
    }

    fun joinMatchmaking(mode: String, onComplete: (QueueTicket?, String?) -> Unit) {
        call("joinMatchmaking", mapOf("mode" to mode)) { result, error ->
            if (error != null || result == null) {
                onComplete(null, error ?: "Could not join matchmaking.")
            } else {
                onComplete(
                    QueueTicket(result.string("status"), result["roomId"] as? String),
                    null
                )
            }
        }
    }

    fun leaveMatchmaking(region: String, mode: String, onComplete: (String?) -> Unit) {
        call("leaveMatchmaking", mapOf("region" to region, "mode" to mode)) { _, error -> onComplete(error) }
    }

    fun observeQueueTicket(
        seasonId: String,
        region: String,
        mode: String,
        uid: String,
        onTicket: (QueueTicket?) -> Unit,
        onError: (String) -> Unit
    ): ValueEventListener {
        val reference = database.reference.child("ludo")
            .child("queues").child(seasonId).child(region.uppercase()).child(mode).child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onTicket(if (snapshot.exists()) QueueTicket(
                    status = snapshot.child("status").getValue(String::class.java) ?: "waiting",
                    roomId = snapshot.child("roomId").getValue(String::class.java)
                ) else null)
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.message)
            }
        }
        reference.addValueEventListener(listener)
        return listener
    }

    fun removeQueueTicketListener(
        seasonId: String,
        region: String,
        mode: String,
        uid: String,
        listener: ValueEventListener
    ) {
        database.reference.child("ludo").child("queues").child(seasonId)
            .child(region.uppercase()).child(mode).child(uid).removeEventListener(listener)
    }

    fun rollDice(roomId: String, onComplete: (String?) -> Unit) {
        call("rollDice", mapOf("roomId" to roomId)) { _, error -> onComplete(error) }
    }

    fun movePawn(roomId: String, pawnIndex: Int, onComplete: (String?) -> Unit) {
        call("movePawn", mapOf("roomId" to roomId, "pawnIndex" to pawnIndex)) { _, error -> onComplete(error) }
    }

    fun observeRoom(
        roomId: String,
        onRoom: (OnlineRoom?) -> Unit,
        onError: (String) -> Unit
    ): ValueEventListener {
        val reference = database.reference.child("ludo").child("rooms").child(roomId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onRoom(snapshot.toOnlineRoom(roomId))
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.message)
            }
        }
        reference.addValueEventListener(listener)
        return listener
    }

    fun removeRoomListener(roomId: String, listener: ValueEventListener) {
        database.reference.child("ludo").child("rooms").child(roomId).removeEventListener(listener)
    }

    fun observeLeaderboard(
        seasonId: String,
        region: String,
        mode: String,
        onEntries: (List<RankEntry>) -> Unit,
        onError: (String) -> Unit
    ): Pair<Query, ValueEventListener> {
        val query = database.reference
            .child("ludo")
            .child("seasons")
            .child(seasonId)
            .child("regions")
            .child(region.uppercase())
            .child("modes")
            .child(mode)
            .child("players")
            .orderByChild("points")
            .limitToLast(200)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val entries = snapshot.children.mapNotNull { player ->
                    val uid = player.key ?: return@mapNotNull null
                    RankEntry(
                        uid = uid,
                        displayName = player.child("displayName").getValue(String::class.java) ?: "Player",
                        region = player.child("region").getValue(String::class.java) ?: region,
                        points = player.child("points").intValue(),
                        wins = player.child("wins").intValue(),
                        games = player.child("games").intValue(),
                        tier = player.child("tier").getValue(String::class.java) ?: "Bronze"
                    )
                }.sortedByDescending { it.points }
                onEntries(entries)
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.message)
            }
        }
        query.addValueEventListener(listener)
        return query to listener
    }

    fun removeLeaderboardListener(query: Query, listener: ValueEventListener) {
        query.removeEventListener(listener)
    }

    fun claimAdmin(code: String, onComplete: (Boolean, String?) -> Unit) {
        call("claimAdmin", mapOf("code" to code)) { _, error ->
            if (error != null) {
                onComplete(false, error)
            } else {
                auth.currentUser?.getIdToken(true)
                    ?.addOnSuccessListener { token -> onComplete(token.claims["ludoAdmin"] == true, null) }
                    ?.addOnFailureListener { onComplete(false, it.message ?: "Could not refresh admin access.") }
                    ?: onComplete(false, "Sign in again and retry.")
            }
        }
    }

    fun checkAdminAccess(onComplete: (Boolean, String?) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onComplete(false, null)
            return
        }
        user.getIdToken(false)
            .addOnSuccessListener { onComplete(it.claims["ludoAdmin"] == true, null) }
            .addOnFailureListener { onComplete(false, it.message ?: "Could not check admin access.") }
    }

    fun banPlayer(uid: String, reason: String, onComplete: (String?) -> Unit) {
        call("banPlayer", mapOf("uid" to uid, "reason" to reason)) { _, error -> onComplete(error) }
    }

    fun unbanPlayer(uid: String, onComplete: (String?) -> Unit) {
        call("unbanPlayer", mapOf("uid" to uid)) { _, error -> onComplete(error) }
    }

    fun listBans(onComplete: (List<BannedPlayer>?, String?) -> Unit) {
        functions.getHttpsCallable("listBans").call()
            .addOnSuccessListener { result ->
                val bans = (result.data as? List<*>)?.mapNotNull { item ->
                    val values = item as? Map<*, *> ?: return@mapNotNull null
                    BannedPlayer(
                        uid = values["uid"] as? String ?: return@mapNotNull null,
                        reason = values["reason"] as? String ?: "",
                        bannedAt = (values["bannedAt"] as? Number)?.toLong() ?: 0L
                    )
                } ?: emptyList()
                onComplete(bans, null)
            }
            .addOnFailureListener { onComplete(null, it.message ?: "Could not load bans.") }
    }

    private fun call(
        name: String,
        data: Map<String, Any>,
        onComplete: (Map<String, Any?>?, String?) -> Unit
    ) {
        functions.getHttpsCallable(name).call(data)
            .addOnSuccessListener { result ->
                val map = result.data as? Map<*, *>
                val typed = map?.entries?.mapNotNull { (key, value) ->
                    (key as? String)?.let { it to value }
                }?.toMap()
                onComplete(typed, null)
            }
            .addOnFailureListener { onComplete(null, it.message ?: "$name failed.") }
    }
}

private fun DataSnapshot.toOnlineRoom(roomId: String): OnlineRoom? {
    if (!exists()) return null
    val players = child("players").children.mapNotNull { player ->
        val uid = player.key ?: return@mapNotNull null
        OnlinePlayer(
            uid = uid,
            displayName = player.child("displayName").getValue(String::class.java) ?: "Player",
            color = player.child("color").intValue(),
            pawns = (0..3).map { index -> player.child("pawns").child(index.toString()).intValue(-1) }
        )
    }.sortedBy { it.color }
    return OnlineRoom(
        id = roomId,
        code = child("code").getValue(String::class.java) ?: "",
        mode = child("mode").getValue(String::class.java) ?: "room",
        ranked = child("ranked").getValue(Boolean::class.javaObjectType) ?: false,
        status = child("status").getValue(String::class.java) ?: "waiting",
        players = players,
        activeUid = child("activeUid").getValue(String::class.java) ?: "",
        dice = child("dice").intValue(),
        lastDice = child("lastDice").intValue(),
        winnerUid = child("winnerUid").getValue(String::class.java),
        winningTeam = child("winningTeam").getValue(Int::class.javaObjectType),
        message = child("message").getValue(String::class.java) ?: ""
    )
}

private fun DataSnapshot.intValue(default: Int = 0): Int = getValue(Int::class.javaObjectType) ?: default
private fun Map<String, Any?>.string(key: String): String = this[key] as? String ?: ""
private fun Map<String, Any?>.int(key: String): Int = (this[key] as? Number)?.toInt() ?: 0
private fun Map<String, Any?>.long(key: String): Long = (this[key] as? Number)?.toLong() ?: 0L
