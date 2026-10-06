const crypto = require('node:crypto');
const { initializeApp } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const { getDatabase } = require('firebase-admin/database');
const { defineSecret } = require('firebase-functions/params');
const { HttpsError, onCall } = require('firebase-functions/v2/https');
const { onValueWritten } = require('firebase-functions/v2/database');
const { logger } = require('firebase-functions');
const {
  movePawn: applyPawnMove,
  movablePawns,
  newPawns,
  rankForPoints,
  seasonFor
} = require('./gameLogic');

initializeApp();

const REGION = 'us-central1';
const ADMIN_BOOTSTRAP_CODE = defineSecret('LUDO_ADMIN_BOOTSTRAP_CODE');
const db = getDatabase();

function requireAuth(request) {
  if (!request.auth) throw new HttpsError('unauthenticated', 'Sign in first.');
  return request.auth;
}

async function requirePlayer(request) {
  const auth = requireAuth(request);
  if (auth.token.firebase?.sign_in_provider === 'anonymous') {
    throw new HttpsError('failed-precondition', 'Create a player account to join ranked matches.');
  }
  const ban = await db.ref(`ludo/bans/${auth.uid}`).get();
  if (ban.exists()) throw new HttpsError('permission-denied', 'This account is banned.');
  return auth;
}

async function requireViewer(request) {
  const auth = requireAuth(request);
  const ban = await db.ref(`ludo/bans/${auth.uid}`).get();
  if (ban.exists()) throw new HttpsError('permission-denied', 'This account is banned.');
  return auth;
}

function requireAdmin(request) {
  const auth = requireAuth(request);
  if (auth.token.ludoAdmin !== true) {
    throw new HttpsError('permission-denied', 'Admin access required.');
  }
  return auth;
}

function validUid(uid) {
  return typeof uid === 'string' && /^[A-Za-z0-9_-]{20,128}$/.test(uid);
}

function normalizeRegion(value) {
  if (typeof value !== 'string' || !/^[A-Za-z]{2}$/.test(value)) {
    throw new HttpsError('invalid-argument', 'Choose a valid two-letter region code.');
  }
  return value.toUpperCase();
}

async function getProfile(uid) {
  const snapshot = await db.ref(`ludo/profiles/${uid}`).get();
  if (!snapshot.exists()) {
    throw new HttpsError('failed-precondition', 'Create a player profile first.');
  }
  return snapshot.val();
}

function createCode() {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  const bytes = crypto.randomBytes(6);
  return Array.from(bytes, value => alphabet[value % alphabet.length]).join('');
}

function rankEntryUpdate(current, profile, delta, won, matchId, region, now) {
  const entry = current || {
    displayName: profile.displayName,
    region,
    points: 0,
    wins: 0,
    games: 0,
    tier: 'Bronze',
    processedMatches: {}
  };
  if (entry.processedMatches?.[matchId]) return;

  entry.displayName = profile.displayName;
  entry.region = region;
  entry.points = Math.max(0, (entry.points || 0) + delta);
  entry.games = (entry.games || 0) + 1;
  entry.wins = (entry.wins || 0) + (won ? 1 : 0);
  entry.tier = rankForPoints(entry.points);
  entry.updatedAt = now;
  entry.processedMatches = { ...(entry.processedMatches || {}), [matchId]: true };
  return entry;
}

async function awardFinishedMatch(roomId, room) {
  if (room.status !== 'finished' || !room.winnerUid || !room.seasonId || !room.region) return;
  const now = Date.now();
  const players = Object.entries(room.players || {});
  await Promise.all(players.map(async ([uid, player]) => {
    const ref = db.ref(`ludo/seasons/${room.seasonId}/regions/${room.region}/players/${uid}`);
    await ref.transaction(current => rankEntryUpdate(
      current,
      { displayName: player.displayName || 'Player' },
      uid === room.winnerUid ? 100 : 25,
      uid === room.winnerUid,
      roomId,
      room.region,
      now
    ));
  }));
}

exports.setPlayerProfile = onCall({ region: REGION }, async request => {
  const auth = await requirePlayer(request);
  const displayName = typeof request.data?.displayName === 'string'
    ? request.data.displayName.trim().slice(0, 24)
    : '';
  const region = normalizeRegion(request.data?.region);
  if (displayName.length < 2) throw new HttpsError('invalid-argument', 'Name must have 2 to 24 characters.');

  const profileRef = db.ref(`ludo/profiles/${auth.uid}`);
  const result = await profileRef.transaction(current => {
    if (current && current.region !== region) return;
    return { ...(current || {}), displayName, region, updatedAt: Date.now() };
  });
  if (!result.committed) {
    throw new HttpsError('failed-precondition', 'Region cannot be changed after the profile is created.');
  }
  return result.snapshot.val();
});

