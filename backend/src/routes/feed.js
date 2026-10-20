const express = require('express');
const { pool } = require('../db/pool');
const { requireAuth } = require('../middleware/auth');
const { timeAgo } = require('../utils/timeAgo');
const { evaluateAchievements } = require('../services/gamification');

const router = express.Router();

router.get('/', requireAuth, async (req, res) => {
  try {
    const postsRes = await pool.query(
      `SELECT p.id, p.steps, p.energy, p.duration_min, p.shoe_color, p.created_at,
              u.id AS user_id, u.name, u.username, u.avatar_url,
              (SELECT COUNT(*)::int FROM post_likes pl WHERE pl.post_id = p.id) AS likes_count,
              EXISTS(SELECT 1 FROM post_likes pl2 WHERE pl2.post_id = p.id AND pl2.user_id = $1) AS liked
       FROM posts p
       JOIN users u ON u.id = p.user_id
       WHERE u.public_profile
          OR u.id = $1
          OR EXISTS (SELECT 1 FROM follows f WHERE f.follower_id = $1 AND f.followee_id = u.id)
       ORDER BY p.created_at DESC
       LIMIT 50`,
      [req.userId]
    );
    const posts = postsRes.rows;
    const postIds = posts.map((p) => p.id);

    let commentsByPost = new Map();
    if (postIds.length > 0) {
      const commentsRes = await pool.query(
        `SELECT c.id, c.post_id, c.text, c.created_at, u.username
         FROM comments c JOIN users u ON u.id = c.user_id
         WHERE c.post_id = ANY($1)
         ORDER BY c.created_at ASC`,
        [postIds]
      );
      commentsByPost = commentsRes.rows.reduce((map, c) => {
        const list = map.get(c.post_id) || [];
        list.push({ id: String(c.id), user: c.username, text: c.text, time: timeAgo(c.created_at) });
        map.set(c.post_id, list);
        return map;
      }, new Map());
    }

    res.json(
      posts.map((p) => ({
        id: String(p.id),
        user: p.username,
        name: p.name,
        avatarUrl: p.avatar_url || '',
        time: timeAgo(p.created_at),
        steps: Number(p.steps),
        energy: Number(p.energy),
        duration: p.duration_min,
        likes: p.likes_count,
        liked: p.liked,
        shoeColor: p.shoe_color,
        comments: commentsByPost.get(p.id) || [],
      }))
    );
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao carregar o feed.' });
  }
});

router.post('/:postId/like', requireAuth, async (req, res) => {
  const postId = Number(req.params.postId);
  try {
    const existing = await pool.query(
      'SELECT 1 FROM post_likes WHERE post_id = $1 AND user_id = $2',
      [postId, req.userId]
    );
    let liked;
    if (existing.rows.length > 0) {
      await pool.query('DELETE FROM post_likes WHERE post_id = $1 AND user_id = $2', [postId, req.userId]);
      liked = false;
    } else {
      await pool.query('INSERT INTO post_likes (post_id, user_id) VALUES ($1,$2)', [postId, req.userId]);
      liked = true;
      await pool.query(
        `INSERT INTO notifications (user_id, type, message, actor_avatar_url)
         SELECT p.user_id, 'like', u.name || ' curtiu sua atividade', u.avatar_url
         FROM posts p, users u WHERE p.id = $1 AND u.id = $2 AND p.user_id != $2
           AND (SELECT notif_social FROM users WHERE id = p.user_id)`,
        [postId, req.userId]
      );
      const ownerRes = await pool.query('SELECT user_id FROM posts WHERE id = $1', [postId]);
      if (ownerRes.rows[0]) await evaluateAchievements(ownerRes.rows[0].user_id);
    }
    const countRes = await pool.query('SELECT COUNT(*)::int AS c FROM post_likes WHERE post_id = $1', [postId]);
    res.json({ liked, likes: countRes.rows[0].c });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao curtir.' });
  }
});

router.post('/:postId/comments', requireAuth, async (req, res) => {
  if (!/^[0-9]{1,9}$/.test(req.params.postId)) return res.status(404).json({ error: 'Publicação não encontrada.' });
  const postId = Number(req.params.postId);
  const { text } = req.body || {};
  if (typeof text !== 'string' || !text.trim()) return res.status(400).json({ error: 'Comentário vazio.' });
  if (text.trim().length > 500) return res.status(400).json({ error: 'O comentário pode ter até 500 caracteres.' });
  try {
    // Existe? E o dono aceita comentários de quem está comentando?
    const owner = await pool.query(
      `SELECT p.user_id, u.comment_policy, u.public_profile,
              EXISTS (SELECT 1 FROM follows f WHERE f.follower_id = $2 AND f.followee_id = p.user_id) AS i_follow
       FROM posts p JOIN users u ON u.id = p.user_id WHERE p.id = $1`,
      [postId, req.userId]
    );
    const o = owner.rows[0];
    if (!o) return res.status(404).json({ error: 'Publicação não encontrada.' });
    if (o.user_id !== req.userId) {
      if (!o.public_profile && !o.i_follow) return res.status(404).json({ error: 'Publicação não encontrada.' });
      if (o.comment_policy === 'none') return res.status(403).json({ error: 'Esta pessoa desativou os comentários.' });
      if (o.comment_policy === 'followers' && !o.i_follow) {
        return res.status(403).json({ error: 'Só quem segue esta pessoa pode comentar.' });
      }
    }
    const result = await pool.query(
      'INSERT INTO comments (post_id, user_id, text) VALUES ($1,$2,$3) RETURNING id, created_at',
      [postId, req.userId, text.trim()]
    );
    const userRes = await pool.query('SELECT username FROM users WHERE id = $1', [req.userId]);

    await pool.query(
      `INSERT INTO notifications (user_id, type, message, actor_avatar_url)
       SELECT p.user_id, 'comment', u.name || ' comentou na sua atividade', u.avatar_url
       FROM posts p, users u WHERE p.id = $1 AND u.id = $2 AND p.user_id != $2
         AND (SELECT notif_social FROM users WHERE id = p.user_id)`,
      [postId, req.userId]
    );

    res.status(201).json({
      id: String(result.rows[0].id),
      user: userRes.rows[0].username,
      text: text.trim(),
      time: timeAgo(result.rows[0].created_at),
    });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao comentar.' });
  }
});

module.exports = router;
