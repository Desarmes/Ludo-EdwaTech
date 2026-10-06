const test = require('node:test');
const assert = require('node:assert/strict');
const {
  SEASON_LENGTH_MS,
  TRACK,
  canMove,
  movePawn,
  movablePawns,
  newPawns,
  rankForPoints,
  seasonFor
} = require('./gameLogic');

const makeRoom = (players, activeUid, dice) => ({
  status: 'playing',
  players,
  activeUid,
  dice
});

test('board has 52 shared path spaces', () => {
  assert.equal(TRACK.length, 52);
});

test('a pawn leaves home only on six and must finish exactly', () => {
  assert.equal(canMove(-1, 5), false);
  assert.equal(canMove(-1, 6), true);
  assert.equal(canMove(56, 1), true);
  assert.equal(canMove(56, 2), false);
});

test('season boundaries are exactly 60 days', () => {
  const season = seasonFor(123456789);
  const next = seasonFor(season.endsAt);
  assert.equal(season.endsAt - season.startsAt, SEASON_LENGTH_MS);
  assert.notEqual(season.id, next.id);
});

test('rank thresholds advance through the requested tiers', () => {
  assert.equal(rankForPoints(0), 'Bronze');
  assert.equal(rankForPoints(1800), 'Diamond');
  assert.equal(rankForPoints(2500), 'Master');
  assert.equal(rankForPoints(3300), 'Legend');
  assert.equal(rankForPoints(4200), 'Mythic');
});

test('only legal pawns are offered after a roll', () => {
  const player = { pawns: { ...newPawns(), 0: -1, 1: 54, 2: 57, 3: 57 } };
  assert.deepEqual(movablePawns(player, 5), []);
  assert.deepEqual(movablePawns(player, 6), [0]);
});

test('a move captures an opponent away from a safe square', () => {
  const room = makeRoom({
    red: { color: 0, pawns: { ...newPawns(), 0: 1 } },
    teal: { color: 1, pawns: { ...newPawns(), 0: 26 } }
  }, 'teal', 1);
  const result = movePawn(room, 'teal', 0);
  assert.equal(result.capturedUid, 'red');
  assert.equal(result.room.players.red.pawns[0], -1);
  assert.equal(result.room.players.teal.pawns[0], 27);
});

test('the exact final move records the winner', () => {
  const room = makeRoom({
    red: { color: 0, pawns: { 0: 55, 1: 57, 2: 57, 3: 57 } },
    teal: { color: 1, pawns: newPawns() }
  }, 'red', 2);
  const result = movePawn(room, 'red', 0);
  assert.equal(result.room.status, 'finished');
  assert.equal(result.room.winnerUid, 'red');
  assert.equal(result.room.players.red.pawns[0], 57);
});