exports.getSeasonInfo = onCall({ region: REGION }, async request => {
  await requireViewer(request);
  return seasonFor();
});

exports.createRoom = onCall({ region: REGION }, async request => {
  const auth = await requirePlayer(request);
  const profile = await getProfile(auth.uid);
  const season = seasonFor();
  const roomsRef = db.ref('ludo/rooms');

  for (let attempt = 0; attempt < 12; attempt++) {
    const code = createCode();
    const roomRef = roomsRef.push();
    const codeResult = await db.ref(`ludo/roomCodes/${code}`).transaction(current => {
      if (current) return;
      return roomRef.key;
    });
    if (!codeResult.committed) continue;

    const room = {
      code,
      status: 'waiting',
      seasonId: season.id,
      seasonEndsAt: season.endsAt,
      region: profile.region,
      players: {
        [auth.uid]: {
          displayName: profile.displayName,
          color: 0,
          pawns: newPawns()
        }
      },
      activeUid: auth.uid,
      dice: 0,
      createdAt: Date.now(),
      lastActionAt: Date.now()
    };
    try {
      await roomRef.set(room);
      return { roomId: roomRef.key, code };
    } catch (error) {
      await db.ref(`ludo/roomCodes/${code}`).remove();
      throw error;
    }
  }
  throw new HttpsError('resource-exhausted', 'Could not allocate a room code. Try again.');
});

exports.joinRoom = onCall({ region: REGION }, async request => {
  const auth = await requirePlayer(request);
  const profile = await getProfile(auth.uid);
  const code = typeof request.data?.code === 'string' ? request.data.code.trim().toUpperCase() : '';
  if (!/^[A-HJ-NP-Z2-9]{6}$/.test(code)) throw new HttpsError('invalid-argument', 'Enter a valid six-character code.');

  const roomIdSnapshot = await db.ref(`ludo/roomCodes/${code}`).get();
  if (!roomIdSnapshot.exists()) throw new HttpsError('not-found', 'Room not found.');
  const roomId = roomIdSnapshot.val();
  const roomRef = db.ref(`ludo/rooms/${roomId}`);
  let failure = 'Room is no longer available.';
  const result = await roomRef.transaction(current => {
    if (!current || current.status !== 'waiting') return;
    if (current.players?.[auth.uid]) return;
    if (current.region !== profile.region) {
      failure = 'Both players must choose the same region.';
      return;
    }
    const hostUid = Object.keys(current.players || {})[0];
    if (!hostUid) return;
    current.players[auth.uid] = {
      displayName: profile.displayName,
      color: 1,
      pawns: newPawns()
    };
    current.status = 'playing';
    current.activeUid = hostUid;
    current.startedAt = Date.now();
    current.lastActionAt = Date.now();
    return current;
  });
  if (!result.committed) throw new HttpsError('failed-precondition', failure);
  return { roomId, code, room: result.snapshot.val() };
});

exports.rollDice = onCall({ region: REGION }, async request => {
  const auth = await requirePlayer(request);
  const roomId = request.data?.roomId;
  if (typeof roomId !== 'string' || !/^[A-Za-z0-9_-]{15,40}$/.test(roomId)) {
    throw new HttpsError('invalid-argument', 'Invalid room.');
  }
  const dice = crypto.randomInt(1, 7);
  let failure = 'It is not your turn.';
  const result = await db.ref(`ludo/rooms/${roomId}`).transaction(current => {
    if (!current || current.status !== 'playing') {
      failure = 'The match is not active.';
      return;
    }
    if (!current.players?.[auth.uid] || current.activeUid !== auth.uid || current.dice !== 0) return;
    const choices = movablePawns(current.players[auth.uid], dice);
    current.lastDice = dice;
    current.lastActionAt = Date.now();
    if (choices.length === 0) {
      const nextUid = Object.keys(current.players).find(uid => uid !== auth.uid);
      current.activeUid = nextUid;
      current.dice = 0;
      current.message = `${dice}: no legal move`;
    } else {
      current.dice = dice;
      current.message = '';
    }
    return current;
  });
  if (!result.committed) throw new HttpsError('failed-precondition', failure);
  return result.snapshot.val();
});

