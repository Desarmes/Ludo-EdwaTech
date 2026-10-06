const SEASON_LENGTH_MS = 60 * 24 * 60 * 60 * 1000;

const TRACK = [];
for (let column = 1; column <= 5; column++) TRACK.push([6, column]);
for (let row = 5; row >= 0; row--) TRACK.push([row, 6]);
TRACK.push([0, 7], [0, 8]);
for (let row = 1; row <= 5; row++) TRACK.push([row, 8]);
for (let column = 9; column <= 14; column++) TRACK.push([6, column]);
TRACK.push([7, 14], [8, 14]);
for (let column = 13; column >= 9; column--) TRACK.push([8, column]);
for (let row = 9; row <= 14; row++) TRACK.push([row, 8]);
TRACK.push([14, 7], [14, 6]);
for (let row = 13; row >= 9; row--) TRACK.push([row, 6]);
for (let column = 5; column >= 0; column--) TRACK.push([8, column]);
TRACK.push([7, 0], [6, 0]);

const START_INDEX = { 0: 0, 1: 13, 2: 26, 3: 39 };
const SAFE_TRACK_INDICES = new Set([0, 13, 26, 39]);
const RANKS = [
  { name: 'Bronze', points: 0 },
  { name: 'Silver', points: 300 },
  { name: 'Gold', points: 700 },
  { name: 'Platinum', points: 1200 },
  { name: 'Diamond', points: 1800 },
  { name: 'Master', points: 2500 },
  { name: 'Legende', points: 3300 },
  { name: 'Mythic', points: 4200 }
];

function seasonFor(timestamp = Date.now()) {
  const number = Math.floor(timestamp / SEASON_LENGTH_MS);
  const startsAt = number * SEASON_LENGTH_MS;
  return {
    id: `season-${number}`,
    number,
    startsAt,
    endsAt: startsAt + SEASON_LENGTH_MS
  };
}

function rankForPoints(points) {
  let rank = RANKS[0].name;
  for (const tier of RANKS) {
    if (points >= tier.points) rank = tier.name;
  }
  return rank;
}

function canMove(progress, dice) {
  if (dice < 1 || dice > 6) return false;
  if (progress < 0) return dice === 6;
  return progress + dice <= 57;
}

function movablePawns(player, dice) {
  return Object.entries(player.pawns || {})
    .filter(([, progress]) => canMove(progress, dice))
    .map(([index]) => Number(index));
}

function winningDuoTeam(players) {
  for (const team of [0, 1]) {
    const teammates = Object.values(players).filter(player => player.team === team);
    if (teammates.length === 2 && teammates.every(player => Object.values(player.pawns).every(value => value === 57))) {
      return team;
    }
  }
  return null;
}

function movePawn(room, uid, pawnIndex) {
  const next = structuredClone(room);
  const player = next.players?.[uid];
  if (!player || next.activeUid !== uid || !Number.isInteger(pawnIndex) || pawnIndex < 0 || pawnIndex > 3) {
    throw new Error('Invalid turn or pawn.');
  }

  const progress = player.pawns?.[pawnIndex];
  const dice = next.dice;
  if (!canMove(progress, dice)) throw new Error('This pawn cannot move.');

  const movedTo = progress < 0 ? 0 : progress + dice;
  player.pawns[pawnIndex] = movedTo;
  let capturedUid = null;

  if (movedTo <= 51) {
    const trackIndex = (START_INDEX[player.color] + movedTo) % TRACK.length;
    if (!SAFE_TRACK_INDICES.has(trackIndex)) {
      for (const [otherUid, otherPlayer] of Object.entries(next.players)) {
        if (otherUid === uid) continue;
        for (const [otherIndex, otherProgress] of Object.entries(otherPlayer.pawns || {})) {
          if (otherProgress < 0 || otherProgress > 51) continue;
          const otherTrackIndex = (START_INDEX[otherPlayer.color] + otherProgress) % TRACK.length;
          if (otherTrackIndex === trackIndex) {
            otherPlayer.pawns[otherIndex] = -1;
            capturedUid = otherUid;
          }
        }
      }
    }
  }

  next.dice = 0;
  next.lastActionAt = Date.now();
  const winningTeam = next.mode === 'duo' ? winningDuoTeam(next.players) : null;
  if (next.mode === 'duo' && winningTeam !== null) {
    next.status = 'finished';
    next.winningTeam = winningTeam;
    next.winnerUid = uid;
    next.finishedAt = Date.now();
  } else if (next.mode !== 'duo' && Object.values(player.pawns).every(value => value === 57)) {
    next.status = 'finished';
    next.winnerUid = uid;
    next.finishedAt = Date.now();
  } else if (dice !== 6) {
    const order = next.turnOrder || Object.keys(next.players);
    const currentIndex = order.indexOf(uid);
    next.activeUid = order[(currentIndex + 1) % order.length];
  }

  return { room: next, capturedUid };
}

function newPawns() {
  return { 0: -1, 1: -1, 2: -1, 3: -1 };
}

module.exports = {
  RANKS,
  SAFE_TRACK_INDICES,
  SEASON_LENGTH_MS,
  START_INDEX,
  TRACK,
  canMove,
  movePawn,
  movablePawns,
  newPawns,
  rankForPoints,
  seasonFor,
  winningDuoTeam
};
