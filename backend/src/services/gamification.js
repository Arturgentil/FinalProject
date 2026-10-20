const { pool } = require('../db/pool');

function todayBr() {
  const d = new Date();
  return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`;
}

/**
 * Applies a newly-recorded activity to the user's active "passos"/"energia" goals.
 * "batimentos"/"oxigenacao" goals are threshold-style (not cumulative) and aren't
 * auto-updated here - a reasonable simplification given the original app never
 * defined how those should accumulate either.
 * Also does NOT reset goals per period (diário/semanal/mensal) - that would need
 * a scheduled job; documented as a known limitation in the README.
 */
async function updateGoalsForActivity(userId, activity) {
  const goalsRes = await pool.query(
    `SELECT * FROM goals WHERE user_id = $1 AND completed = false AND type IN ('passos','energia')`,
    [userId]
  );
  for (const goal of goalsRes.rows) {
    const delta = goal.type === 'passos' ? activity.steps : activity.energy;
    const newCurrent = Number(goal.current) + Number(delta);
    const completed = newCurrent >= Number(goal.target);
    await pool.query(
      `UPDATE goals SET current = $1, completed = $2, completed_date = $3 WHERE id = $4`,
      [newCurrent, completed, completed ? todayBr() : null, goal.id]
    );
    if (completed) {
      await pool.query(
        `INSERT INTO notifications (user_id, type, message)
         SELECT $1, 'goal', $2 WHERE (SELECT notif_goal FROM users WHERE id = $1)`,
        [userId, `Você atingiu sua meta de ${goal.type}!`]
      );
    }
  }
}

/**
 * Recomputes every achievement's unlock state for a user from their real stats
 * and upserts user_achievements. Returns the list of achievement ids that were
 * newly unlocked in this call (so callers can notify).
 */
async function evaluateAchievements(userId) {
  const [userRes, activitiesRes, maxActivityRes, goalsCompletedRes, followersRes, likesRes] = await Promise.all([
    pool.query('SELECT total_steps, total_energy FROM users WHERE id = $1', [userId]),
    pool.query('SELECT COUNT(*)::int AS c, COUNT(DISTINCT activity_date)::int AS days FROM activities WHERE user_id = $1', [userId]),
    pool.query('SELECT COALESCE(MAX(steps),0) AS steps, COALESCE(MAX(energy),0) AS energy FROM activities WHERE user_id = $1', [userId]),
    pool.query('SELECT COUNT(*)::int AS c FROM goals WHERE user_id = $1 AND completed = true', [userId]),
    pool.query('SELECT COUNT(*)::int AS c FROM follows WHERE followee_id = $1', [userId]),
    pool.query(
      `SELECT COUNT(*)::int AS c FROM post_likes pl JOIN posts p ON p.id = pl.post_id WHERE p.user_id = $1`,
      [userId]
    ),
  ]);

  const user = userRes.rows[0];
  const activityCount = activitiesRes.rows[0].c;
  const activeDays = activitiesRes.rows[0].days;
  const maxSteps = Number(maxActivityRes.rows[0].steps);
  const maxEnergy = Number(maxActivityRes.rows[0].energy);
  const completedGoals = goalsCompletedRes.rows[0].c;
  const followers = followersRes.rows[0].c;
  const likesReceived = likesRes.rows[0].c;

  const unlockedNow = {
    s1: activityCount >= 1,
    s2: maxSteps >= 1000,
    s3: maxSteps >= 10000,
    s4: Number(user.total_steps) >= 100000,
    s5: Number(user.total_steps) >= 1000000,
    e1: activityCount >= 1,
    e2: Number(user.total_energy) >= 100,
    e3: maxEnergy >= 1000,
    e4: Number(user.total_energy) >= 100000,
    a1: activeDays >= 7,
    a2: activeDays >= 30,
    a3: activityCount >= 100,
    m1: completedGoals >= 1,
    m2: completedGoals >= 10,
    m3: completedGoals >= 100,
    so1: followers >= 1,
    so2: likesReceived >= 10,
    so3: followers >= 1000,
  };

  const existingRes = await pool.query('SELECT achievement_id, unlocked FROM user_achievements WHERE user_id = $1', [userId]);
  const existingMap = new Map(existingRes.rows.map((r) => [r.achievement_id, r.unlocked]));

  const newlyUnlocked = [];
  for (const [id, isUnlocked] of Object.entries(unlockedNow)) {
    const wasUnlocked = existingMap.get(id) || false;
    if (isUnlocked && !wasUnlocked) {
      newlyUnlocked.push(id);
      await pool.query(
        `INSERT INTO user_achievements (user_id, achievement_id, unlocked, unlocked_date)
         VALUES ($1,$2,true,$3)
         ON CONFLICT (user_id, achievement_id) DO UPDATE SET unlocked = true, unlocked_date = $3`,
        [userId, id, todayBr()]
      );
    } else if (!existingMap.has(id)) {
      await pool.query(
        `INSERT INTO user_achievements (user_id, achievement_id, unlocked) VALUES ($1,$2,false)
         ON CONFLICT (user_id, achievement_id) DO NOTHING`,
        [userId, id]
      );
    }
  }

  if (newlyUnlocked.length > 0) {
    const namesRes = await pool.query('SELECT id, name FROM achievements_catalog WHERE id = ANY($1)', [newlyUnlocked]);
    for (const row of namesRes.rows) {
      await pool.query(
        `INSERT INTO notifications (user_id, type, message)
         SELECT $1, 'record', $2 WHERE (SELECT notif_record FROM users WHERE id = $1)`,
        [userId, `Nova conquista desbloqueada: ${row.name}!`]
      );
    }
  }

  return newlyUnlocked;
}

module.exports = { updateGoalsForActivity, evaluateAchievements, todayBr };