exports.movePawn = onCall({ region: REGION }, async request => {
  const auth = await requirePlayer(request);
  const roomId = request.data?.roomId;
  const pawnIndex = request.data?.pawnIndex;
  if (typeof roomId !== 'string' || !/^[A-Za-z0-9_-]{15,40}$/.test(roomId)) {
    throw new HttpsError('invalid-argument', 'Invalid room.');
  }
  if (!Number.isInteger(pawnIndex) || pawnIndex < 0 || pawnIndex > 3) {
    throw new HttpsError('invalid-argument', 'Invalid pawn.');
  }

  let failure = 'It is not your turn or that pawn cannot move.';
  const result = await db.ref(`ludo/rooms/${roomId}`).transaction(current => {
    if (!current || current.status !== 'playing') {
      failure = 'The match is not active.';
      return;
    }
    if (!current.players?.[auth.uid] || current.activeUid !== auth.uid) return;
    try {
      const moved = applyPawnMove(current, auth.uid, pawnIndex);
      moved.room.message = moved.capturedUid ? 'capture' : '';
      return moved.room;
    } catch {
      return;
    }
  });
  if (!result.committed) throw new HttpsError('failed-precondition', failure);
  return result.snapshot.val();
});

exports.onMatchFinished = onValueWritten({
  ref: '/ludo/rooms/{roomId}',
  region: REGION
}, async event => {
  const before = event.data.before.val();
  const after = event.data.after.val();
  if (after?.status !== 'finished' || before?.status === 'finished') return;
  await awardFinishedMatch(event.params.roomId, after);
  logger.info('Ranked match recorded', { roomId: event.params.roomId, seasonId: after.seasonId });
});

exports.banPlayer = onCall({ region: REGION }, async request => {
  const auth = requireAdmin(request);
  const uid = request.data?.uid;
  if (!validUid(uid) || uid === auth.uid) throw new HttpsError('invalid-argument', 'Choose a valid player account.');
  const target = await getAuth().getUser(uid).catch(() => null);
  if (!target) throw new HttpsError('not-found', 'Player not found.');
  if (target.customClaims?.ludoAdmin === true) throw new HttpsError('failed-precondition', 'Admin accounts cannot be banned here.');
  const reason = typeof request.data?.reason === 'string' ? request.data.reason.trim().slice(0, 160) : '';
  await db.ref(`ludo/bans/${uid}`).set({ reason, bannedAt: Date.now(), bannedBy: auth.uid });
  return { uid, banned: true };
});

exports.unbanPlayer = onCall({ region: REGION }, async request => {
  const auth = requireAdmin(request);
  const uid = request.data?.uid;
  if (!validUid(uid) || uid === auth.uid) throw new HttpsError('invalid-argument', 'Choose a valid player account.');
  await db.ref(`ludo/bans/${uid}`).remove();
  return { uid, banned: false };
});

exports.listBans = onCall({ region: REGION }, async request => {
  requireAdmin(request);
  const snapshot = await db.ref('ludo/bans').get();
  return Object.entries(snapshot.val() || {}).map(([uid, details]) => ({
    uid,
    reason: details.reason || '',
    bannedAt: details.bannedAt || 0
  }));
});

exports.claimAdmin = onCall({ region: REGION, secrets: [ADMIN_BOOTSTRAP_CODE] }, async request => {
  const auth = requireAuth(request);
  if (auth.token.firebase?.sign_in_provider === 'anonymous') {
    throw new HttpsError('failed-precondition', 'Sign in with an email account before admin setup.');
  }
  if (auth.token.ludoAdmin === true) return { admin: true };

  const uid = auth.uid;
  const now = Date.now();
  const attemptsRef = db.ref(`ludo/adminCodeAttempts/${uid}`);
  const attempt = await attemptsRef.transaction(current => {
    if (current?.blockedUntil > now) return;
    const windowStart = current?.windowStart > now - 15 * 60 * 1000 ? current.windowStart : now;
    const count = (current?.windowStart === windowStart ? current.count : 0) + 1;
    return { windowStart, count, blockedUntil: count >= 5 ? now + 15 * 60 * 1000 : 0 };
  });
  if (!attempt.committed) throw new HttpsError('resource-exhausted', 'Too many attempts. Try again later.');

  const supplied = typeof request.data?.code === 'string' ? request.data.code : '';
  if (supplied.length < 8 || supplied.length > 256) {
    throw new HttpsError('permission-denied', 'Admin code was not accepted.');
  }
  const submittedHash = crypto.createHash('sha256').update(supplied).digest();
  const expectedHash = crypto.createHash('sha256').update(ADMIN_BOOTSTRAP_CODE.value()).digest();
  if (!crypto.timingSafeEqual(submittedHash, expectedHash)) {
    throw new HttpsError('permission-denied', 'Admin code was not accepted.');
  }

  const user = await getAuth().getUser(uid);
  await getAuth().setCustomUserClaims(uid, { ...(user.customClaims || {}), ludoAdmin: true });
  await attemptsRef.remove();
  return { admin: true, refreshToken: true };
});
